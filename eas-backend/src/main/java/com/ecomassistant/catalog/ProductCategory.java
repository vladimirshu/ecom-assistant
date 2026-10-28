package com.ecomassistant.catalog;

/**
 * The closed list of catalog categories. Each category has a stable {@code slug} (the
 * cross-project contract with the frontend's image directory) and a human-readable
 * {@code displayName} used in prompts and embedded text.
 *
 * <p>Categories are stored as the slug; the display name is presentation-only and is
 * not stored.
 */
public enum ProductCategory {

    PUFFER("puffer", "Puffer Jackets"),
    PARKA("parka", "Parkas"),
    COAT("coat", "Coats"),
    RAIN("rain", "Rain Jackets"),
    SOFTSHELL("softshell", "Softshell Jackets"),
    FLEECE("fleece", "Fleece Jackets"),
    TRACK("track", "Track Jackets"),
    TECHNICAL("technical", "Technical Jackets"),
    FIELD("field", "Field Jackets"),
    BOMBER("bomber", "Bomber Jackets"),
    DENIM("denim", "Denim Jackets"),
    LEATHER("leather", "Leather Jackets"),
    WINDBREAKER("windbreaker", "Windbreakers");

    private final String slug;
    private final String displayName;

    ProductCategory(String slug, String displayName) {
        this.slug = slug;
        this.displayName = displayName;
    }

    /** The stable, storage-level identifier of the category. */
    public String slug() {
        return slug;
    }

    /** The human-readable category name used in prompts and embedded text. */
    public String displayName() {
        return displayName;
    }

    /**
     * Resolves a category from its slug, or returns {@code null} when the slug is
     * unknown. Used both by the import validation (to reject unknown categories) and when
     * reading products back from the vector store payload.
     */
    public static ProductCategory fromSlug(String slug) {
        if (slug == null) {
            return null;
        }
        for (ProductCategory category : values()) {
            if (category.slug.equals(slug)) {
                return category;
            }
        }
        return null;
    }
}
