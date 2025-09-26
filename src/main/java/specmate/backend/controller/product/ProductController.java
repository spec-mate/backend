package specmate.backend.controller.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.product.ProductRequest;
import specmate.backend.dto.product.ProductResponse;
import specmate.backend.service.product.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
@Tag(name = "Product API", description = "상품 CRUD 및 조회 API")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 전체 조회", description = "DB에 저장된 모든 상품 리스트를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "상품 목록 조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponse.class)))
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @Operation(summary = "상품 Type별 조회", description = "특정 type의 상품을 조회합니다. " + "옵션: manufacturer(제조사), sort(priceAsc, priceDesc, popRank)")
    @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponse.class)))
    @GetMapping("/type/{type}")
    public ResponseEntity<Page<ProductResponse>> getProductsByType(@PathVariable String type, @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false, defaultValue = "popRank") String sort, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(productService.getProductsByType(type, manufacturer, sort, pageable));
    }

    @Operation(summary = "상품 단건 조회", description = "상품 ID를 이용해 특정 상품 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "상품 조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Integer id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    @Operation(summary = "상품 등록", description = "새로운 상품을 등록합니다.")
    @ApiResponse(responseCode = "200", description = "상품 등록 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponse.class)))
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.createProduct(request));
    }

    @Operation(summary = "상품 수정", description = "기존 상품의 정보를 수정합니다.")
    @ApiResponse(responseCode = "200", description = "상품 수정 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Integer id, @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @Operation(summary = "상품 삭제", description = "상품 ID를 이용해 특정 상품을 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "상품 삭제 성공")
    @ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}