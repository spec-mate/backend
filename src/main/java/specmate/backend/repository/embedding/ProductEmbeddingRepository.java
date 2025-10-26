package specmate.backend.repository.embedding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.entity.ProductEmbedding;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductEmbeddingRepository extends JpaRepository<ProductEmbedding, Long>, ProductEmbeddingRepositoryCustom {

    /** 벡터 삽입 (OpenAI 임베딩 결과를 pgvector로 캐스팅하여 저장) */
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


    /** pgvector 의미 검색 (ILIKE 제거, 오직 벡터 유사도 기반) */
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
        WHERE pe.vector IS NOT NULL
        ORDER BY pe.vector <-> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ProductEmbedding> searchBySimilarity(
            @Param("queryVector") String queryVector,
            @Param("keyword") String keyword, // 유지 (호출 구조 변경 방지용)
            @Param("limit") int limit
    );


    /** 특정 카테고리 내에서 유사도 검색 */
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
      AND vector IS NOT NULL
    ORDER BY vector <-> CAST(:queryVector AS vector)
    LIMIT :limit
    """, nativeQuery = true)
    List<ProductEmbedding> findNearestByNormalizedType(
            @Param("queryVector") String queryVector,
            @Param("type") String normalizedType,
            @Param("limit") int limit
    );


    /** 전체 범위 유사도 검색 (카테고리 무시, fallback 용도) */
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
