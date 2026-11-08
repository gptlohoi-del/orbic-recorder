import com.example.orbicrecorder.SilenceCompactor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SilenceCompactorTest {
    static class FakeSink implements SilenceCompactor.Sink {
        List<Integer> samples = new ArrayList<>();
        @Override public void append(byte[] pcm) { samples.add((int) pcm[0] & 255); }
        @Override public void truncateTail(long bytes) throws IOException {
            if (bytes < 0 || bytes % 2 != 0 || bytes / 2 > samples.size())
                throw new IOException("Bad truncate");
            for (long i = 0; i < bytes/2; i++) samples.remove(samples.size()-1);
        }
    }
    static byte[] frame(int value) { return new byte[]{(byte) value, 0}; }
    static void test(int threshold, int quietSeconds, boolean endWithSpeech) throws Exception {
        SilenceCompactor compact = new SilenceCompactor(threshold, 1000, 2);
        FakeSink sink = new FakeSink();
        compact.accept(frame(9), true, sink);
        for (int i=0;i<quietSeconds;i++) compact.accept(frame(1), false, sink);
        if (endWithSpeech) compact.accept(frame(8), true, sink);
        else compact.flushTail(sink);
        int expectedQuiet = quietSeconds < threshold ? quietSeconds : 4;
        if (sink.samples.size() != 1+expectedQuiet+(endWithSpeech?1:0))
            throw new AssertionError("Expected quiet="+expectedQuiet+", got "+sink.samples.size());
        if (sink.samples.get(0)!=9) throw new AssertionError("Lost first speech frame");
        if (endWithSpeech && sink.samples.get(sink.samples.size()-1)!=8)
            throw new AssertionError("Lost next speech frame");
        if (compact.cutFrames()!=quietSeconds-expectedQuiet)
            throw new AssertionError("Wrong count of omitted frames: "+compact.cutFrames());
    }
    public static void main(String[] args) throws Exception {
        test(60, 0, true);
        test(60, 59, true);
        test(60, 60, true);
        test(60, 90, true);
        test(20, 19, false);
        test(20, 20, false);
        test(1000, 999, true);
        test(1000, 1000, true);
        // Real capture uses 20 ms frames (50 frames/s): preserve 100 frames at each edge.
        SilenceCompactor realFrames = new SilenceCompactor(60, 20, 2);
        FakeSink realSink = new FakeSink();
        for(int i=0;i<3000;i++) realFrames.accept(frame(1), false, realSink);
        realFrames.accept(frame(7), true, realSink);
        if(realSink.samples.size()!=201 || realFrames.cutFrames()!=2800)
            throw new AssertionError("20ms frame compaction is incorrect");
        // Split two long silences with a speech frame, both must be compacted.
        SilenceCompactor compact = new SilenceCompactor(20, 1000, 2);
        FakeSink sink = new FakeSink();
        for(int i=0;i<22;i++) compact.accept(frame(1), false, sink);
        compact.accept(frame(9), true, sink);
        for(int i=0;i<21;i++) compact.accept(frame(1), false, sink);
        compact.flushTail(sink);
        if(sink.samples.size()!=9 || compact.cutFrames()!=35)
            throw new AssertionError("Long silences not compacted independently");
        System.out.println("PASS: quiet < threshold kept; >= threshold compacted with 2s edges; 20..1000s, SAVE tail");
    }
}
