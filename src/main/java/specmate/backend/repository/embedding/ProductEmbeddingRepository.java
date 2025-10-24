package specmate.backend.repository.embedding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.entity.ProductEmbedding;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductEmbeddingRepository extends JpaRepository<ProductEmbedding, Long> {

    /** 특정 product_id로 임베딩 조회 */
    Optional<ProductEmbedding> findByProductId(Integer productId);

    /** pgvector: 벡터 삽입 (문자열 → CAST(:vector AS vector)) */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO product_embeddings
            (product_id, content, created_at, vector, normalized_type, price_numeric)
        VALUES
            (:productId, :content, :createdAt, CAST(:vector AS vector), :normalizedType, :price)
        """, nativeQuery = true)
    void insertWithVector(@Param("productId") Integer productId,
                          @Param("content") String content,
                          @Param("createdAt") LocalDateTime createdAt,
                          @Param("vector") String vector,
                          @Param("normalizedType") String normalizedType,
                          @Param("price") Long priceNumeric);

    /** products.type ILIKE 로 필터링 (pgvector 유사도 검색 포함) */
    @Query(value = """
        SELECT
            pe.id,
            pe.product_id,
            pe.vector,
            pe.content,
            pe.created_at,
            pe.normalized_type,
            pe.price_numeric
        FROM product_embeddings pe
        JOIN products p ON pe.product_id = p.id
        WHERE p.type ILIKE CONCAT('%', :type, '%')
        ORDER BY pe.vector <-> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ProductEmbedding> findNearestEmbeddingsByType(
            @Param("queryVector") String queryVector,
            @Param("type") String type,
            @Param("limit") int limit
    );

    /** normalized_type 으로 직접 필터링 — 조인 제거 (속도 개선) */
    @Query(value = """
        SELECT
            id,
            product_id,
            vector,
            content,
            created_at,
            normalized_type,
            price_numeric
        FROM product_embeddings
        WHERE normalized_type = :type
        ORDER BY vector <-> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ProductEmbedding> findNearestByNormalizedType(
            @Param("queryVector") String queryVector,
            @Param("type") String normalizedType,
            @Param("limit") int limit
    );

    /** 타입 무관 전체 근접 검색 (유사도 기반 정렬) */
    @Query(value = """
        SELECT
            id,
            product_id,
            vector,
            content,
            created_at,
            normalized_type,
            price_numeric
        FROM product_embeddings
        WHERE vector IS NOT NULL
        ORDER BY vector <-> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ProductEmbedding> findNearestEmbeddings(
            @Param("queryVector") String queryVector,
            @Param("limit") int limit
    );
}
