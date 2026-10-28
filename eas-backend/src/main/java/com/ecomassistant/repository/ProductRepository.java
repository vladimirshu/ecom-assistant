package com.ecomassistant.repository;

import com.ecomassistant.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    /**
     * Whether any stored product has no category yet. This is the upgrade path from the
     * previous schema: when the stored rows predate the category column (Hibernate's
     * {@code ddl-auto: update} adds it as {@code NULL}), the import re-runs to fill the
     * new fields.
     */
    boolean existsByCategoryIsNull();

    /**
     * Deletes products that still have no category after an import, i.e. legacy rows whose
     * SKU is no longer (validly) in the CSV. Without this they would keep
     * {@link #existsByCategoryIsNull()} true and re-trigger the import on every startup.
     *
     * @return the number of deleted rows
     */
    @Transactional
    long deleteByCategoryIsNull();
}
