package com.example.orbicrecorder;

import java.util.Arrays;
import java.util.Random;

/** Plain-Java regression tests for the limiter; no Android SDK required. */
public final class AudioDynamicsTest {
    private static byte[] constant(int samples, int sample) {
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            pcm[i * 2] = (byte) sample;
            pcm[i * 2 + 1] = (byte) (sample >>> 8);
        }
        return pcm;
    }

    private static int maxAbs(byte[] pcm) {
        int peak = 0;
        for (int i = 0; i < pcm.length; i += 2) {
            int n = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8));
            peak = Math.max(peak, Math.abs(n));
        }
        return peak;
    }

    private static void expect(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        AudioDynamics d = new AudioDynamics();
        byte[] loud = constant(320, 32767);
        byte[] pristine = loud.clone();
        d.configure(true, -6, 10, 500);
        d.processInPlace(loud);
        int ceiling = (int) Math.ceil(32767 * Math.pow(10, -6 / 20.0));
        expect(maxAbs(loud) <= ceiling + 1, "peak must be limited");
        expect(maxAbs(pristine) == 32767, "original PCM must stay unchanged");
        expect(AudioDynamics.peakDbFs(pristine) > -1, "near-clip warning should trigger");
        expect(AudioDynamics.peakDbFs(constant(320, 0)) == -120, "silence level");

        // Disabled limiter must be a true bit-for-bit bypass.
        d.configure(false, -6, 10, 500);
        byte[] bypass = pristine.clone();
        d.processInPlace(bypass);
        expect(Arrays.equals(bypass, pristine), "bypass changed PCM");

        // Gain returns to 1 gradually, not as a sudden jump after a loud frame.
        d = new AudioDynamics();
        d.configure(true, -12, 5, 1000);
        for (int i = 0; i < 5; i++) d.processInPlace(constant(320, 32767));
        byte[] earlyQuiet = constant(320, 1000);
        d.processInPlace(earlyQuiet);
        int early = maxAbs(earlyQuiet);
        expect(early < 1000, "release ignored");
        int late = early;
        for (int i = 0; i < 200; i++) {
            byte[] next = constant(320, 1000);
            d.processInPlace(next);
            late = maxAbs(next);
        }
        expect(late > early && late >= 980, "release failed to recover");

        // Noise extremes including minimum signed PCM sample remain under ceiling.
        d.configure(true, -3, 1, 20);
        Random random = new Random(42);
        for (int t = 0; t < 100; t++) {
            byte[] input = new byte[640]; random.nextBytes(input);
            d.processInPlace(input);
            int p = (int) Math.ceil(32767 * Math.pow(10, -3 / 20.0));
            expect(maxAbs(input) <= p + 1, "random frame exceeded ceiling");
        }
        System.out.println("PASS: PCM limiter, backup independence, bypass, release, warning level");
    }
}
