package com.ecomassistant.catalog;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compares the frontend's editable category tree against the backend's agreed browsing
 * ids and parent edges. Only stable ids and parent relationships are compared; labels,
 * sibling order and image keys are deliberately ignored, so a pure presentation edit never
 * requires a backend membership change. Uncoordinated new ids or hierarchy changes are
 * reported so integration deployment can reject them.
 */
@Component
public class CategoryParity {

    private final CatalogCategoryRegistry registry;

    public CategoryParity(CatalogCategoryRegistry registry) {
        this.registry = registry;
    }

    public ParityResult validate(Path frontendCategoriesFile) {
        CatalogCategoryRegistry.EdgeSet frontend =
                CatalogCategoryRegistry.flatten(CatalogCategoryRegistry.load(frontendCategoriesFile));
        return compare(frontend);
    }

    private ParityResult compare(CatalogCategoryRegistry.EdgeSet frontend) {
        Set<String> backendIds = new LinkedHashSet<>(registry.allIds());
        Set<String> frontendIds = new LinkedHashSet<>(frontend.idToName().keySet());

        Set<String> missingIds = new LinkedHashSet<>(backendIds);
        missingIds.removeAll(frontendIds);

        Set<String> extraIds = new LinkedHashSet<>(frontendIds);
        extraIds.removeAll(backendIds);

        Map<String, String> parentMismatches = new LinkedHashMap<>();
        Map<String, String> backendEdges = registry.edges();
        for (Map.Entry<String, String> backendEdge : backendEdges.entrySet()) {
            String child = backendEdge.getKey();
            String expectedParent = backendEdge.getValue();
            String actualParent = frontend.childToParent().get(child);
            if (actualParent == null) {
                parentMismatches.put(child, expectedParent);
            } else if (!expectedParent.equals(actualParent)) {
                parentMismatches.put(child, actualParent);
            }
        }

        boolean ok = missingIds.isEmpty() && extraIds.isEmpty() && parentMismatches.isEmpty();
        return new ParityResult(ok, missingIds, extraIds, parentMismatches);
    }

    public record ParityResult(boolean ok, Set<String> missingIds, Set<String> extraIds,
            Map<String, String> parentMismatches) {
    }
}
