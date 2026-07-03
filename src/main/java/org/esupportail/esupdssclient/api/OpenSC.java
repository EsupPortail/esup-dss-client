package org.esupportail.esupdssclient.api;

public class OpenSC implements Product {

    private String version;
    private boolean enabled = true;

    public OpenSC() {
        super();
    }

    public OpenSC(String version, boolean enabled) {
        this.version = version;
        this.enabled = enabled;
    }

    public String getLabel() {
        if (enabled) {
            return "Gestionnaire de certificats matériels (" + (version != null ? version : "OpenSC") + ")";
        } else {
            return "Gestionnaire de certificats matériels (OpenSC non détecté)";
        }
    }

    public String getVersion() {
        return version;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
