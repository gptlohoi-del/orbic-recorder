package com.example.orbicrecorder;

import java.io.IOException;
import java.util.ArrayDeque;

/**
 * Disk-backed long-silence trimming for ECONOMY mode. No long PCM buffers in RAM.
 * Quiet spans shorter than the configured threshold are emitted unchanged.
 * At the threshold we rewind the WAV tail, retaining the first 2 seconds; once
 * sound returns (or SAVE occurs) we append the last 2 seconds from a small ring.
 * Speech-level detection applies ONLY to trimming, never to stopping capture.
 */
public final class SilenceCompactor {
    public interface Sink {
        void append(byte[] pcm) throws IOException;
        void truncateTail(long bytes) throws IOException;
    }

    private final int thresholdFrames;
    private final int edgeFrames;
    private final ArrayDeque<byte[]> recent = new ArrayDeque<>();
    private int quietFrames;
    private boolean shortened;
    private long cutFrames;

    public SilenceCompactor(int thresholdSeconds, int frameMillis, int edgeSeconds) {
        if (thresholdSeconds < 20 || thresholdSeconds > 1000 || (thresholdSeconds - 20) % 10 != 0
                || frameMillis <= 0 || edgeSeconds < 1 || edgeSeconds * 2 >= thresholdSeconds)
            throw new IllegalArgumentException("Invalid economy silence interval");
        thresholdFrames = thresholdSeconds * 1000 / frameMillis;
        edgeFrames = edgeSeconds * 1000 / frameMillis;
    }

    /** Input PCM is already limiter-processed, and the caller supplies threshold status. */
    public void accept(byte[] pcm, boolean signal, Sink sink) throws IOException {
        if (pcm == null || pcm.length == 0) throw new IllegalArgumentException("Empty PCM frame");
        if (signal) {
            flushTail(sink);
            sink.append(pcm);
            return;
        }
        quietFrames++;
        recent.addLast(pcm.clone());
        if (recent.size() > edgeFrames) recent.removeFirst();
        if (!shortened) {
            sink.append(pcm);
            if (quietFrames >= thresholdFrames) {
                long removeFrames = quietFrames - edgeFrames;
                sink.truncateTail(removeFrames * pcm.length);
                cutFrames += removeFrames;
                shortened = true;
            }
        } else {
            // Do not write the middle of a long quiet period to the WAV.
            cutFrames++;
        }
    }

    /** Called on SAVE and immediately before writing the next audible frame. */
    public void flushTail(Sink sink) throws IOException {
        if (shortened) {
            for (byte[] block : recent) {
                sink.append(block);
                cutFrames--;
            }
        }
        recent.clear();
        quietFrames = 0;
        shortened = false;
    }

    public boolean inQuietSpan() { return quietFrames > 0; }
    public long cutFrames() { return cutFrames; }
}
