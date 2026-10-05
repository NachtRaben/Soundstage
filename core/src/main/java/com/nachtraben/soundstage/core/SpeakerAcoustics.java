package com.nachtraben.soundstage.core;

public enum SpeakerAcoustics {

    SUBWOOFER(AudioDSP.FilterType.LOW_PASS, 100.0f, Dispersion.OMNIDIRECTIONAL, 1.0f, 45.0f,
            SpeakerGeometry.apertureRadiusMetres(SpeakerGeometry.SUBWOOFER_WOOFER_WIDTH_PX,
                    SpeakerGeometry.SUBWOOFER_WOOFER_COUNT),
            30.0, 0.5, 0.45),

    SATELLITE(AudioDSP.FilterType.HIGH_PASS, 150.0f, Dispersion.CARDIOID, 1.0f, 45.0f,
            SpeakerGeometry.apertureRadiusMetres(SpeakerGeometry.SATELLITE_WOOFER_WIDTH_PX,
                    SpeakerGeometry.SATELLITE_WOOFER_COUNT),
            120.0, 0.65, 0.30),

    CENTER(AudioDSP.FilterType.HIGH_PASS, 100.0f, Dispersion.CARDIOID, 1.0f, 45.0f,
            SpeakerGeometry.apertureRadiusMetres(SpeakerGeometry.CENTER_WOOFER_WIDTH_PX,
                    SpeakerGeometry.CENTER_WOOFER_COUNT),
            90.0, 0.6, 0.35),

    PARTY_BOX(AudioDSP.FilterType.NONE, 1000.0f, Dispersion.OMNIDIRECTIONAL, 1.0f, 45.0f,
            SpeakerGeometry.apertureRadiusMetres(SpeakerGeometry.PARTY_BOX_WOOFER_WIDTH_PX,
                    SpeakerGeometry.PARTY_BOX_WOOFER_COUNT),
            55.0, 0.6, 0.35),

    MAIN(AudioDSP.FilterType.HIGH_PASS, 80.0f, Dispersion.CARDIOID, 1.0f, 45.0f,
            SpeakerGeometry.apertureRadiusMetres(SpeakerGeometry.MAIN_WOOFER_WIDTH_PX,
                    SpeakerGeometry.MAIN_WOOFER_COUNT),
            65.0, 0.55, 0.70);

    public enum Dispersion {
        OMNIDIRECTIONAL,
        CARDIOID,
        BIDIRECTIONAL,
        CONE
    }

    private final AudioDSP.FilterType filterType;
    private final float filterFrequency;
    private final Dispersion dispersion;
    private final float directivity;
    private final float beamAngle;
    private final double apertureRadiusMetres;
    private final double driverResonanceHz;
    private final double driverQ;
    private final double fullTravelReference;

    SpeakerAcoustics(AudioDSP.FilterType filterType, float filterFrequency, Dispersion dispersion,
                     float directivity, float beamAngle, double apertureRadiusMetres,
                     double driverResonanceHz, double driverQ, double fullTravelReference) {
        this.filterType = filterType;
        this.filterFrequency = filterFrequency;
        this.dispersion = dispersion;
        this.directivity = directivity;
        this.beamAngle = beamAngle;
        this.apertureRadiusMetres = apertureRadiusMetres;
        this.driverResonanceHz = driverResonanceHz;
        this.driverQ = driverQ;
        this.fullTravelReference = fullTravelReference;
    }

    public AudioDSP.FilterType getFilterType() {
        return filterType;
    }

    public float getFilterFrequency() {
        return filterFrequency;
    }

    public Dispersion getDispersion() {
        return dispersion;
    }

    public float getDirectivity() {
        return directivity;
    }

    public float getBeamAngle() {
        return beamAngle;
    }

    public double getApertureRadiusMetres() {
        return apertureRadiusMetres;
    }

    public double getDriverResonanceHz() {
        return driverResonanceHz;
    }

    public double getDriverQ() {
        return driverQ;
    }

    public double getFullTravelReference() {
        return fullTravelReference;
    }

    public static SpeakerAcoustics forBlockPath(String path) {
        if (path.contains("subwoofer")) return SUBWOOFER;
        if (path.contains("satellite")) return SATELLITE;
        if (path.contains("center")) return CENTER;
        if (path.contains("party_box")) return PARTY_BOX;
        return MAIN;
    }
}
