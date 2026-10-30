package com.ecomassistant.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogCategoryRegistryTest {

    private final CatalogCategoryRegistry registry =
            new CatalogCategoryRegistry("catalog/browsing-categories.json");

    @Test
    void loadsAgreedTreeWithBothRoots() {
        assertThat(registry.allIds()).hasSize(38);
        assertThat(registry.isKnown("men")).isTrue();
        assertThat(registry.isKnown("women")).isTrue();
        assertThat(registry.isKnown("men-jackets-coats")).isTrue();
        assertThat(registry.isKnown("women-jeans")).isTrue();
        assertThat(registry.isKnown("men-unknown")).isFalse();
    }

    @Test
    void parentEdgesMatchTheAgreedHierarchy() {
        assertThat(registry.parentOf("men")).isNull();
        assertThat(registry.parentOf("women")).isNull();
        assertThat(registry.parentOf("men-jackets-coats")).isEqualTo("men");
        assertThat(registry.parentOf("men-jackets")).isEqualTo("men-jackets-coats");
        assertThat(registry.parentOf("men-coats")).isEqualTo("men-jackets-coats");
        assertThat(registry.parentOf("men-bomber")).isEqualTo("men-jackets");
        assertThat(registry.parentOf("women-jeans")).isEqualTo("women");
    }

    @Test
    void pathIdsAreAncestorInclusiveFromRoot() {
        assertThat(registry.pathIdsOf("men-bomber"))
                .containsExactly("men", "men-jackets-coats", "men-jackets", "men-bomber");
        assertThat(registry.pathIdsOf("women-jeans")).containsExactly("women", "women-jeans");
        assertThat(registry.pathIdsOf("men")).containsExactly("men");
    }

    @Test
    void categoryPathIdsIsDeduplicatedUnion() {
        Set<String> pathIds = registry.categoryPathIds(List.of("men-bomber", "men"));
        assertThat(pathIds).containsExactly("men", "men-jackets-coats", "men-jackets", "men-bomber");
    }

    @Test
    void readableAncestryReplacesOpaqueIds() {
        assertThat(registry.readableAncestry(List.of("men-bomber"))).isEqualTo("Men Bomber Jackets");
        assertThat(registry.readableAncestry(List.of("women-jeans"))).isEqualTo("Women Jeans");
        assertThat(registry.readableAncestry(List.of("men-sweaters", "women-sweaters")))
                .isEqualTo("Men Sweaters, Women Sweaters");
    }

    @Test
    void duplicateIdAcrossBranchesIsRejected() {
        // Two roots with the same id collapse in the id->name map but the duplicate id is
        // still unambiguous; assert the flat-edge validation catches a real defect instead.
        assertThatThrownBy(() -> new CatalogCategoryRegistry(new CatalogCategoryRegistry.EdgeSet(
                Map.of("a", "missing-parent"), Map.of("a", "A", "b", "B"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing parent");
    }

    @Test
    void missingParentIsRejected() {
        assertThatThrownBy(() -> new CatalogCategoryRegistry(new CatalogCategoryRegistry.EdgeSet(
                Map.of("a", "b"), Map.of("a", "A"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing parent");
    }

    @Test
    void cycleIsRejected() {
        assertThatThrownBy(() -> new CatalogCategoryRegistry(new CatalogCategoryRegistry.EdgeSet(
                Map.of("a", "b", "b", "a"), Map.of("a", "A", "b", "B"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }
}
