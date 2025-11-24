package specmate.backend.repository.product;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import specmate.backend.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> { // ID 타입 Long으로 변경

    @Query("SELECT p FROM Product p " +
        "WHERE (:category IS NULL OR p.category = :category) " +
        "AND (:brand IS NULL OR :brand = '' OR p.brand = :brand) " +
        "AND (:keyword IS NULL OR :keyword = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
        "AND p.availability = 'In Stock' || 'Available'")
    Page<Product> searchProducts(
        @Param("category") String category,
        @Param("brand") String brand,
        @Param("keyword") String keyword,
        Pageable pageable
    );
}