package com.ecomassistant.catalog.facts;

/**
 * The three material components an authored profile distinguishes. Shell and lining are
 * outer/lining fabrics; fill is insulation (relevant to down versus synthetic judgments).
 */
public record Materials(MaterialComponent shell, MaterialComponent lining, MaterialComponent fill) {

    public Materials {
        shell = shell == null ? MaterialComponent.notApplicable() : shell;
        lining = lining == null ? MaterialComponent.notApplicable() : lining;
        fill = fill == null ? MaterialComponent.notApplicable() : fill;
    }
}
