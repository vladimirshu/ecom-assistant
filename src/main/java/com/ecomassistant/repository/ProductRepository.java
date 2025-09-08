package com.ecomassistant.repository;

import com.ecomassistant.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    // Find products by color
    List<Product> findByColorIgnoreCase(String color);

    // Find products by size
    List<Product> findBySizeIgnoreCase(String size);

    // Find products by price range
    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    // Find products by color and size
    List<Product> findByColorIgnoreCaseAndSizeIgnoreCase(String color, String size);

    // Search in description
    @Query("SELECT p FROM Product p WHERE LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Product> findByDescriptionContaining(@Param("keyword") String keyword);

    // Find products by multiple criteria
    @Query("SELECT p FROM Product p WHERE " +
           "(:color IS NULL OR LOWER(p.color) = LOWER(:color)) AND " +
           "(:size IS NULL OR LOWER(p.size) = LOWER(:size)) AND " +
           "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR p.price <= :maxPrice)")
    List<Product> findByCriteria(@Param("color") String color, 
                                @Param("size") String size,
                                @Param("minPrice") BigDecimal minPrice, 
                                @Param("maxPrice") BigDecimal maxPrice);
}
