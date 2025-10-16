package specmate.backend.repository.product;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import specmate.backend.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    Page<Product> findByTypeAndManufacturer(String type, String manufacturer, Pageable pageable);

    Page<Product> findByType(String type, Pageable pageable);

    List<Product> findAllByName(String name);

    Optional<Product> findFirstByNameContainingIgnoreCase(String name);

    @Query(value = """
        SELECT * 
        FROM products 
        WHERE type = :type
          AND (:manufacturer IS NULL OR :manufacturer = '' OR manufacturer = :manufacturer)
          AND (:keyword IS NULL OR :keyword = '' OR LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY 
          CASE WHEN :sort = 'priceAsc' 
               THEN CAST(NULLIF(REGEXP_REPLACE(lowest_price->>'price', '[^0-9]', '', 'g'), '') AS INTEGER) END ASC,
          CASE WHEN :sort = 'priceDesc' 
               THEN CAST(NULLIF(REGEXP_REPLACE(lowest_price->>'price', '[^0-9]', '', 'g'), '') AS INTEGER) END DESC,
          CASE WHEN :sort = 'popRank' 
               THEN pop_rank END ASC,
          id ASC
        """,
            countQuery = """
        SELECT count(*) 
        FROM products 
        WHERE type = :type
          AND (:manufacturer IS NULL OR :manufacturer = '' OR manufacturer = :manufacturer)
          AND (:keyword IS NULL OR :keyword = '' OR LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """,
            nativeQuery = true)
    Page<Product> searchProducts(@Param("type") String type, @Param("manufacturer") String manufacturer, @Param("keyword") String keyword, @Param("sort") String sort, Pageable pageable);
}