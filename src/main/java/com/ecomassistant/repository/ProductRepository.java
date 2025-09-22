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

    // Find products by price with specific operator
    @Query("SELECT p FROM Product p WHERE " +
           "(:operator = 'LESS_THAN' AND p.price < :price) OR " +
           "(:operator = 'GREATER_THAN' AND p.price > :price) OR " +
           "(:operator = 'LESS_THAN_OR_EQUAL' AND p.price <= :price) OR " +
           "(:operator = 'GREATER_THAN_OR_EQUAL' AND p.price >= :price) " +
            "ORDER BY p.price ASC")
    List<Product> findByPrice(@Param("price") BigDecimal price, @Param("operator") String operator);
}
