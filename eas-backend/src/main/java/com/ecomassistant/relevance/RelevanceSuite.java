package com.ecomassistant.relevance;

import com.ecomassistant.catalog.facts.FactProduct;
import com.ecomassistant.catalog.quality.FiberClassification;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The versioned judged search-scenario suite and a fact-based constraint matcher. The suite
 * records its expected judgments against a source catalog revision; the matcher verifies
 * the deterministic, fact-derivable hard constraints (price, material exclusions,
 * waterproof/water-resistant, packable, hood, stretch and root membership) without calling
 * any model. Soft style/occasion judgments remain human/agent review.
 */
public record RelevanceSuite(
        int schemaVersion,
        String suiteId,
        String sourceCatalogRevision,
        List<RelevanceCase> cases) {

    public record NearMiss(String sku, String reason) {
    }

    public record RelevanceCase(
            String id,
            String family,
            String split,
            String language,
            List<String> turns,
            int expectedStatus,
            boolean broad,
            boolean expectedEmpty,
            List<String> hardConstraints,
            List<String> eligibleSkus,
            List<String> acceptableSkus,
            List<NearMiss> nearMissSkus,
            String notes) {

        public RelevanceCase {
            turns = turns == null ? List.of() : List.copyOf(turns);
            hardConstraints = hardConstraints == null ? List.of() : List.copyOf(hardConstraints);
            eligibleSkus = eligibleSkus == null ? List.of() : List.copyOf(eligibleSkus);
            acceptableSkus = acceptableSkus == null ? List.of() : List.copyOf(acceptableSkus);
            nearMissSkus = nearMissSkus == null ? List.of() : List.copyOf(nearMissSkus);
        }

        public boolean isHeldOut() {
            return "held-out".equals(split);
        }
    }

    public static RelevanceSuite loadClasspath(String resource) {
        try (var in = new ClassPathResource(resource).getInputStream()) {
            return mapper().readValue(in, RelevanceSuite.class);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read relevance suite from " + resource, e);
        }
    }

    public static RelevanceSuite load(Path file) {
        try (var in = java.nio.file.Files.newInputStream(file)) {
            return mapper().readValue(in, RelevanceSuite.class);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read relevance suite from " + file, e);
        }
    }

    private static JsonMapper mapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    // --- Fact-based hard-constraint matching -----------------------------------------

    private static final Pattern PRICE_LT = Pattern.compile("price\\s*<\\s*([0-9]+)");
    private static final Pattern PRICE_LE = Pattern.compile("price\\s*<=\\s*([0-9]+)");
    private static final Pattern UNDER_EUR = Pattern.compile("under\\s+([0-9]+)\\s*euros?");
    private static final Pattern UP_TO_EUR = Pattern.compile("up to\\s+([0-9]+)\\s*euros?");

    /**
     * Whether a product satisfies every fact-derivable hard constraint in the list.
     * Constraints the matcher cannot parse are skipped, so only the deterministic
     * material/price/protection/root predicates are enforced here.
     */
    public static boolean satisfiesHardConstraints(FactProduct product, List<String> constraints) {
        for (String raw : constraints) {
            String c = raw.toLowerCase(Locale.ROOT);
            if (!satisfiesOne(product, c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean satisfiesOne(FactProduct p, String c) {
        BigDecimal price = p.price();
        Matcher m;

        if ((m = PRICE_LT.matcher(c)).find()) {
            return price != null && price.compareTo(new BigDecimal(m.group(1))) < 0;
        }
        if ((m = PRICE_LE.matcher(c)).find()) {
            return price != null && price.compareTo(new BigDecimal(m.group(1))) <= 0;
        }
        if ((m = UNDER_EUR.matcher(c)).find()) {
            return price != null && price.compareTo(new BigDecimal(m.group(1))) < 0;
        }
        if ((m = UP_TO_EUR.matcher(c)).find()) {
            return price != null && price.compareTo(new BigDecimal(m.group(1))) <= 0;
        }

        if (c.contains("no animal fiber") || c.contains("no wool") || c.contains("no cashmere")
                || c.contains("no animal hair")) {
            return !hasAnimalFiber(p);
        }
        if (c.contains("no down") || c.contains("no animal down") || c.contains("no echte daunen")) {
            return !hasAnimalDown(p);
        }
        if (c.contains("no leather") || c.contains("no suede") || c.contains("leather-free")) {
            return !hasLeather(p);
        }

        if (c.contains("water-resistant")) {
            return isWaterResistant(p);
        }
        if (c.contains("waterproof")) {
            return isWaterproof(p);
        }

        if (c.contains("no hood")) {
            return "none".equals(p.facts().hood());
        }
        if (matchesWord(c, "hood")) {
            return !"none".equals(p.facts().hood());
        }

        if (c.contains("packable")) {
            return Boolean.TRUE.equals(p.facts().packable());
        }

        if (c.contains("stretch")) {
            String stretch = p.facts().stretch();
            return stretch != null && !"none".equals(stretch);
        }

        // Check the women root before the men root: "women membership" contains the
        // substring "men membership", so word-boundary matching is used to avoid that trap.
        if (c.contains("women's") || matchesWord(c, "women")) {
            if (!isRoot(p, "women")) {
                return false;
            }
        }
        if (c.contains("men's") || matchesWord(c, "men")) {
            if (!isRoot(p, "men")) {
                return false;
            }
        }

        // Unknown constraint: not enforced here.
        return true;
    }

    private static boolean matchesWord(String text, String word) {
        return Pattern.compile("\\b" + word + "\\b").matcher(text).find();
    }

    private static boolean isRoot(FactProduct p, String root) {
        for (String id : p.categoryIds()) {
            if (id.equals(root) || id.startsWith(root + "-")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnimalFiber(FactProduct p) {
        return anyFiber(p, FiberClassification::isAnimalFiber);
    }

    private static boolean hasAnimalDown(FactProduct p) {
        return anyFiber(p, FiberClassification::isAnimalDown);
    }

    private static boolean hasLeather(FactProduct p) {
        return anyFiber(p, FiberClassification::isLeather);
    }

    private static boolean anyFiber(FactProduct p, java.util.function.Predicate<String> predicate) {
        return FiberClassification.anyFiber(p.facts().materials().shell(), predicate)
                || FiberClassification.anyFiber(p.facts().materials().lining(), predicate)
                || FiberClassification.anyFiber(p.facts().materials().fill(), predicate);
    }

    private static boolean isWaterproof(FactProduct p) {
        return "waterproof".equals(p.facts().weatherProtection());
    }

    private static boolean isWaterResistant(FactProduct p) {
        String wp = p.facts().weatherProtection();
        return "water-resistant".equals(wp) || "waterproof".equals(wp);
    }
}
