//package specmate.backend.service.embedding;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.embedding.EmbeddingResponse;
//import org.springframework.ai.openai.OpenAiEmbeddingModel;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//import specmate.backend.entity.Product;
//import specmate.backend.repository.embedding.ProductEmbeddingRepository;
//import specmate.backend.repository.product.ProductRepository;
//
//import java.time.LocalDateTime;
//import java.util.*;
//import java.util.stream.Collectors;
//import java.util.stream.IntStream;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class ProductEmbeddingLoader implements CommandLineRunner {
//
//    private final ProductRepository productRepository;
//    private final ProductEmbeddingRepository embeddingRepository;
//    private final OpenAiEmbeddingModel embeddingModel;
//
//    @Override
//    @Transactional
//    public void run(String... args) throws Exception {
//        long totalProducts = productRepository.count();
//        if (totalProducts == 0) {
//            log.warn("Product 데이터가 없습니다. 임베딩 로드를 건너뜁니다.");
//            return;
//        }
//
//        log.info("총 {}개의 Product 데이터를 점검 후 임베딩합니다.", totalProducts);
//
//        List<Product> products = productRepository.findAll();
//        final int batchSize = 20;
//
//        for (int i = 0; i < products.size(); i += batchSize) {
//            int end = Math.min(i + batchSize, products.size());
//            List<Product> batch = products.subList(i, end);
//
//            for (Product product : batch) {
//                try {
//                    // 기존 임베딩 존재 + 벡터 검증
//                    var existing = embeddingRepository.findByProductId(product.getId());
//                    if (existing.isPresent()) {
//                        var e = existing.get();
//                        if (e.getVector() != null && !e.getVector().isBlank() && e.getVector().length() > 20) {
//                            log.debug("이미 임베딩 존재: {} (id={}), 건너뜀", product.getName(), product.getId());
//                            continue;
//                        } else {
//                            log.warn("임베딩 불완전 - 재생성: {} (id={})", product.getName(), product.getId());
//                            embeddingRepository.deleteByProductId(product.getId());
//                        }
//                    }
//
//                    // 임베딩 텍스트 생성
//                    String content = buildEmbeddingText(product);
//                    if (content == null || content.isBlank()) {
//                        log.warn("빈 임베딩 텍스트: productId={}, name={}", product.getId(), product.getName());
//                        continue;
//                    }
//
//                    // 임베딩 생성
//                    EmbeddingResponse response = embeddingModel.embedForResponse(List.of(content));
//                    float[] vector = response.getResults().get(0).getOutput();
//
//                    String vectorString = "[" + IntStream.range(0, vector.length)
//                            .mapToObj(idx -> Float.toString(vector[idx]))
//                            .collect(Collectors.joining(",")) + "]";
//
//                    String normalizedType = normalizeType(product.getType());
//                    Long priceNum = parsePriceToLong(product);
//
//                    embeddingRepository.insertWithVector(
//                            product.getId(),
//                            content,
//                            LocalDateTime.now(),
//                            vectorString,
//                            normalizedType,
//                            priceNum
//                    );
//
//                    log.info("임베딩 완료: {} (id={})", product.getName(), product.getId());
//
//                } catch (Exception e) {
//                    log.error("임베딩 실패 - {}(id={}): {}", product.getName(), product.getId(), e.getMessage());
//                }
//            }
//
//            log.info("{}~{}번 배치 완료 ({}개)", i, end, batch.size());
//            Thread.sleep(1000); // rate limit 방지
//        }
//
//        long embeddedCount = embeddingRepository.count();
//        log.info("모든 Product 임베딩 완료. 총 {}개 저장됨.", embeddedCount);
//    }
//
//    /** === 임베딩 텍스트 생성 === */
//    private String buildEmbeddingText(Product p) {
//        String normalizedType = normalizeType(p.getType());
//
//        // 가격(있으면 포함)
//        String price = extractPriceString(p); // "123456" 또는 null
//        String pricePart = (price != null) ? "최저가:" + price + "원, " : "";
//
//        // 옵션 정리 (타입별 핵심 우선 + 나머지 알파벳)
//        String optionsPart = formatOptionsForEmbedding(normalizedType, p.getOptions());
//
//        // 제품명/제조사 null 방지
//        String name = Optional.ofNullable(p.getName()).orElse("").trim();
//        String maker = Optional.ofNullable(p.getManufacturer()).orElse("").trim();
//
//        // 너무 빈약하면 null 반환
//        if (name.isEmpty() && maker.isEmpty() && (optionsPart == null || optionsPart.isEmpty())) {
//            return null;
//        }
//
//        String typeAlias = typeAliasKo(normalizedType);
//        return String.format(
//                "제품명:%s, 제조사:%s, 분류:%s, %s주요 옵션:%s",
//                name,
//                maker,
//                typeAlias,
//                pricePart,
//                optionsPart
//        );
//    }
//
//    /** type 정규화: 다양한 변형을 대표군으로 귀속 */
//    private static String normalizeType(String raw) {
//        String s = Optional.ofNullable(raw).orElse("unknown")
//                .trim()
//                .toLowerCase(Locale.ROOT)
//                .replaceAll("\\s+", "_")
//                .replaceAll("-", "_");
//
//        switch (s) {
//            case "cpu", "processor": return "cpu";
//            case "gpu", "graphics", "graphic_card", "vga", "video_card": return "vga";
//            case "ram", "ram_memory", "memory", "dimm", "ddr", "ddr4", "ddr5": return "ram";
//            case "ssd", "nvme", "m2", "m_2", "solid_state_drive", "storage": return "ssd";
//            case "hdd", "harddisk", "hard_drive": return "hdd";
//            case "psu", "power", "power_supply", "smps": return "power";
//            case "mainboard", "motherboard", "mb": return "mainboard";
//            case "cooler", "cooling", "cpu_cooler": return "cooler";
//            case "case", "chassis", "tower": return "case";
//            default: return s;
//        }
//    }
//
//    /** 한국어 별칭(검색 시 도움) */
//    private static String typeAliasKo(String normalized) {
//        return switch (normalized) {
//            case "cpu"       -> "CPU(프로세서)";
//            case "vga"       -> "VGA(그래픽카드)";
//            case "ram"       -> "RAM(메모리)";
//            case "ssd"       -> "SSD(저장장치)";
//            case "hdd"       -> "HDD(하드디스크)";
//            case "power"     -> "PSU(파워)";
//            case "mainboard" -> "MAINBOARD(메인보드)";
//            case "cooler"    -> "COOLER(쿨러)";
//            case "case"      -> "CASE(케이스)";
//            default          -> "기타";
//        };
//    }
//
//    /** 가격 추출: 숫자 문자열만 추출 */
//    private static String extractPriceString(Product p) {
//        try {
//            if (p.getLowestPrice() == null) return null;
//            Object v = p.getLowestPrice().get("price");
//            if (v == null) return null;
//            String s = v.toString().replaceAll("[^0-9]", "");
//            return s.isEmpty() ? null : s;
//        } catch (Exception e) {
//            return null;
//        }
//    }
//
//    /** 가격 Long 변환(실패 시 null) */
//    private static Long parsePriceToLong(Product p) {
//        String s = extractPriceString(p);
//        if (s == null) return null;
//        try {
//            return Long.parseLong(s);
//        } catch (NumberFormatException e) {
//            return null;
//        }
//    }
//
//    /** 옵션 정리: 타입별 핵심 키 우선, 10개 이내로 제한 */
//    @SuppressWarnings("unchecked")
//    private static String formatOptionsForEmbedding(String normalizedType, Map<String, Object> options) {
//        if (options == null || options.isEmpty()) return "";
//
//        // 타입별 우선순위 키
//        List<String> prefer = switch (normalizedType) {
//            case "cpu" -> List.of("socket","cores","threads","base_clock","boost_clock","tdp","l3_cache","igpu");
//            case "vga" -> List.of("chipset","vram","vram_type","base_clock","boost_clock","tdp","length","power_connector");
//            case "ram" -> List.of("capacity","kit","speed","ddr","cas_latency","voltage","ecc","registered");
//            case "ssd" -> List.of("capacity","interface","form_factor","controller","nand_structure","sequential_read","sequential_write","tbw","trim","slc_caching");
//            case "power" -> List.of("wattage","efficiency","80plus","modular","length","connector");
//            case "mainboard" -> List.of("chipset","socket","form_factor","memory_slots","max_memory","m2_slots","pcie_version");
//            case "cooler" -> List.of("type","tdp","noise","rpm","height","socket");
//            case "hdd" -> List.of("capacity","rpm","cache","interface","form_factor","sequential_read","sequential_write");
//            case "case" -> List.of("form_factor","max_gpu_length","max_cooler_height","fan_support","radiator_support");
//            default -> List.of();
//        };
//
//        // 키 정렬: 선호키 우선 → 그 외 알파벳
//        List<String> keys = new ArrayList<>(options.keySet());
//        keys.sort(Comparator
//                .comparingInt((String k) -> {
//                    int idx = prefer.indexOf(k.toLowerCase(Locale.ROOT));
//                    return idx >= 0 ? idx : prefer.size() + 1;
//                })
//                .thenComparing(k -> k.toLowerCase(Locale.ROOT))
//        );
//
//        // 값 문자열화
//        return keys.stream()
//                .limit(10)
//                .map(k -> k + "=" + String.valueOf(options.get(k)))
//                .collect(Collectors.joining(", "));
//    }
//}
