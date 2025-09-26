package specmate.backend.repository.product;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import specmate.backend.entity.Product;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    Page<Product> findByTypeAndManufacturer(String type, String manufacturer, Pageable pageable);

    Page<Product> findByType(String type, Pageable pageable);

    @Query(value =
            "SELECT * FROM products " +
            "WHERE type = :type " +
            "ORDER BY CAST(REPLACE(lowest_price->>'price', ',', '') AS INTEGER) ASC, id ASC",
            countQuery = "SELECT count(*) FROM products WHERE type = :type",
            nativeQuery = true)
    Page<Product> findByTypeOrderByLowestPriceAsc(@Param("type") String type, Pageable pageable);

    @Query(value =
            "SELECT * FROM products " +
                    "WHERE type = :type " +
                    "ORDER BY CAST(REPLACE(lowest_price->>'price', ',', '') AS INTEGER) DESC, id ASC",
            countQuery = "SELECT count(*) FROM products WHERE type = :type",
            nativeQuery = true)
    Page<Product> findByTypeOrderByLowestPriceDesc(@Param("type") String type, Pageable pageable);

    @Query(value =
            "SELECT * FROM products " +
            "WHERE type = :type AND manufacturer = :manufacturer " +
            "ORDER BY CAST(REPLACE(lowest_price->>'price', ',', '') AS INTEGER) ASC, id ASC",
            countQuery = "SELECT count(*) FROM products WHERE type = :type AND manufacturer = :manufacturer",
            nativeQuery = true)
    Page<Product> findByTypeAndManufacturerOrderByLowestPriceAsc(@Param("type") String type, @Param("manufacturer") String manufacturer, Pageable pageable);

    @Query(value =
            "SELECT * FROM products " +
            "WHERE type = :type AND manufacturer = :manufacturer " +
            "ORDER BY CAST(REPLACE(lowest_price->>'price', ',', '') AS INTEGER) DESC, id ASC",
            countQuery = "SELECT count(*) FROM products WHERE type = :type AND manufacturer = :manufacturer",
            nativeQuery = true)
    Page<Product> findByTypeAndManufacturerOrderByLowestPriceDesc(@Param("type") String type, @Param("manufacturer") String manufacturer, Pageable pageable);

    @Query(value =
            "SELECT * FROM products " +
            "WHERE type = :type AND manufacturer = :manufacturer " +
            "ORDER BY pop_rank NULLS LAST, id ASC",
            countQuery = "SELECT count(*) FROM products WHERE type = :type AND manufacturer = :manufacturer",
            nativeQuery = true)
    Page<Product> findByTypeAndManufacturerOrderByPopRankAsc(@Param("type") String type, @Param("manufacturer") String manufacturer, Pageable pageable);

    @Query(value =
            "SELECT * FROM products " +
            "WHERE type = :type " +
            "ORDER BY pop_rank NULLS LAST, id ASC",
            countQuery = "SELECT count(*) FROM products WHERE type = :type",
            nativeQuery = true)
    Page<Product> findByTypeOrderByPopRankAsc(@Param("type") String type, Pageable pageable);
}