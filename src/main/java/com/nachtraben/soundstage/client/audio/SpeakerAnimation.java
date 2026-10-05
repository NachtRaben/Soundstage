package com.nachtraben.soundstage.client.audio;

import com.nachtraben.soundstage.core.ChannelMode;
import com.nachtraben.soundstage.core.ConeExcursionModel;
import com.nachtraben.soundstage.core.LevelMeterModel;
import com.nachtraben.soundstage.core.PcmRing;
import com.nachtraben.soundstage.core.SpeakerAcoustics;
import com.nachtraben.soundstage.core.TraceBuffer;

import java.nio.ShortBuffer;

public final class SpeakerAnimation {

    public record Key(ChannelMode channel, SpeakerAcoustics acoustics, float gain) {}

    private static final int HISTORY_FRAMES = PcmRing.RETAINED_FRAMES;

    private final SpeakerAcoustics acoustics;
    private final TraceBuffer excursionTrace = new TraceBuffer(HISTORY_FRAMES);
    private final TraceBuffer levelTrace = new TraceBuffer(HISTORY_FRAMES);

    private ConeExcursionModel excursionModel;
    private LevelMeterModel levelModel;

    private long tracedSequence = Long.MIN_VALUE;

    SpeakerAnimation(SpeakerAcoustics acoustics) {
        this.acoustics = acoustics;
    }

    synchronized void trace(long sequence, ShortBuffer mono, float sampleRate) {
        if (tracedSequence != Long.MIN_VALUE && sequence <= tracedSequence) {
            if (tracedSequence - sequence <= HISTORY_FRAMES) return;
            excursionModel = null;
            levelModel = null;
        }
        tracedSequence = sequence;
        if (excursionModel == null) {
            excursionModel = new ConeExcursionModel(sampleRate,
                    acoustics.getDriverResonanceHz(), acoustics.getDriverQ(),
                    acoustics.getFullTravelReference());
        }
        if (levelModel == null) {
            levelModel = new LevelMeterModel(sampleRate);
        }
        excursionTrace.record(sequence, excursionModel.trace(mono));
        levelTrace.record(sequence, levelModel.trace(mono));
    }

    float sampleExcursion(double sequence) {
        return excursionTrace.sample(sequence);
    }

    float sampleLevel(double sequence) {
        return levelTrace.sample(sequence);
    }

    float peakLevel(double sequence, int framesBack) {
        return levelTrace.peak(sequence, framesBack);
    }

    synchronized void clear() {
        excursionTrace.clear();
        levelTrace.clear();
        tracedSequence = Long.MIN_VALUE;
    }
}
