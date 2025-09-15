package specmate.backend.repository.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import specmate.backend.entity.Product;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    Page<Product> findByTypeAndManufacturer(String type, String manufacturer, Pageable pageable);

    Page<Product> findByType(String type, Pageable pageable);

    @Query(value = "SELECT * FROM products " +
            "WHERE type = :type " +
            "ORDER BY CAST(REPLACE(lowest_price->>'price', ',', '') AS INT) ASC",
            countQuery = "SELECT count(*) FROM products WHERE type = :type",
            nativeQuery = true)
    Page<Product> findByTypeOrderByLowestPriceAsc(String type, Pageable pageable);

    @Query(value = "SELECT * FROM products " +
            "WHERE type = :type " +
            "ORDER BY CAST(REPLACE(lowest_price->>'price', ',', '') AS INT) DESC",
            countQuery = "SELECT count(*) FROM products WHERE type = :type",
            nativeQuery = true)
    Page<Product> findByTypeOrderByLowestPriceDesc(String type, Pageable pageable);
}
