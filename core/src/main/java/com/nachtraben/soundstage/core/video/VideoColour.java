package com.nachtraben.soundstage.core.video;

import java.util.Locale;
import java.util.Set;

public record VideoColour(String transfer, String primaries, String matrix, String range,
                          int dolbyVisionProfile, int dolbyVisionCompatibility) {

    public static final int NO_DOLBY_VISION = -1;

    public static final VideoColour UNSTATED = new VideoColour(null, null, null, null, NO_DOLBY_VISION, -1);

    static final String PQ = "smpte2084";
    static final String HLG = "arib-std-b67";

    private static final Set<String> PRIMARIES = Set.of("bt2020", "bt709", "smpte431", "smpte432");
    private static final Set<String> MATRICES = Set.of("bt2020nc", "bt2020c", "bt709");

    public String effectiveTransfer() {
        String tagged = lower(transfer);
        if (PQ.equals(tagged) || HLG.equals(tagged)) return tagged;
        if (dolbyVisionProfile == NO_DOLBY_VISION) return tagged;
        return switch (baseLayer()) {
            case 0, 1, 6 -> PQ;
            case 4 -> HLG;
            default -> tagged;
        };
    }

    public boolean isHdr() {
        String effective = effectiveTransfer();
        return PQ.equals(effective) || HLG.equals(effective);
    }

    public boolean colourWillBeOff() {
        return dolbyVisionProfile != NO_DOLBY_VISION && baseLayer() == 0;
    }

    private int baseLayer() {
        return dolbyVisionProfile == 5 ? 0 : dolbyVisionCompatibility;
    }

    String zscaleInput() {
        boolean ipt = colourWillBeOff();
        return "tin=" + effectiveTransfer()
                + ":pin=" + (ipt ? "bt2020" : known(primaries, PRIMARIES, "bt2020"))
                + ":min=" + (ipt ? "bt2020nc" : known(matrix, MATRICES, "bt2020nc"))
                + ":rin=" + ("pc".equals(lower(range)) ? "pc" : "tv");
    }

    public String describe() {
        String curve = PQ.equals(effectiveTransfer()) ? "PQ" : HLG.equals(effectiveTransfer()) ? "HLG" : "SDR";
        String text = curve + ", " + known(primaries, PRIMARIES, "bt2020");
        return dolbyVisionProfile == NO_DOLBY_VISION ? text : text + ", Dolby Vision profile " + dolbyVisionProfile;
    }

    private static String known(String value, Set<String> allowed, String otherwise) {
        String key = lower(value);
        return key != null && allowed.contains(key) ? key : otherwise;
    }

    private static String lower(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
