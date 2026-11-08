import com.example.orbicrecorder.InputSignalTrigger;
import java.util.List;

public final class InputSignalTriggerTest {
    private static byte[] frame(int amplitude) {
        byte[] f = new byte[640];
        for (int i=0; i<f.length; i+=2) {
            f[i] = (byte)(amplitude & 255);
            f[i+1] = (byte)((amplitude >>> 8) & 255);
        }
        return f;
    }
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        InputSignalTrigger gate = new InputSignalTrigger(-40, 50, 5);
        for (int i=0; i<90; i++) check(!gate.offer(frame(0)), "silence cannot start recording");
        check(!gate.offer(frame(1000)), "one click cannot start");
        for (int i=0; i<3; i++) check(!gate.offer(frame(0)), "reset after click");
        for (int i=0; i<4; i++) check(!gate.offer(frame(1000)), "must qualify for five frames");
        check(gate.offer(frame(1000)), "qualified signal starts recording once");
        List<byte[]> buffered = gate.drainPreRoll();
        check(buffered.size()==50, "exact 1s of pre-roll, no unbounded memory");
        check(buffered.get(buffered.size()-1)[0] == frame(1000)[0], "trigger frame retained");
        check(gate.drainPreRoll().isEmpty(), "pre-roll drained once");
        check(!gate.offer(frame(1000)), "one-shot trigger never retriggers");
        InputSignalTrigger low = new InputSignalTrigger(-60, 50, 5);
        for (int i=0; i<5; i++) check(i==4 ? low.offer(frame(40)) : !low.offer(frame(40)), "low-level sensitivity setting");
        InputSignalTrigger high = new InputSignalTrigger(-15, 50, 5);
        for (int i=0; i<15; i++) check(!high.offer(frame(1000)), "a weak level cannot activate a high target");
        System.out.println("PASS: input one-shot trigger, stable gate, 1s PCM pre-roll, no duplicate activation");
    }
}
