package specmate.backend.repository.embedding;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import specmate.backend.entity.ProductEmbedding;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ProductEmbeddingRepositoryImpl implements ProductEmbeddingRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    /** 대량 삽입 (멀티스레드 안전, EntityManager 비의존) */
    @Override
    public void bulkInsert(List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) return;

        String sql = """
            INSERT INTO product_embeddings
                (product_id, content, created_at, vector, normalized_type, price_numeric)
            VALUES (?, ?, ?, CAST(? AS vector), ?, ?)
        """;

        jdbcTemplate.batchUpdate(sql, records, records.size(), (ps, record) -> {
            ps.setObject(1, record.get("id"));
            ps.setString(2, (String) record.get("content"));
            ps.setObject(3, record.get("now"));
            ps.setString(4, (String) record.get("vector"));
            ps.setString(5, (String) record.get("normalizedType"));
            ps.setObject(6, record.get("priceNum"));
        });
    }

    /** pgvector 기반 유사도 검색 */
    @Override
    public List<ProductEmbedding> searchBySimilarity(String userInput, int limit) {
        String sql = """
            SELECT *
            FROM product_embeddings
            WHERE vector IS NOT NULL
            ORDER BY vector <-> (
                SELECT vector
                FROM product_embeddings
                WHERE content ILIKE CONCAT('%', ?, '%')
                LIMIT 1
            )
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            ProductEmbedding e = new ProductEmbedding();
            e.setId(rs.getLong("id"));
            e.setContent(rs.getString("content"));
            e.setNormalizedType(rs.getString("normalized_type"));
            e.setPriceNumeric(rs.getLong("price_numeric"));
            e.setVector(rs.getString("vector"));
            return e;
        }, userInput, limit);
    }
}
