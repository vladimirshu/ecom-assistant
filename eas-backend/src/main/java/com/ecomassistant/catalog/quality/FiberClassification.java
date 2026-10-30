package com.ecomassistant.catalog.quality;

import com.ecomassistant.catalog.facts.Fiber;
import com.ecomassistant.catalog.facts.MaterialComponent;

import java.util.Set;

/**
 * Controlled-vocabulary classification for authored material fibers. This is what makes
 * exclusion judgments deterministic: a "no wool / no animal fiber", "no down" or "no
 * leather" benchmark query is evaluated against known composition facts, not against the
 * presence or absence of a marketing keyword in the description.
 *
 * <p>Fibers are grouped into plant/cellulosic, synthetic and animal categories. Cashmere is
 * an animal fiber even though its name does not contain the word "wool". Down and feather
 * are animal fill; leather and suede are animal hide.
 */
public final class FiberClassification {

    private static final Set<String> WOOL_FAMILY = Set.of(
            "wool", "merino", "lambswool", "cashmere", "alpaca", "mohair", "angora", "silk");

    private static final Set<String> ANIMAL_DOWN = Set.of("down", "feather");

    private static final Set<String> LEATHER = Set.of("leather", "suede");

    /** Every fiber name the authoring schema allows. */
    public static final Set<String> KNOWN_FIBERS = Set.of(
            // Plant / cellulosic
            "cotton", "linen", "viscose", "rayon", "tencel", "lyocell", "modal",
            // Synthetic
            "polyester", "nylon", "polyamide", "elastane", "spandex", "acrylic",
            // Animal wool family
            "wool", "merino", "lambswool", "cashmere", "alpaca", "mohair", "angora", "silk",
            // Animal fill / hide
            "down", "feather", "leather", "suede");

    private FiberClassification() {
    }

    /** Whether a fiber is an animal/wool-family fiber (excluded by "no wool / no animal fiber"). */
    public static boolean isAnimalFiber(String fiber) {
        return WOOL_FAMILY.contains(fiber) || ANIMAL_DOWN.contains(fiber) || LEATHER.contains(fiber);
    }

    /** Whether a fiber is wool-family (wool, cashmere, merino, etc.), for "no wool" semantics. */
    public static boolean isWoolFamily(String fiber) {
        return WOOL_FAMILY.contains(fiber);
    }

    /** Whether a fiber is animal down or feather, for "no down" semantics. */
    public static boolean isAnimalDown(String fiber) {
        return ANIMAL_DOWN.contains(fiber);
    }

    /** Whether a fiber is animal leather/suede, for "no leather" semantics. */
    public static boolean isLeather(String fiber) {
        return LEATHER.contains(fiber);
    }

    /** Whether a {@code known} component contains any fiber matching the given predicate. */
    public static boolean anyFiber(MaterialComponent component, java.util.function.Predicate<String> predicate) {
        if (component == null || !component.isKnown()) {
            return false;
        }
        for (Fiber fiber : component.fibers()) {
            if (predicate.test(fiber.fiber())) {
                return true;
            }
        }
        return false;
    }
}
