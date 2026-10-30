package com.ecomassistant.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryParityTest {

    private final CategoryParity parity = new CategoryParity(new CatalogCategoryRegistry(
            new CatalogCategoryRegistry.EdgeSet(
                    Map.of("men-jackets", "men", "men-jeans", "men"),
                    Map.of("men", "Men", "men-jackets", "Jackets", "men-jeans", "Jeans"))));

    @Test
    void labelOrderAndImageOnlyEditsDoNotBreakParity(@TempDir Path dir) {
        Path file = write(dir, """
                [
                  {
                    "id": "men",
                    "name": "Gentlemen",
                    "imageKey": "men.webp",
                    "children": [
                      { "id": "men-jeans", "name": "Denim Trousers", "imageKey": "jeans.webp", "children": [] },
                      { "id": "men-jackets", "name": "Outerwear", "children": [] }
                    ]
                  }
                ]
                """);

        CategoryParity.ParityResult result = parity.validate(file);

        assertThat(result.ok()).isTrue();
    }

    @Test
    void missingIdIsRejected(@TempDir Path dir) {
        Path file = write(dir, """
                [
                  { "id": "men", "name": "Men", "children": [
                    { "id": "men-jackets", "name": "Jackets", "children": [] }
                  ] }
                ]
                """);

        CategoryParity.ParityResult result = parity.validate(file);

        assertThat(result.ok()).isFalse();
        assertThat(result.missingIds()).containsExactly("men-jeans");
    }

    @Test
    void extraIdIsRejected(@TempDir Path dir) {
        Path file = write(dir, """
                [
                  { "id": "men", "name": "Men", "children": [
                    { "id": "men-jackets", "name": "Jackets", "children": [] },
                    { "id": "men-jeans", "name": "Jeans", "children": [] }
                  ] },
                  { "id": "women", "name": "Women", "children": [] }
                ]
                """);

        CategoryParity.ParityResult result = parity.validate(file);

        assertThat(result.ok()).isFalse();
        assertThat(result.extraIds()).containsExactly("women");
    }

    @Test
    void reparentingIsRejected(@TempDir Path dir) {
        Path file = write(dir, """
                [
                  { "id": "men", "name": "Men", "children": [
                    { "id": "men-jackets", "name": "Jackets", "children": [
                      { "id": "men-jeans", "name": "Jeans", "children": [] }
                    ] }
                  ] }
                ]
                """);

        CategoryParity.ParityResult result = parity.validate(file);

        assertThat(result.ok()).isFalse();
        assertThat(result.parentMismatches()).containsEntry("men-jeans", "men-jackets");
    }

    private static Path write(Path dir, String content) {
        try {
            Path file = dir.resolve("categories.json");
            Files.writeString(file, content);
            return file;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
