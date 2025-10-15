package specmate.backend.repository.embedding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import specmate.backend.entity.ProductEmbedding;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductEmbeddingRepository extends JpaRepository<ProductEmbedding, Long> {

    Optional<ProductEmbedding> findByProductId(Integer productId);

    @Query(value = """
        SELECT pe.id, pe.product_id, pe.vector, pe.content, pe.created_at
        FROM product_embeddings pe
        JOIN products p ON pe.product_id = p.id
        WHERE LOWER(p.type) ILIKE CONCAT('%', LOWER(:type), '%')
        ORDER BY pe.vector <-> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<ProductEmbedding> findNearestEmbeddingsByType(
            @Param("queryVector") String queryVector,
            @Param("type") String type,
            @Param("limit") int limit
    );

    @Query(value = """
        SELECT id, product_id, vector, content, created_at
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
