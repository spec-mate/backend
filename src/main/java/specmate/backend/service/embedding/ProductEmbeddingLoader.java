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
//import java.util.concurrent.*;
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
//    private static final int BATCH_SIZE = 20;
//    private static final int THREAD_COUNT = 5;
//
//    @Override
//    @Transactional
//    public void run(String... args) throws Exception {
//        long totalProducts = productRepository.count();
//        if (totalProducts == 0) {
//            log.warn("⚠️ Product 데이터가 없습니다. 임베딩 로드를 건너뜁니다.");
//            return;
//        }
//
//        log.info("===== Product 임베딩 시작 =====");
//        log.info("총 {}개의 Product 데이터를 처리합니다.", totalProducts);
//
//        List<Product> allProducts = productRepository.findAll();
//        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
//        List<Future<Integer>> futures = new ArrayList<>();
//
//        for (int i = 0; i < allProducts.size(); i += BATCH_SIZE) {
//            int start = i;
//            int end = Math.min(i + BATCH_SIZE, allProducts.size());
//            List<Product> batch = allProducts.subList(start, end);
//            futures.add(executor.submit(() -> processBatch(batch)));
//        }
//
//        int totalEmbedded = 0;
//        for (Future<Integer> f : futures) {
//            try {
//                totalEmbedded += f.get();
//            } catch (Exception e) {
//                log.error("스레드 처리 중 오류: {}", e.getMessage());
//            }
//        }
//
//        executor.shutdown();
//        executor.awaitTermination(1, TimeUnit.HOURS);
//
//        log.info("===== Product 임베딩 완료 =====");
//        log.info("총 {}개 Product 임베딩 완료.", totalEmbedded);
//    }
//
//    /** === 배치 처리 === */
//    private int processBatch(List<Product> batch) {
//        try {
//            List<String> contents = batch.stream()
//                    .map(this::buildEmbeddingText)
//                    .filter(s -> s != null && !s.isBlank())
//                    .toList();
//
//            if (contents.isEmpty()) return 0;
//
//            EmbeddingResponse response = embeddingModel.embedForResponse(contents);
//            List<float[]> vectors = response.getResults().stream()
//                    .map(r -> r.getOutput())
//                    .toList();
//
//            List<Map<String, Object>> records = new ArrayList<>();
//            for (int idx = 0; idx < contents.size(); idx++) {
//                Product p = batch.get(idx);
//                float[] vector = vectors.get(idx);
//
//                String vectorString = "[" + IntStream.range(0, vector.length)
//                        .mapToObj(v -> Float.toString(vector[v]))
//                        .collect(Collectors.joining(",")) + "]";
//
//                Map<String, Object> data = new HashMap<>();
//                data.put("id", p.getId());
//                data.put("content", contents.get(idx));
//                data.put("vector", vectorString);
//                data.put("normalizedType", normalizeType(p.getType()));
//                data.put("priceNum", parsePriceToLong(p));
//                data.put("imageUrl", p.getImage());
//                data.put("now", LocalDateTime.now());
//                records.add(data);
//            }
//
//            embeddingRepository.bulkInsert(records);
//            return records.size();
//
//        } catch (Exception e) {
//            log.error("배치 임베딩 실패: {}", e.getMessage());
//            return 0;
//        }
//    }
//
//    /** === 임베딩 텍스트 생성 === */
//    private String buildEmbeddingText(Product p) {
//        String normalizedType = normalizeType(p.getType()); // 이미 cpu, ssd 등으로 정규화됨
//        String price = extractPriceString(p);
//        String pricePart = (price != null) ? "최저가:" + price + "원, " : "";
//        String optionsPart = formatOptionsForEmbedding(normalizedType, p.getOptions());
//        String name = Optional.ofNullable(p.getName()).orElse("").trim();
//        String maker = Optional.ofNullable(p.getManufacturer()).orElse("").trim();
//        String imageUrl = Optional.ofNullable(p.getImage()).orElse("").trim();
//
//        if (name.isEmpty() && maker.isEmpty() && (optionsPart == null || optionsPart.isEmpty())) {
//            return null;
//        }
//
//        return String.format(
//                "제품명:%s, 제조사:%s, 분류:%s, %s주요 옵션:%s%s",
//                name,
//                maker,
//                normalizedType,
//                pricePart,
//                optionsPart,
//                imageUrl.isEmpty() ? "" : ", 이미지URL:" + imageUrl
//        );
//    }
//
//    /** === type 정규화 === */
//    private static String normalizeType(String raw) {
//        String s = Optional.ofNullable(raw).orElse("unknown")
//                .trim()
//                .toLowerCase(Locale.ROOT)
//                .replaceAll("\\s+", "_")
//                .replaceAll("-", "_");
//
//        return switch (s) {
//            case "cpu", "processor" -> "cpu";
//            case "gpu", "graphics", "graphic_card", "vga", "video_card" -> "vga";
//            case "ram", "memory", "ddr", "ddr5" -> "ram";
//            case "ssd", "nvme", "m2", "m_2", "storage" -> "ssd";
//            case "hdd", "harddisk", "hard_drive" -> "hdd";
//            case "psu", "power", "power_supply" -> "power";
//            case "mainboard", "motherboard", "mb" -> "mainboard";
//            case "cooler", "cooling", "cpu_cooler" -> "cooler";
//            case "case", "chassis", "tower" -> "case";
//            default -> "unknown";
//        };
//    }
//
//    /** === 가격 추출 === */
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
//    /** === 가격 Long 변환 === */
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
//    /** === 옵션 정리 === */
//    @SuppressWarnings("unchecked")
//    private static String formatOptionsForEmbedding(String normalizedType, Map<String, Object> options) {
//        if (options == null || options.isEmpty()) return "";
//
//        List<String> prefer = switch (normalizedType) {
//            case "cpu" -> List.of("socket","cores","threads","base_clock","boost_clock","tdp","l3_cache","igpu");
//            case "vga" -> List.of("chipset","vram","vram_type","base_clock","boost_clock","tdp","length","power_connector");
//            case "ram" -> List.of("capacity","kit","speed","ddr","cas_latency","voltage");
//            case "ssd" -> List.of("capacity","interface","form_factor","controller","nand_structure","sequential_read","sequential_write");
//            case "power" -> List.of("wattage","efficiency","80plus","modular");
//            case "mainboard" -> List.of("chipset","socket","form_factor","memory_slots","max_memory");
//            case "cooler" -> List.of("type","tdp","noise","rpm","height","socket");
//            case "hdd" -> List.of("capacity","rpm","cache","interface");
//            case "case" -> List.of("form_factor","max_gpu_length","max_cooler_height","fan_support");
//            default -> List.of();
//        };
//
//        List<String> keys = new ArrayList<>(options.keySet());
//        keys.sort(Comparator
//                .comparingInt((String k) -> {
//                    int idx = prefer.indexOf(k.toLowerCase(Locale.ROOT));
//                    return idx >= 0 ? idx : prefer.size() + 1;
//                })
//                .thenComparing(k -> k.toLowerCase(Locale.ROOT))
//        );
//
//        return keys.stream()
//                .limit(10)
//                .map(k -> k + "=" + String.valueOf(options.get(k)))
//                .collect(Collectors.joining(", "));
//    }
//}
