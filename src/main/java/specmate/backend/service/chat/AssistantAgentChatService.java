package specmate.backend.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.chat.AgentResponse;
import specmate.backend.dto.chat.ConversationData;
import specmate.backend.dto.chat.EstimateData;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.entity.enums.MessageStatus;
import specmate.backend.entity.enums.SenderType;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.service.estimate.ai.AiEstimateService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenAI Assistant API + Thread 기반 Agent 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantAgentChatService {

    private final AssistantRunner assistantRunner;
    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final AiEstimateRepository aiEstimateRepository;
    private final AiEstimateService aiEstimateService;
    private final ObjectMapper objectMapper;
    private final specmate.backend.service.product.QdrantProductSearchService qdrantProductSearchService;

    // Qdrant RAG 검색 결과를 저장 (견적 생성 시 사용)
    private List<specmate.backend.entity.Product> lastRagProducts = new ArrayList<>();

    /**
     * Assistant API 기반 사용자 메시지 처리
     */
    @Transactional
    public AgentResponse handleUserMessage(String roomId, String userInput) {
        try {
            // 채팅방 조회
            ChatRoom room = chatRoomService.getEntityById(roomId);

            // Thread ID 확인 (없으면 생성)
            String threadId = room.getThread();
            if (threadId == null || threadId.isBlank()) {
                threadId = assistantRunner.createThread();
                room.setThread(threadId);
                chatRoomService.saveRoom(room);
                log.info("새 Thread 생성: roomId={}, threadId={}", roomId, threadId);
            }

            // 사용자 메시지 저장 (DB)
            ChatMessage userMessage = chatMessageService.saveUserMessage(room, userInput);

            // Thread에 메시지 추가 (OpenAI)
            assistantRunner.addMessage(threadId, userInput);

            // 분류기 실행 (A/B/C 판단)
            String classifierResponse = assistantRunner.runClassifier(threadId);
            String classification = extractClassification(classifierResponse);
            log.info("분류 결과: {} (roomId={})", classification, roomId);

            // 분류 결과에 따라 적절한 Assistant 실행
            String assistantResponse;
            if ("A".equals(classification) || "C".equals(classification)) {
                // 견적 생성/수정 - RAG 검색 결과를 instructions에 포함
                String ragContext = buildRagContext(userInput);
                log.info("RAG 컨텍스트 생성 완료: {} characters", ragContext.length());
                log.debug("━━━━ RAG 데이터 (Assistant에게 전달) ━━━━\n{}\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━", ragContext);
                assistantResponse = assistantRunner.runWithAssistant(
                        threadId,
                        assistantRunner.getEstimateAssistantId(),
                        ragContext
                );
            } else {
                // 질문 답변/대화
                assistantResponse = assistantRunner.runConversation(threadId);
            }

            log.debug("Assistant 응답: {}", assistantResponse);

            // 응답 파싱
            AgentResponse agentResponse = parseAgentResponse(assistantResponse);

            // Assistant 응답을 ChatMessage로 저장
            String responseContent = formatResponseForStorage(agentResponse);
            ChatMessage assistantMessage = ChatMessage.builder()
                    .chatRoom(room)
                    .sender(SenderType.ASSISTANT)
                    .content(responseContent)
                    .status(MessageStatus.SUCCESS)
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();

            // 견적 저장 (estimate 타입인 경우)
            AiEstimate savedEstimate = null;
            if ("estimate".equals(agentResponse.getType())) {
                boolean messageSaved = false;
                try {
                    Map<String, Object> jsonData = objectMapper.convertValue(
                            agentResponse.getData(),
                            Map.class
                    );
                    assistantMessage.setParsedJson(jsonData);

                    // EstimateData → EstimateResult 변환
                    EstimateData estimateData = objectMapper.convertValue(
                            agentResponse.getData(),
                            EstimateData.class
                    );
                    EstimateResult estimateResult = convertToEstimateResult(estimateData);

                    // ChatMessage 먼저 저장 (AiEstimate가 참조하므로)
                    chatMessageService.save(assistantMessage);
                    messageSaved = true;

                    // 🚨 검증: RAG 데이터에 없는 제품 필터링
                    EstimateResult validatedResult = validateAndFixEstimateResult(estimateResult, lastRagProducts);

                    // AiEstimate 및 EstimateProduct 저장 (Qdrant 검색 결과 전달)
                    savedEstimate = aiEstimateService.createAiEstimate(room, assistantMessage, validatedResult, lastRagProducts);
                    log.info("견적 저장 완료: estimateId={}, Qdrant 제품 풀 크기={}", savedEstimate.getId(), lastRagProducts.size());

                    // 🔄 검증된 데이터를 EstimateData로 변환 (클라이언트 응답용)
                    EstimateData validatedEstimateData = convertEstimateResultToData(validatedResult);
                    validatedEstimateData.setAiEstimateId(savedEstimate.getId());

                    // ✅ 검증된 데이터로 응답 교체
                    agentResponse.setData(validatedEstimateData);
                    log.info("✅ 클라이언트 응답을 검증된 데이터로 교체 완료");

                } catch (Exception e) {
                    log.warn("견적 데이터 처리 실패: {}", e.getMessage(), e);
                    // ChatMessage가 아직 저장되지 않았다면 저장
                    if (!messageSaved) {
                        try {
                            chatMessageService.save(assistantMessage);
                        } catch (Exception saveEx) {
                            log.error("ChatMessage 저장 실패: {}", saveEx.getMessage());
                        }
                    }
                }
            } else {
                // estimate 타입이 아닌 경우 저장
                chatMessageService.save(assistantMessage);
            }

            log.info("Agent 응답 완료: roomId={}, type={}, estimateId={}",
                    roomId, agentResponse.getType(), savedEstimate != null ? savedEstimate.getId() : "N/A");
            return agentResponse;

        } catch (Exception e) {
            log.error("Agent 메시지 처리 중 오류: {}", e.getMessage(), e);

            // 사용자 친화적 에러 메시지 생성
            String userMessage;
            if (e.getMessage() != null && e.getMessage().contains("500 Internal Server Error")) {
                userMessage = "OpenAI 서버에서 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
            } else if (e.getMessage() != null && e.getMessage().contains("Run 생성 실패")) {
                userMessage = "AI 요청 생성에 실패했습니다. 잠시 후 다시 시도해주세요.";
            } else if (e.getMessage() != null && e.getMessage().contains("Run 상태: failed")) {
                userMessage = "AI 처리 중 오류가 발생했습니다. 요청 내용을 확인하고 다시 시도해주세요.";
            } else if (e.getMessage() != null && e.getMessage().contains("Run 대기 시간 초과")) {
                userMessage = "AI 응답 대기 시간이 초과되었습니다. 다시 시도해주세요.";
            } else {
                userMessage = "죄송합니다. 요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
            }

            return AgentResponse.builder()
                    .type("conversation")
                    .data(ConversationData.builder()
                            .message(userMessage)
                            .build())
                    .build();
        }
    }

    /**
     * Agent 응답 파싱 (JSON 또는 Markdown 텍스트)
     */
    private AgentResponse parseAgentResponse(String response) throws JsonProcessingException {
        // 먼저 JSON 파싱 시도
        try {
            // JSON 추출 (코드 블록 제거)
            String cleanJson = response
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            // JSON 파싱
            AgentResponse agentResponse = objectMapper.readValue(cleanJson, AgentResponse.class);

            // type에 따라 data를 적절한 클래스로 변환
            if ("estimate".equals(agentResponse.getType())) {
                EstimateData estimateData = objectMapper.convertValue(
                        agentResponse.getData(),
                        EstimateData.class
                );
                agentResponse.setData(estimateData);
            } else if ("conversation".equals(agentResponse.getType())) {
                ConversationData conversationData = objectMapper.convertValue(
                        agentResponse.getData(),
                        ConversationData.class
                );
                agentResponse.setData(conversationData);
            }

            return agentResponse;
        } catch (JsonProcessingException e) {
            // JSON 파싱 실패 시 Markdown/텍스트로 파싱
            log.info("JSON 파싱 실패 - Markdown 텍스트로 파싱 시도");
            return parseMarkdownResponse(response);
        }
    }

    /**
     * Markdown/텍스트 형식의 응답을 EstimateData로 파싱
     */
    private AgentResponse parseMarkdownResponse(String response) {
        try {
            EstimateData estimateData = new EstimateData();
            List<EstimateData.ComponentData> components = new ArrayList<>();

            String[] lines = response.split("\n");

            // Build name/description 추출
            String buildName = "AI 추천 견적";
            String buildDescription = "";

            // 첫 번째 문장을 build description으로 사용
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("*")) {
                    buildDescription = trimmed;
                    break;
                }
            }

            // 컴포넌트 파싱
            String currentType = null;
            String currentName = null;
            String currentPrice = null;
            String currentDescription = null;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();

                // 타입 감지 (1. **CPU**: **제품명** 형식)
                if (line.matches("^\\d+\\.\\s*\\*\\*.*?\\*\\*:.*")) {
                    // 이전 컴포넌트 저장
                    if (currentType != null && currentName != null) {
                        components.add(EstimateData.ComponentData.builder()
                                .type(normalizeType(currentType))  // type 정규화
                                .name(currentName)
                                .description(currentDescription != null ? currentDescription : "")
                                .detail(EstimateData.ComponentDetail.builder()
                                        .price(currentPrice != null ? currentPrice.replaceAll("[^0-9]", "") : "0")
                                        .image(null)
                                        .build())
                                .build());
                    }

                    // 새 컴포넌트 시작
                    // "1. **CPU**: **AMD Ryzen 5 5600X**" 파싱
                    String[] parts = line.split("\\*\\*");
                    if (parts.length >= 4) {
                        currentType = parts[1].replace(":", "").trim();
                        currentName = parts[3].trim();
                        currentPrice = null;
                        currentDescription = null;
                    }
                }
                // 가격 추출 (- **가격**: 약 300,000 원)
                else if (line.contains("가격") && line.contains(":")) {
                    String priceText = line.substring(line.indexOf(":") + 1).trim();
                    currentPrice = priceText.replaceAll("[^0-9]", "");
                }
                // 설명 추출 (- **설명**: ...)
                else if (line.contains("설명") && line.contains(":")) {
                    currentDescription = line.substring(line.indexOf(":") + 1).trim();
                }
                // 총 합계 추출
                else if (line.contains("총 합계") || line.contains("합계")) {
                    String totalText = line.replaceAll("[^0-9]", "");
                    if (!totalText.isEmpty()) {
                        try {
                            estimateData.setTotal(Integer.parseInt(totalText));
                        } catch (NumberFormatException e) {
                            log.warn("총 합계 파싱 실패: {}", line);
                        }
                    }
                }
            }

            // 마지막 컴포넌트 저장
            if (currentType != null && currentName != null) {
                components.add(EstimateData.ComponentData.builder()
                        .type(normalizeType(currentType))  // type 정규화
                        .name(currentName)
                        .description(currentDescription != null ? currentDescription : "")
                        .detail(EstimateData.ComponentDetail.builder()
                                .price(currentPrice != null ? currentPrice.replaceAll("[^0-9]", "") : "0")
                                .image(null)
                                .build())
                        .build());
            }

            // 총 합계가 없으면 컴포넌트 가격 합산
            if (estimateData.getTotal() == null || estimateData.getTotal() == 0) {
                int totalPrice = components.stream()
                        .mapToInt(c -> {
                            try {
                                return Integer.parseInt(c.getDetail().getPrice());
                            } catch (Exception e) {
                                return 0;
                            }
                        })
                        .sum();
                estimateData.setTotal(totalPrice);
            }

            estimateData.setBuildName(buildName);
            estimateData.setBuildDescription(buildDescription);
            estimateData.setComponents(components);

            log.info("Markdown 파싱 완료: {} 개 컴포넌트, 총액: {}",
                    components.size(), estimateData.getTotal());

            return AgentResponse.builder()
                    .type("estimate")
                    .data(estimateData)
                    .build();

        } catch (Exception e) {
            log.error("Markdown 파싱 실패: {}", e.getMessage(), e);
            // 파싱 실패 시 대화형 응답으로 처리
            return AgentResponse.builder()
                    .type("conversation")
                    .data(ConversationData.builder()
                            .message(response)
                            .build())
                    .build();
        }
    }

    /**
     * AgentResponse를 저장용 텍스트로 포맷팅
     */
    private String formatResponseForStorage(AgentResponse response) {
        try {
            if ("estimate".equals(response.getType())) {
                return objectMapper.writeValueAsString(response.getData());
            } else if ("conversation".equals(response.getType())) {
                ConversationData data = objectMapper.convertValue(
                        response.getData(),
                        ConversationData.class
                );
                return data.getMessage();
            }
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("응답 포맷팅 실패: {}", e.getMessage());
            return "응답 저장 실패";
        }
    }

    /**
     * EstimateResult 검증 및 수정 (RAG 데이터 강제 적용)
     */
    private EstimateResult validateAndFixEstimateResult(EstimateResult result, List<specmate.backend.entity.Product> ragProducts) {
        if (result == null || result.getProducts() == null || ragProducts == null || ragProducts.isEmpty()) {
            log.warn("⚠️ 검증 스킵: result={}, products={}, ragProducts={}",
                    result != null, result != null ? result.getProducts() : "N/A", ragProducts != null ? ragProducts.size() : "null");
            return result;
        }

        log.info("🔍 견적 검증 시작: AI 제품 {} 개, RAG 제품 풀 {} 개", result.getProducts().size(), ragProducts.size());

        // RAG 제품 풀의 이미지 상태 로깅
        long ragImagesCount = ragProducts.stream()
                .filter(p -> p.getImage() != null && !p.getImage().isBlank())
                .count();
        log.info("📸 RAG 제품 중 이미지 있는 제품: {} / {} 개", ragImagesCount, ragProducts.size());

        // 타입별로 RAG 제품 그룹화
        Map<String, List<specmate.backend.entity.Product>> ragByType = ragProducts.stream()
                .collect(Collectors.groupingBy(p -> p.getType().toLowerCase()));

        List<EstimateResult.Product> validatedProducts = new ArrayList<>();

        for (EstimateResult.Product aiProduct : result.getProducts()) {
            String type = normalizeType(aiProduct.getType());
            String aiName = aiProduct.getMatchedName();

            // 해당 타입의 RAG 제품 목록
            List<specmate.backend.entity.Product> ragCandidates = ragByType.getOrDefault(type, Collections.emptyList());

            if (ragCandidates.isEmpty()) {
                log.warn("⚠️ RAG에 '{}' 타입 제품 없음 - 스킵: {}", type, aiName);
                continue; // RAG에 없는 타입은 제외
            }

            // RAG에서 매칭되는 제품 찾기
            Optional<specmate.backend.entity.Product> matched = ragCandidates.stream()
                    .filter(rag -> rag.getName().equalsIgnoreCase(aiName) ||
                            rag.getName().toLowerCase().contains(aiName.toLowerCase()) ||
                            aiName.toLowerCase().contains(rag.getName().toLowerCase()))
                    .findFirst();

            specmate.backend.entity.Product ragProduct;
            if (matched.isPresent()) {
                ragProduct = matched.get();
                log.info("✅ 매칭 성공: '{}' → '{}'", aiName, ragProduct.getName());
            } else {
                // 매칭 실패 시 해당 타입의 첫 번째 제품으로 대체
                ragProduct = ragCandidates.get(0);
                log.warn("❌ 매칭 실패: '{}' → RAG 첫 제품 사용: '{}'", aiName, ragProduct.getName());
            }

            // RAG 데이터로 덮어쓰기
            String price = extractPrice(ragProduct);
            String image = (ragProduct.getImage() != null && !ragProduct.getImage().isBlank())
                    ? ragProduct.getImage()
                    : null;

            if (image == null) {
                log.warn("⚠️ 이미지 없음: type={}, name={}, ragProduct.image='{}'",
                        type, ragProduct.getName(), ragProduct.getImage());
            }

            EstimateResult.Product validatedProduct = EstimateResult.Product.builder()
                    .type(type)
                    .description(aiProduct.getDescription())  // 설명은 AI 것 유지
                    .matchedName(ragProduct.getName())        // ✅ RAG 제품명으로 강제 변경
                    .aiName(ragProduct.getName())
                    .price(price)                              // ✅ RAG 가격으로 강제 변경
                    .image(image)                              // ✅ RAG 이미지로 강제 변경
                    .build();

            validatedProducts.add(validatedProduct);

            log.info("  → type={}, name={}, price={}, image={}",
                    type, ragProduct.getName(), price, image != null ? image.substring(0, Math.min(50, image.length())) + "..." : "null");
        }

        result.setProducts(validatedProducts);

        // ✅ 총 가격 재계산 (검증된 제품 기준)
        int totalPrice = validatedProducts.stream()
                .mapToInt(prod -> {
                    try {
                        return Integer.parseInt(prod.getPrice().replaceAll("[^0-9]", ""));
                    } catch (Exception e) {
                        log.warn("가격 파싱 실패: {}", prod.getPrice());
                        return 0;
                    }
                })
                .sum();
        result.setTotalPrice(String.valueOf(totalPrice));
        log.info("🔍 견적 검증 완료: {} 개 제품, 총액: {} 원", validatedProducts.size(), totalPrice);

        return result;
    }

    /**
     * EstimateData → EstimateResult 변환
     */
    private EstimateResult convertToEstimateResult(EstimateData data) {
        EstimateResult result = new EstimateResult();
        result.setBuildName(data.getBuildName());
        result.setBuildDescription(data.getBuildDescription());
        result.setTotalPrice(data.getTotal() != null ? String.valueOf(data.getTotal()) : "0");
        result.setNotes(data.getNotes());
        result.setAnotherInputText(data.getAnotherInputText());

        // Components 변환
        if (data.getComponents() != null) {
            result.setProducts(data.getComponents().stream()
                    .map(comp -> EstimateResult.Product.builder()
                            .type(normalizeType(comp.getType()))  // type 정규화
                            .description(comp.getDescription())
                            .matchedName(comp.getName())
                            .price(comp.getDetail() != null ? comp.getDetail().getPrice() : "0")
                            .image(comp.getDetail() != null ? comp.getDetail().getImage() : null)
                            .aiName(comp.getName())
                            .build())
                    .collect(Collectors.toList()));
        }

        return result;
    }

    /**
     * EstimateResult → EstimateData 변환 (검증 후 클라이언트 응답용)
     */
    private EstimateData convertEstimateResultToData(EstimateResult result) {
        EstimateData data = new EstimateData();
        data.setBuildName(result.getBuildName());
        data.setBuildDescription(result.getBuildDescription());

        // totalPrice 문자열 → Integer 변환
        try {
            if (result.getTotalPrice() != null && !result.getTotalPrice().isBlank()) {
                data.setTotal(Integer.parseInt(result.getTotalPrice().replaceAll("[^0-9]", "")));
            }
        } catch (NumberFormatException e) {
            log.warn("totalPrice 파싱 실패: {}", result.getTotalPrice());
            data.setTotal(0);
        }

        data.setNotes(result.getNotes());
        data.setAnotherInputText(result.getAnotherInputText());

        // Products → Components 변환
        if (result.getProducts() != null) {
            data.setComponents(result.getProducts().stream()
                    .map(prod -> EstimateData.ComponentData.builder()
                            .type(prod.getType())
                            .name(prod.getMatchedName())  // 검증된 제품명 사용
                            .description(prod.getDescription())
                            .detail(EstimateData.ComponentDetail.builder()
                                    .price(prod.getPrice())
                                    .image(prod.getImage())  // 검증된 이미지 사용
                                    .build())
                            .build())
                    .collect(Collectors.toList()));
        }

        return data;
    }

    /**
     * type 필드 정규화 (한글 → 영어)
     */
    private String normalizeType(String type) {
        if (type == null) return "";

        String lowerType = type.toLowerCase().trim();

        // 한글 → 영어 변환
        switch (lowerType) {
            case "메인보드":
            case "mainboard":
            case "마더보드":
                return "mainboard";

            case "그래픽 카드":
            case "그래픽카드":
            case "gpu":
            case "vga":
            case "비디오카드":
                return "vga";

            case "저장장치":
            case "스토리지":
            case "ssd":
            case "nvme":
                return "ssd";

            case "쿨러":
            case "cooler":
            case "cpu쿨러":
            case "쿨링":
                return "cooler";

            case "파워":
            case "파워 서플라이":
            case "파워서플라이":
            case "power":
            case "psu":
                return "power";

            case "케이스":
            case "case":
            case "본체":
                return "case";

            case "cpu":
            case "프로세서":
            case "시피유":
                return "cpu";

            case "램":
            case "메모리":
            case "ram":
            case "ddr":
                return "ram";

            case "하드":
            case "hdd":
            case "하드디스크":
                return "hdd";

            default:
                // 이미 영어 소문자면 그대로 반환
                log.warn("알 수 없는 type: '{}' - 소문자로 변환하여 반환", type);
                return lowerType;
        }
    }

    /**
     * 분류 결과 추출 (A, B, C)
     */
    private String extractClassification(String response) {
        // 응답에서 A, B, C 추출
        response = response.trim().toUpperCase();

        if (response.contains("C")) return "C";
        if (response.contains("A")) return "A";
        if (response.contains("B")) return "B";

        // 기본값: B (질문/대화)
        log.warn("분류 결과를 파싱할 수 없습니다. 기본값 B 반환: {}", response);
        return "B";
    }

    /**
     * RAG 검색 결과를 포함한 컨텍스트 생성
     * @param userInput 사용자 입력
     * @return Assistant에 전달할 instructions
     */
    private String buildRagContext(String userInput) {
        try {
            // 각 카테고리별로 RAG 검색 수행 (상위 5개씩 가져와서 필터링)
            var products = qdrantProductSearchService.searchSimilarProductsByCategory(userInput, 5);

            // 악세서리 필터링 (나사, 브라켓, 케이블 등 제외)
            var filteredProducts = products.stream()
                    .filter(this::isMainComponent)
                    .collect(Collectors.toList());

            // Qdrant 검색 결과를 필드에 저장 (견적 생성 시 사용)
            this.lastRagProducts = new ArrayList<>(filteredProducts);

            if (filteredProducts.isEmpty()) {
                log.warn("RAG 검색 결과 없음 (필터링 후) - 사용자 입력: {}", userInput);
                return "사용자의 요청에 맞는 제품을 검색했지만 결과를 찾지 못했습니다. 일반적인 제품 정보를 활용해서 견적을 생성하세요.";
            }

            // 카테고리별로 그룹화
            var productsByCategory = filteredProducts.stream()
                    .collect(Collectors.groupingBy(specmate.backend.entity.Product::getType));

            // 누락된 카테고리 확인
            List<String> requiredCategories = List.of("case", "cpu", "vga", "ram", "ssd", "power", "mainboard", "cooler", "hdd");
            List<String> missingCategories = requiredCategories.stream()
                    .filter(cat -> !productsByCategory.containsKey(cat))
                    .collect(Collectors.toList());

            if (!missingCategories.isEmpty()) {
                log.warn("RAG 검색에서 누락된 카테고리: {}", missingCategories);
            }

            log.info("RAG 검색 완료: 총 {} 개 제품, {} 개 카테고리 (필터링 전: {} 개)",
                    filteredProducts.size(), productsByCategory.size(), products.size());

            // 카테고리별 제품 목록 상세 로깅
            for (var entry : productsByCategory.entrySet()) {
                String category = entry.getKey();
                List<String> productNames = entry.getValue().stream()
                        .map(specmate.backend.entity.Product::getName)
                        .collect(Collectors.toList());
                log.info("  [{}] {} 개: {}", category.toUpperCase(), productNames.size(), productNames);
            }

            // RAG 데이터를 JSON 예시 형태로 제공 (정확한 복사를 위해)
            StringBuilder ragData = new StringBuilder();

            ragData.append("━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            ragData.append("RAG 검색 결과 (아래 제품만 사용)\n");
            ragData.append("━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

            for (var entry : productsByCategory.entrySet()) {
                String category = entry.getKey();
                var categoryProducts = entry.getValue();

                ragData.append(String.format("■ type=\"%s\" 제품 목록 (%d개)\n\n", category, categoryProducts.size()));

                for (var product : categoryProducts) {
                    String priceStr = extractPrice(product);
                    String imageUrl = (product.getImage() != null && !product.getImage().isBlank())
                            ? product.getImage()
                            : null;

                    // JSON 형태로 정확히 제공
                    ragData.append("{\n");
                    ragData.append(String.format("  \"type\": \"%s\",\n", category));
                    ragData.append(String.format("  \"name\": \"%s\",\n", product.getName()));
                    ragData.append("  \"detail\": {\n");
                    ragData.append(String.format("    \"price\": \"%s\",\n", priceStr));
                    if (imageUrl != null) {
                        ragData.append(String.format("    \"image\": \"%s\"\n", imageUrl));
                    } else {
                        ragData.append("    \"image\": null\n");
                    }
                    ragData.append("  }\n");
                    ragData.append("}\n\n");
                }
            }

            // 누락된 카테고리 명시
            if (!missingCategories.isEmpty()) {
                ragData.append("━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                ragData.append("⚠️ 누락된 카테고리: " + String.join(", ", missingCategories) + "\n");
                ragData.append("━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
                ragData.append("누락된 카테고리는 다음 형식으로 출력:\n\n");
                ragData.append("{\n");
                ragData.append("  \"type\": \"카테고리명\",\n");
                ragData.append("  \"name\": \"데이터 없음\",\n");
                ragData.append("  \"description\": \"해당 부품 없음\",\n");
                ragData.append("  \"detail\": {\n");
                ragData.append("    \"price\": \"0\",\n");
                ragData.append("    \"image\": null\n");
                ragData.append("  }\n");
                ragData.append("}\n\n");
            }

            ragData.append("━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            ragData.append("🚨 필수 확인사항\n");
            ragData.append("━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
            ragData.append("1. 위의 제품 데이터를 정확히 복사하세요\n");
            ragData.append("2. name, price, image는 한 글자도 바꾸지 마세요\n");
            ragData.append("3. type은 영어 소문자 그대로 사용하세요\n");
            ragData.append("4. 위에 없는 제품은 절대 만들지 마세요\n\n");

            log.info("RAG 컨텍스트 생성 완료: {} 카테고리, {} 제품 (필터링 전: {})",
                    productsByCategory.size(), filteredProducts.size(), products.size());

            return ragData.toString();

        } catch (Exception e) {
            log.error("RAG 컨텍스트 생성 실패: {}", e.getMessage(), e);
            return "제품 검색 중 오류가 발생했습니다. 일반적인 지식을 활용해서 견적을 생성하세요.";
        }
    }

    /**
     * 메인 부품인지 확인 (악세서리 필터링)
     * @param product Product 엔티티
     * @return 메인 부품이면 true, 악세서리면 false
     */
    private boolean isMainComponent(specmate.backend.entity.Product product) {
        if (product == null || product.getName() == null) {
            return false;
        }

        String name = product.getName().toLowerCase();

        // 명확한 악세서리 키워드 (단독으로 나와도 제외)
        String[] strongExcludeKeywords = {
                "나사", "볼트", "너트", "와셔",
                "케이블", "선", "연장선", "젠더",
                "가이드", "매뉴얼", "설명서",
                "스티커", "라벨"
        };

        for (String keyword : strongExcludeKeywords) {
            if (name.contains(keyword)) {
                log.debug("악세서리 필터링: {} (키워드: {})", product.getName(), keyword);
                return false;
            }
        }

        // 조합 키워드 (특정 단어와 함께 나올 때만 제외)
        if ((name.contains("브라켓") && !name.contains("케이스")) ||
            (name.contains("받침대")) ||
            (name.contains("스탠드") && !name.contains("라이저")) ||
            (name.contains("거치대")) ||
            (name.contains("방열판") && !name.contains("지지대"))) {
            log.debug("악세서리 필터링: {} (조합 키워드)", product.getName());
            return false;
        }

        // "세트"는 악세서리 관련 세트만 제외
        if (name.contains("세트") &&
            (name.contains("나사") || name.contains("볼트") || name.contains("공구"))) {
            log.debug("악세서리 세트 필터링: {}", product.getName());
            return false;
        }

        // 노트북 제외
        if (name.contains("노트북") || name.contains("laptop")) {
            log.debug("노트북 제품 필터링: {}", product.getName());
            return false;
        }

        // 서버용 제외
        if (name.contains("서버") || name.contains("server")) {
            log.debug("서버용 제품 필터링: {}", product.getName());
            return false;
        }

        return true;
    }

    /**
     * Product에서 가격 정보 추출
     * @param product Product 엔티티
     * @return 가격 문자열
     */
    private String extractPrice(specmate.backend.entity.Product product) {
        try {
            // lowestPrice에서 가격 추출
            if (product.getLowestPrice() != null && !product.getLowestPrice().isEmpty()) {
                Object priceObj = product.getLowestPrice().get("price");
                if (priceObj != null) {
                    return priceObj.toString();
                }
            }

            // priceInfo에서 가격 추출
            if (product.getPriceInfo() != null && !product.getPriceInfo().isEmpty()) {
                Map<String, Object> firstPrice = product.getPriceInfo().get(0);
                Object priceObj = firstPrice.get("price");
                if (priceObj != null) {
                    return priceObj.toString();
                }
            }

            return "가격 정보 없음";

        } catch (Exception e) {
            log.warn("가격 추출 실패 - 제품: {}, 에러: {}", product.getName(), e.getMessage());
            return "가격 정보 없음";
        }
    }
}