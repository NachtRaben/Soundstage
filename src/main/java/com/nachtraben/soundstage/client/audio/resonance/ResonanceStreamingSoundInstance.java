package com.nachtraben.soundstage.client.audio.resonance;

import com.nachtraben.soundstage.client.audio.ClientAudioReceiver;
import com.nachtraben.soundstage.client.audio.StreamingSoundInstance;
import com.nachtraben.soundstage.core.SpeakerAcoustics;
import dev.nachtraben.resonance.api.DirectionalSound;
import dev.nachtraben.resonance.api.Facing;
import dev.nachtraben.resonance.api.RadiationPattern;
import dev.nachtraben.resonance.api.SelfTimedSound;
import net.minecraft.core.BlockPos;

public class ResonanceStreamingSoundInstance extends StreamingSoundInstance
        implements DirectionalSound, SelfTimedSound {

    public static StreamingSoundInstance create(BlockPos pos, ClientAudioReceiver receiver,
                                                 SpeakerAcoustics initialAcoustics) {
        return initialize(new ResonanceStreamingSoundInstance(pos, receiver.receiverPos),
                receiver, initialAcoustics);
    }

    protected ResonanceStreamingSoundInstance(BlockPos pos, BlockPos receiverPos) {
        super(pos, receiverPos);
    }

    @Override
    public RadiationPattern radiationPattern() {
        return radiationPattern(getAcoustics().getDispersion());
    }

    public static RadiationPattern radiationPattern(SpeakerAcoustics.Dispersion dispersion) {
        return switch (dispersion) {
            case CARDIOID -> RadiationPattern.CARDIOID;
            case BIDIRECTIONAL -> RadiationPattern.BIDIRECTIONAL;
            case CONE -> RadiationPattern.CONE;
            case OMNIDIRECTIONAL -> RadiationPattern.OMNIDIRECTIONAL;
        };
    }

    @Override
    public Facing facing() {
        return facing(facingSnapshot());
    }

    public static Facing facing(StreamingSoundInstance.FacingVector v) {
        return new Facing(v.x(), v.y(), v.z());
    }

    @Override
    public double directivity() {
        return getAcoustics().getDirectivity();
    }

    @Override
    public double beamHalfAngleDegrees() {
        return getAcoustics().getBeamAngle();
    }

    @Override
    public double apertureRadiusMetres() {
        return getAcoustics().getApertureRadiusMetres();
    }
}
