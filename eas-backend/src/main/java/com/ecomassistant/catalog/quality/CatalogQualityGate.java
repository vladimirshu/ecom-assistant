package com.ecomassistant.catalog.quality;

import com.ecomassistant.catalog.CatalogCategoryRegistry;
import com.ecomassistant.catalog.ProductCategory;
import com.ecomassistant.catalog.facts.CatalogFacts;
import com.ecomassistant.catalog.facts.FactProduct;
import com.ecomassistant.catalog.facts.Facts;
import com.ecomassistant.catalog.facts.Fiber;
import com.ecomassistant.catalog.facts.MaterialComponent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The reproducible offline seed quality gate. It validates the authoring-facts source and
 * its exported CSV without making any model or vector-store calls, and produces a
 * SKU-addressable report. Blocking issues must be resolved before publication.
 *
 * <p>The gate covers: schema and percentages, unique identities, 200-design coverage and
 * per-root/style allocation, browsing memberships, image keys, price bands, normalized
 * duplicate descriptions, functional-signature diversity within each garment style, and
 * lightweight title/description versus fact consistency checks.
 */
public final class CatalogQualityGate {

    /** Expected per-root garment-style allocation (jacket/coat sub-styles plus the four others). */
    private static final Map<String, Integer> JACKET_COAT_ALLOCATION = Map.ofEntries(
            Map.entry("puffer", 5),
            Map.entry("parka", 3),
            Map.entry("coat", 7),
            Map.entry("rain", 5),
            Map.entry("softshell", 4),
            Map.entry("fleece", 4),
            Map.entry("track", 3),
            Map.entry("technical", 4),
            Map.entry("field", 3),
            Map.entry("bomber", 3),
            Map.entry("denim", 3),
            Map.entry("leather", 3),
            Map.entry("windbreaker", 3));

    private static final Set<String> WARMTH = Set.of("none", "light", "medium", "high", "extreme", "unknown");
    private static final Set<String> WEATHER_PROTECTION =
            Set.of("none", "water-resistant", "waterproof", "unknown");
    private static final Set<String> WIND_PROTECTION = Set.of("none", "low", "medium", "high", "unknown");
    private static final Set<String> HOOD = Set.of("none", "fixed", "removable", "unknown");
    private static final Set<String> FIT = Set.of("slim", "regular", "relaxed", "tailored", "athletic", "loose");
    private static final Set<String> STRETCH = Set.of("none", "low", "medium", "high");
    private static final Set<String> SEASONS = Set.of("spring", "summer", "autumn", "winter");

    private static final int TARGET_PRODUCTS = 200;
    private static final int PER_ROOT = 100;
    private static final int MIN_DESC_WORDS = 45;
    private static final int MAX_DESC_WORDS = 80;
    private static final int MAX_TITLE_LENGTH = 80;

    private static final List<String> DIMENSIONS = List.of(
            "shell", "lining", "fill", "warmth", "seasons", "weatherProtection", "windProtection",
            "hood", "fit", "stretch", "pockets", "packable", "occasions");

    private final CatalogCategoryRegistry registry;

    public CatalogQualityGate(CatalogCategoryRegistry registry) {
        this.registry = registry;
    }

    public QualityReport audit(CatalogFacts facts) {
        QualityReport report = new QualityReport();
        List<FactProduct> products = facts.products();
        report.setProductCount(products.size());

        checkUniqueSkus(products, report);
        checkAllocation(products, report);
        for (FactProduct product : products) {
            checkProduct(product, report);
        }
        checkPriceBands(products, report);
        checkDuplicateDescriptions(products, report);
        checkFunctionalDiversity(products, report);

        return report;
    }

    private void checkUniqueSkus(List<FactProduct> products, QualityReport report) {
        Set<String> seen = new java.util.HashSet<>();
        for (FactProduct product : products) {
            if (product.sku() == null || product.sku().isBlank()) {
                report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "identity", "",
                        "product with a blank SKU"));
            }
            else if (!seen.add(product.sku())) {
                report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "identity", product.sku(),
                        "duplicate SKU"));
            }
        }
    }

    private void checkAllocation(List<FactProduct> products, QualityReport report) {
        if (products.size() < TARGET_PRODUCTS) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "coverage", "",
                    "expected at least " + TARGET_PRODUCTS + " distinct products, found " + products.size()));
        }

        Map<String, Map<String, Integer>> countsByRoot = new LinkedHashMap<>();
        for (FactProduct product : products) {
            String root = rootOf(product);
            if (root == null) {
                continue;
            }
            countsByRoot.computeIfAbsent(root, k -> new LinkedHashMap<>())
                    .merge(product.category(), 1, Integer::sum);
        }

        for (String root : List.of("men", "women")) {
            Map<String, Integer> counts = countsByRoot.getOrDefault(root, Map.of());
            int rootTotal = counts.values().stream().mapToInt(Integer::intValue).sum();
            if (rootTotal != PER_ROOT) {
                report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "coverage", "",
                        root + " root has " + rootTotal + " products, expected " + PER_ROOT));
            }
            for (Map.Entry<String, Integer> entry : JACKET_COAT_ALLOCATION.entrySet()) {
                int actual = counts.getOrDefault(entry.getKey(), 0);
                if (actual != entry.getValue()) {
                    report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "coverage", "",
                            root + " " + entry.getKey() + " has " + actual + " designs, expected "
                                    + entry.getValue()));
                }
            }
            for (Map.Entry<String, Integer> entry : Map.of("sweater", 12, "hoodie", 8, "jeans", 15, "trousers", 15)
                    .entrySet()) {
                int actual = counts.getOrDefault(entry.getKey(), 0);
                if (actual != entry.getValue()) {
                    report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "coverage", "",
                            root + " " + entry.getKey() + " has " + actual + " designs, expected "
                                    + entry.getValue()));
                }
            }
        }

        for (FactProduct product : products) {
            report.addCategoryCount(product.category(), 1);
        }
    }

    private void checkProduct(FactProduct product, QualityReport report) {
        String sku = product.sku() == null ? "" : product.sku();

        if (isBlank(product.title())) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "schema", sku, "missing title"));
        }
        else if (product.title().length() > MAX_TITLE_LENGTH) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "schema", sku,
                    "title longer than " + MAX_TITLE_LENGTH + " characters"));
        }
        if (isBlank(product.category()) || ProductCategory.fromSlug(product.category()) == null) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "schema", sku,
                    "unknown or missing category '" + product.category() + "'"));
        }
        if (product.categoryIds() == null || product.categoryIds().isEmpty()) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "membership", sku,
                    "no browsing category ids"));
        }
        else {
            for (String id : product.categoryIds()) {
                if (!registry.isKnown(id)) {
                    report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "membership", sku,
                            "unknown browsing category id '" + id + "'"));
                }
            }
        }
        if (product.colors() == null || product.colors().isEmpty()) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "schema", sku, "no colors"));
        }
        if (product.sizes() == null || product.sizes().isEmpty()) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "schema", sku, "no sizes"));
        }
        if (product.price() == null || product.price().signum() <= 0) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "schema", sku, "price must be greater than 0"));
        }
        if (isBlank(product.description())) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "schema", sku, "missing description"));
        }
        else {
            int words = wordCount(product.description());
            if (words < MIN_DESC_WORDS || words > MAX_DESC_WORDS) {
                report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "description", sku,
                        "description has " + words + " words, expected " + MIN_DESC_WORDS + "-"
                                + MAX_DESC_WORDS));
            }
        }
        checkImageKey(product, report);
        if (product.facts() == null) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "facts", sku, "missing authoring facts"));
        }
        else {
            checkFacts(product, report);
        }
    }

    private void checkImageKey(FactProduct product, QualityReport report) {
        String key = product.imageKey();
        if (key == null || key.isBlank()) {
            return;
        }
        if (!key.startsWith("categories/") || !key.endsWith(".webp")) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "image", product.sku(),
                    "image key '" + key + "' must be categories/<key>.webp"));
        }
    }

    private void checkFacts(FactProduct product, QualityReport report) {
        Facts facts = product.facts();
        String sku = product.sku() == null ? "" : product.sku();

        checkComponent(facts.materials().shell(), "shell", sku, report);
        checkComponent(facts.materials().lining(), "lining", sku, report);
        checkComponent(facts.materials().fill(), "fill", sku, report);

        checkEnum(facts.warmth(), WARMTH, "warmth", sku, report);
        checkEnum(facts.weatherProtection(), WEATHER_PROTECTION, "weatherProtection", sku, report);
        checkEnum(facts.windProtection(), WIND_PROTECTION, "windProtection", sku, report);
        checkEnum(facts.hood(), HOOD, "hood", sku, report);
        checkEnum(facts.fit(), FIT, "fit", sku, report);
        checkEnum(facts.stretch(), STRETCH, "stretch", sku, report);
        for (String season : facts.seasons()) {
            if (!SEASONS.contains(season)) {
                report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "facts", sku,
                        "unknown season '" + season + "'"));
            }
        }

        checkTitleFactConsistency(product, report);
    }

    private void checkComponent(MaterialComponent component, String name, String sku, QualityReport report) {
        if (component.isKnown()) {
            if (component.fibers().isEmpty()) {
                report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "materials", sku,
                        name + " is known but lists no fibers"));
                return;
            }
            int total = 0;
            for (Fiber fiber : component.fibers()) {
                if (!FiberClassification.KNOWN_FIBERS.contains(fiber.fiber())) {
                    report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "materials", sku,
                            name + " uses unknown fiber '" + fiber.fiber() + "'"));
                }
                if (fiber.percent() <= 0 || fiber.percent() > 100) {
                    report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "materials", sku,
                            name + " fiber '" + fiber.fiber() + "' has invalid percent " + fiber.percent()));
                }
                total += fiber.percent();
            }
            if (total != 100) {
                report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "materials", sku,
                        name + " fiber percentages sum to " + total + ", expected 100"));
            }
        }
        else if (!component.fibers().isEmpty()) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "materials", sku,
                    name + " is " + component.state() + " but lists fibers"));
        }
    }

    private void checkEnum(String value, Set<String> allowed, String field, String sku, QualityReport report) {
        if (value == null) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "facts", sku,
                    "missing " + field + " value"));
        }
        else if (!allowed.contains(value)) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "facts", sku,
                    field + " has unknown value '" + value + "'"));
        }
    }

    private void checkTitleFactConsistency(FactProduct product, QualityReport report) {
        String sku = product.sku() == null ? "" : product.sku();
        Facts facts = product.facts();
        String title = product.title() == null ? "" : product.title().toLowerCase(Locale.ROOT);
        String description = product.description() == null ? "" : product.description().toLowerCase(Locale.ROOT);
        String protection = facts.weatherProtection();

        if (title.contains("waterproof") && !"waterproof".equals(protection)) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "consistency", sku,
                    "title calls it waterproof but facts say weatherProtection=" + protection));
        }
        if (description.contains("waterproof") && !"waterproof".equals(protection)
                && !"water-resistant".equals(protection)) {
            report.add(new QualityIssue(QualityIssue.Severity.REVIEW, "consistency", sku,
                    "description mentions waterproof but facts say weatherProtection=" + protection));
        }

        boolean mentionsDown = (title + " " + description).contains("down");
        MaterialComponent fill = facts.materials().fill();
        if (mentionsDown && fill.isUnknown()) {
            report.add(new QualityIssue(QualityIssue.Severity.REVIEW, "consistency", sku,
                    "down mentioned but fill composition is unknown"));
        }
        boolean mentionsWool = (title + " " + description).matches(".*\\b(wool|cashmere|merino)\\b.*");
        boolean hasAnimalWool = FiberClassification.anyFiber(facts.materials().shell(),
                FiberClassification::isWoolFamily)
                || FiberClassification.anyFiber(facts.materials().lining(), FiberClassification::isWoolFamily);
        if (mentionsWool && !hasAnimalWool) {
            report.add(new QualityIssue(QualityIssue.Severity.REVIEW, "consistency", sku,
                    "wool/cashmere/merino mentioned but facts list no wool-family fiber"));
        }
        boolean mentionsLeather = (title + " " + description).contains("leather");
        boolean hasLeather = FiberClassification.anyFiber(facts.materials().shell(), FiberClassification::isLeather)
                || FiberClassification.anyFiber(facts.materials().lining(), FiberClassification::isLeather);
        if (mentionsLeather && !hasLeather) {
            report.add(new QualityIssue(QualityIssue.Severity.REVIEW, "consistency", sku,
                    "leather mentioned but facts list no leather fiber"));
        }
    }

    private void checkPriceBands(List<FactProduct> products, QualityReport report) {
        int under50 = 0;
        int to100 = 0;
        int to200 = 0;
        int over200 = 0;
        for (FactProduct product : products) {
            BigDecimal price = product.price();
            if (price == null) {
                continue;
            }
            if (price.compareTo(new BigDecimal("50")) < 0) {
                under50++;
            }
            else if (price.compareTo(new BigDecimal("100")) < 0) {
                to100++;
            }
            else if (price.compareTo(new BigDecimal("200")) < 0) {
                to200++;
            }
            else {
                over200++;
            }
        }
        report.addPriceBandCount("<50", under50);
        report.addPriceBandCount("50-100", to100);
        report.addPriceBandCount("100-200", to200);
        report.addPriceBandCount(">=200", over200);

        requireMinimum("price < 50", under50, 20, report);
        requireMinimum("price 50-100", to100, 60, report);
        requireMinimum("price 100-200", to200, 60, report);
        requireMinimum("price >= 200", over200, 20, report);
    }

    private void requireMinimum(String label, int actual, int minimum, QualityReport report) {
        if (actual < minimum) {
            report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "price", "",
                    label + " has " + actual + " products, expected at least " + minimum));
        }
    }

    private void checkDuplicateDescriptions(List<FactProduct> products, QualityReport report) {
        Map<String, List<String>> byNormalized = new LinkedHashMap<>();
        for (FactProduct product : products) {
            if (product.description() == null) {
                continue;
            }
            byNormalized.computeIfAbsent(normalize(product.description()), k -> new ArrayList<>())
                    .add(product.sku());
        }
        for (List<String> skus : byNormalized.values()) {
            if (skus.size() > 1) {
                report.addDuplicateDescriptionGroup(skus);
                report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "duplicate-description",
                        String.join(",", skus), "normalized duplicate description shared by " + skus.size()
                                + " products"));
            }
        }
    }

    private void checkFunctionalDiversity(List<FactProduct> products, QualityReport report) {
        Map<String, List<FactProduct>> byCategory = products.stream()
                .filter(product -> product.category() != null)
                .collect(Collectors.groupingBy(FactProduct::category, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<FactProduct>> entry : byCategory.entrySet()) {
            List<FactProduct> group = entry.getValue();
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    FactProduct a = group.get(i);
                    FactProduct b = group.get(j);
                    if (a.facts() == null || b.facts() == null) {
                        continue;
                    }
                    int differing = differingDimensions(a, b);
                    if (differing == 0) {
                        report.addFunctionalDuplicateGroup(List.of(a.sku(), b.sku()));
                        report.add(new QualityIssue(QualityIssue.Severity.BLOCKING, "functional-diversity",
                                a.sku() + "," + b.sku(),
                                "identical functional profile within style " + entry.getKey()
                                        + " (audience/name/color/price do not count)"));
                    }
                    else if (differing == 1) {
                        // Same-root designs must differ in at least two dimensions. A single
                        // difference across roots (e.g. a women's cut vs a men's cut) is a review
                        // signal, not automatically a duplicate.
                        String rootA = rootOf(a);
                        String rootB = rootOf(b);
                        boolean sameRoot = rootA != null && rootA.equals(rootB);
                        QualityIssue.Severity severity = sameRoot
                                ? QualityIssue.Severity.BLOCKING
                                : QualityIssue.Severity.REVIEW;
                        report.add(new QualityIssue(severity, "functional-diversity",
                                a.sku() + "," + b.sku(),
                                (sameRoot ? "only one substantive functional difference within style "
                                        : "single functional difference across roots within style ")
                                        + entry.getKey() + " (expected at least two)"));
                    }
                }
            }
        }
    }

    private int differingDimensions(FactProduct a, FactProduct b) {
        Map<String, String> sa = signature(a);
        Map<String, String> sb = signature(b);
        int differing = 0;
        for (String dimension : DIMENSIONS) {
            if (!sa.get(dimension).equals(sb.get(dimension))) {
                differing++;
            }
        }
        return differing;
    }

    private static Map<String, String> signature(FactProduct product) {
        Facts f = product.facts();
        Map<String, String> sig = new LinkedHashMap<>();
        sig.put("shell", componentSignature(f.materials().shell()));
        sig.put("lining", componentSignature(f.materials().lining()));
        sig.put("fill", componentSignature(f.materials().fill()));
        sig.put("warmth", nullToUnknown(f.warmth()));
        sig.put("seasons", sortedJoin(f.seasons()));
        sig.put("weatherProtection", nullToUnknown(f.weatherProtection()));
        sig.put("windProtection", nullToUnknown(f.windProtection()));
        sig.put("hood", nullToUnknown(f.hood()));
        sig.put("fit", nullToUnknown(f.fit()));
        sig.put("stretch", nullToUnknown(f.stretch()));
        sig.put("pockets", sortedJoin(f.pockets()));
        sig.put("packable", f.packable() == null ? "unknown" : f.packable().toString());
        sig.put("occasions", sortedJoin(f.occasions()));
        return sig;
    }

    private static String componentSignature(MaterialComponent component) {
        if (!component.isKnown()) {
            return component.state();
        }
        TreeSet<String> fibers = new TreeSet<>();
        for (Fiber fiber : component.fibers()) {
            fibers.add(fiber.fiber());
        }
        return "known:" + String.join("+", fibers);
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String sortedJoin(List<String> values) {
        return new TreeSet<>(values).stream().collect(Collectors.joining(","));
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static int wordCount(String text) {
        return text.trim().split("\\s+").length;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String rootOf(FactProduct product) {
        for (String id : product.categoryIds()) {
            if (id.equals("men") || id.startsWith("men-")) {
                return "men";
            }
            if (id.equals("women") || id.startsWith("women-")) {
                return "women";
            }
        }
        return null;
    }
}
