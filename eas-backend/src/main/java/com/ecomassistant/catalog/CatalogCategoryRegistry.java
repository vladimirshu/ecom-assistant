package com.ecomassistant.catalog;

import com.ecomassistant.config.EcomBrowsingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory registry of the agreed, stable browsing category ids and their parent
 * relationships. It is the backend's source of truth for which category ids exist, how
 * they nest, and how a product's direct memberships expand into the ancestor-inclusive
 * {@code categoryPathIds} set stored on the Qdrant payload.
 *
 * <p>The tree is authored as a nested JSON resource mirroring the frontend
 * {@code src/data/categories.json} shape, then flattened and validated. Labels, sibling
 * order and image keys are not membership semantics and are ignored everywhere except the
 * readable audience/ancestry text used in embedded search content.
 */
@Component
public class CatalogCategoryRegistry {

    private final Map<String, String> parentById = new LinkedHashMap<>();
    private final Map<String, String> nameById = new LinkedHashMap<>();
    private final Map<String, List<String>> pathIdsById = new LinkedHashMap<>();
    private final List<String> rootIds = new ArrayList<>();

    @Autowired
    public CatalogCategoryRegistry(EcomBrowsingProperties properties) {
        this(load(properties.categoriesFile()));
    }

    public CatalogCategoryRegistry(String categoriesFile) {
        this(load(categoriesFile));
    }

    CatalogCategoryRegistry(List<BrowsingCategory> roots) {
        EdgeSet edges = flatten(roots);
        this.parentById.putAll(edges.childToParent());
        this.nameById.putAll(edges.idToName());
        validate();
        computePaths();
    }

    /** Flattened edges used by tests to exercise cycle / missing-parent validation directly. */
    record EdgeSet(Map<String, String> childToParent, Map<String, String> idToName) {
    }

    CatalogCategoryRegistry(EdgeSet edges) {
        this.parentById.putAll(edges.childToParent());
        this.nameById.putAll(edges.idToName());
        validate();
        computePaths();
    }

    /** Whether the given id is a known browsing category. */
    public boolean isKnown(String id) {
        return id != null && nameById.containsKey(id);
    }

    /** All known browsing category ids, in insertion order. */
    public List<String> allIds() {
        return List.copyOf(nameById.keySet());
    }

    /** The parent id of {@code id}, or {@code null} when it is a root. */
    public String parentOf(String id) {
        return parentById.get(id);
    }

    /** The id -> parent-id edges (roots are absent), for parity comparison. */
    public Map<String, String> edges() {
        return Map.copyOf(parentById);
    }

    /** The readable reference name of a category id, or {@code null} when unknown. */
    public String nameOf(String id) {
        return nameById.get(id);
    }

    /**
     * The ancestor-inclusive path from the root down to {@code id} (root first, {@code id}
     * last), or {@code null} when {@code id} is unknown.
     */
    public List<String> pathIdsOf(String id) {
        return pathIdsById.get(id);
    }

    /**
     * The deduplicated, ancestor-inclusive union of every direct membership's path. This
     * is exactly the {@code categoryPathIds} value stored on the Qdrant payload, so that
     * filtering one requested category is an indexed keyword membership match.
     */
    public Set<String> categoryPathIds(Collection<String> directIds) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String id : directIds) {
            List<String> path = pathIdsOf(id);
            if (path != null) {
                result.addAll(path);
            }
        }
        return result;
    }

    /**
     * A readable audience/ancestry string for embedded search content, e.g.
     * {@code "Men Rain Jackets"} or {@code "Women Sweaters, Men Sweaters"}. Opaque ids are
     * replaced with their readable names; a root-only membership contributes just the root
     * name. Unknown ids are skipped.
     */
    public String readableAncestry(Collection<String> directIds) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (String id : directIds) {
            List<String> path = pathIdsOf(id);
            if (path == null || path.isEmpty()) {
                continue;
            }
            String rootName = nameById.get(path.get(0));
            if (path.size() == 1) {
                parts.add(rootName);
            } else {
                parts.add(rootName + " " + nameById.get(id));
            }
        }
        return String.join(", ", parts);
    }

    static List<BrowsingCategory> load(String categoriesFile) {
        JsonMapper mapper = jsonMapper();
        try (var in = new ClassPathResource(categoriesFile).getInputStream()) {
            BrowsingCategory[] roots = mapper.readValue(in, BrowsingCategory[].class);
            return List.of(roots);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read browsing categories from " + categoriesFile, e);
        }
    }

    static List<BrowsingCategory> load(java.nio.file.Path file) {
        JsonMapper mapper = jsonMapper();
        try (var in = java.nio.file.Files.newInputStream(file)) {
            BrowsingCategory[] roots = mapper.readValue(in, BrowsingCategory[].class);
            return List.of(roots);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read browsing categories from " + file, e);
        }
    }

    private static JsonMapper jsonMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    static EdgeSet flatten(List<BrowsingCategory> roots) {
        Map<String, String> childToParent = new LinkedHashMap<>();
        Map<String, String> idToName = new LinkedHashMap<>();
        for (BrowsingCategory root : roots) {
            flatten(root, null, childToParent, idToName);
        }
        return new EdgeSet(childToParent, idToName);
    }

    private static void flatten(BrowsingCategory node, String parent,
            Map<String, String> childToParent, Map<String, String> idToName) {
        idToName.put(node.id(), node.name() == null ? node.id() : node.name());
        if (parent != null) {
            childToParent.put(node.id(), parent);
        }
        for (BrowsingCategory child : node.children()) {
            flatten(child, node.id(), childToParent, idToName);
        }
    }

    private void validate() {
        for (String id : nameById.keySet()) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Browsing category configuration contains a blank id");
            }
        }
        // Duplicate ids are impossible in a map, but a parent reference must resolve.
        for (Map.Entry<String, String> edge : parentById.entrySet()) {
            if (!nameById.containsKey(edge.getValue())) {
                throw new IllegalArgumentException(
                        "Browsing category '" + edge.getKey() + "' references missing parent '"
                                + edge.getValue() + "'");
            }
        }
        // Detect cycles in the parent edges.
        Set<String> visited = new LinkedHashSet<>();
        for (String id : nameById.keySet()) {
            if (hasCycle(id, visited, new LinkedHashSet<>())) {
                throw new IllegalArgumentException(
                        "Browsing category configuration contains a cycle involving '" + id + "'");
            }
        }
    }

    private boolean hasCycle(String id, Set<String> visited, Set<String> stack) {
        if (visited.contains(id)) {
            return false;
        }
        if (!stack.add(id)) {
            return true;
        }
        String parent = parentById.get(id);
        if (parent != null && hasCycle(parent, visited, stack)) {
            return true;
        }
        stack.remove(id);
        visited.add(id);
        return false;
    }

    private void computePaths() {
        for (String id : nameById.keySet()) {
            List<String> path = new ArrayList<>();
            String cursor = id;
            while (cursor != null) {
                path.add(0, cursor);
                cursor = parentById.get(cursor);
            }
            pathIdsById.put(id, List.copyOf(path));
        }
        for (String id : nameById.keySet()) {
            if (parentById.get(id) == null) {
                rootIds.add(id);
            }
        }
    }
}
