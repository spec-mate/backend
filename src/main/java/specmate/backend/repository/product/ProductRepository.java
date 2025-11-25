package specmate.backend.repository.product;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import specmate.backend.entity.Product;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> { // ID 타입 Long으로 변경

    @Query("SELECT p FROM Product p " +
        "WHERE (:category IS NULL OR p.category = :category) " +
        "AND (:manufacturer IS NULL OR :manufacturer = '' OR p.manufacturer = :manufacturer) " +
        "AND (:keyword IS NULL OR :keyword = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
        "AND p.status = 'NORMAL'")
    Page<Product> searchProducts(
        @Param("category") String category,
        @Param("manufacturer") String manufacturer,
        @Param("keyword") String keyword,
        Pageable pageable
    );

    Optional<Product> findByName(String name);
}