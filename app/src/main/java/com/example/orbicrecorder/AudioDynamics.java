package com.example.orbicrecorder;

/**
 * Software PCM limiter for the voice-only WAV. Works on 16-bit mono little-endian
 * AudioRecord buffers. The non-VAD backup WAV receives the original PCM bytes.
 *
 * IMPORTANT: Software processing cannot repair microphone/ADC clipping that has
 * already occurred before AudioRecord returns its bytes. Android device AGC is
 * separately controlled by RecorderService when hardware supports it.
 */
final class AudioDynamics {
    private static final int RATE = RecordingArchive.SAMPLE_RATE;
    private boolean enabled = true;
    private int limitDb = -6;
    private int attackMs = 10;
    private int releaseMs = 500;
    private double gain = 1.0;

    void configure(boolean on, int ceiling, int attack, int release) {
        enabled = on;
        limitDb = Math.max(-30, Math.min(-1, ceiling));
        attackMs = Math.max(1, Math.min(250, attack));
        releaseMs = Math.max(20, Math.min(5000, release));
        if (!on) gain = 1.0;
    }

    /** Peak level *before* limiter: a value near 0 dBFS should trigger a warning. */
    static double peakDbFs(byte[] pcm) {
        int peak = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int sample = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak == 0 ? -120.0 : 20.0 * Math.log10(peak / 32768.0);
    }

    /**
     * Attenuation-only, low latency limiter with smoothing and per-sample
     * peak ceiling. The immediate ceiling guard avoids PCM overflow while
     * the attack/release envelope limits pumping between consecutive samples.
     */
    void processInPlace(byte[] pcm) {
        if (!enabled) return;
        double ceiling = 32767.0 * Math.pow(10.0, limitDb / 20.0);
        double attackFactor = Math.exp(-1000.0 / (RATE * (double) attackMs));
        double releaseFactor = Math.exp(-1000.0 / (RATE * (double) releaseMs));
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int sample = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            double magnitude = Math.abs(sample);
            double wanted = magnitude <= ceiling ? 1.0 : ceiling / magnitude;
            double coefficient = wanted < gain ? attackFactor : releaseFactor;
            gain = wanted + coefficient * (gain - wanted);
            // Guard against brief peaks before the attack envelope catches up.
            double safeGain = Math.min(gain, wanted);
            int output = (int) Math.round(sample * safeGain);
            output = Math.max(-32768, Math.min(32767, output));
            pcm[i] = (byte) output;
            pcm[i + 1] = (byte) (output >>> 8);
        }
    }
}
