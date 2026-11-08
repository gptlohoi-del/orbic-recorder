package com.example.orbicrecorder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot AUDIO LEVEL trigger, not a voice-activity gate.
 * Input is 16-bit little-endian mono PCM. It never removes samples after firing.
 * Independent of Android so it can be unit-tested on a desktop JVM.
 */
public final class InputSignalTrigger {
    private final int triggerDb;
    private final int bufferFrames;
    private final int consecutiveFrames;
    private final ArrayDeque<byte[]> preRoll = new ArrayDeque<>();
    private int consecutive;
    private boolean fired;
    private double lastDb = -120;

    public InputSignalTrigger(int triggerDb, int bufferFrames, int consecutiveFrames) {
        if (triggerDb < -60 || triggerDb > -15 || bufferFrames < 1 || consecutiveFrames < 1)
            throw new IllegalArgumentException("Invalid trigger settings");
        this.triggerDb = triggerDb;
        this.bufferFrames = bufferFrames;
        this.consecutiveFrames = consecutiveFrames;
    }

    /** Add exactly one PCM frame. True exactly once when signal is stable enough. */
    public boolean offer(byte[] frame) {
        if (fired) return false;
        if (frame == null || frame.length == 0 || (frame.length & 1) != 0)
            throw new IllegalArgumentException("Expected non-empty PCM16 frame");
        // Own a copy because AudioRecord reuses its input array for every read.
        preRoll.addLast(frame.clone());
        while (preRoll.size() > bufferFrames) preRoll.removeFirst();
        long power = 0;
        for (int i = 0; i < frame.length; i += 2) {
            int sample = (short) ((frame[i] & 0xff) | (frame[i + 1] << 8));
            power += (long) sample * sample;
        }
        double rms = Math.sqrt((double) power / (frame.length / 2));
        lastDb = 20 * Math.log10(Math.max(1.0e-9, rms / 32768.0));
        consecutive = lastDb >= triggerDb ? consecutive + 1 : 0;
        if (consecutive < consecutiveFrames) return false;
        fired = true;
        return true;
    }

    public double lastDb() { return lastDb; }

    /** In oldest-to-newest order; clears buffer, so no first sample is written twice. */
    public List<byte[]> drainPreRoll() {
        ArrayList<byte[]> result = new ArrayList<>(preRoll);
        preRoll.clear();
        return result;
    }
}
