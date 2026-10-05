package com.nachtraben.soundstage.client.render;

public final class SodiumRebuilds {
    public static final boolean DROPPED =
            com.nachtraben.soundstage.RigStaging.sessionStages("soundstage:block-in-first-build", "rebuild-dropped")
                    || com.nachtraben.soundstage.RigStaging.sessionStages("soundstage:screen-bars-true", "rebuild-dropped");

    private SodiumRebuilds() {
    }
}
