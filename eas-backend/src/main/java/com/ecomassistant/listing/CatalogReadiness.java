package com.ecomassistant.listing;

import org.springframework.stereotype.Component;

/**
 * In-memory marker of the verified, complete catalog revision. It is set only after a
 * successful import/upgrade has upserted and verified every expected SKU, and is reset on
 * restart (so recovery re-verifies during startup). Catalog listing refuses to serve until
 * this is set.
 */
@Component
public class CatalogReadiness {

    private volatile String verifiedRevision;

    public void markVerified(String revision) {
        this.verifiedRevision = revision;
    }

    public String verifiedRevision() {
        return verifiedRevision;
    }

    public boolean isReady() {
        return verifiedRevision != null;
    }
}
