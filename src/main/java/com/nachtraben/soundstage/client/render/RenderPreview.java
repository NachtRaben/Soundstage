package com.nachtraben.soundstage.client.render;

public final class RenderPreview {
    private RenderPreview() {}

    public static volatile Float partyLevel;
    public static volatile Boolean partyPlaying;
    public static volatile Float tubeGlow;
    public static volatile boolean suppressTubeHalo;
    public static volatile Float speakerExcursion;
    public static volatile boolean wooferMechanismOnly;
    public static volatile boolean wooferFullBright;
    public static volatile boolean wooferPinnedAtRest;
    public static volatile boolean bypassGeometryCache;

    public static volatile boolean forceRecordedReplay;

    public static volatile boolean suppressBlendedPasses;
}
