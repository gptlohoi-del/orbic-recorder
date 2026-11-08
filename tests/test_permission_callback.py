"""Compile and exercise the actual MainActivity permission callback without Android SDK.

This catches invalid if/else syntax before the full Android build, and verifies
that automatic/manual recording preference survives the permission prompt.
"""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/example/orbicrecorder/MainActivity.java').read_text()
m = re.search(
    r'@Override public void onRequestPermissionsResult\(int code, String\[\] permissions, int\[\] results\) \{.*?\n    \}',
    source,
    re.S,
)
assert m is not None, 'Cannot find microphone permission callback'
callback = m.group(0)
java = '''
class PackageManager { static final int PERMISSION_GRANTED = 0; }
class BaseActivity {
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) { }
}
class MainActivity extends BaseActivity {
    static final int REQUEST_MIC = 101;
    boolean pendingArmPermission;
    int starts;
    int toasts;
    boolean lastAutomatic;
    void startRecording(boolean automatic) { starts++; lastAutomatic = automatic; }
    void toast(String value) { toasts++; }
''' + callback + '''
}
public class PermissionSmoke {
    static void check(boolean passed) { if (!passed) throw new AssertionError("permission callback regression"); }
    public static void main(String[] args) {
        MainActivity a = new MainActivity();
        a.pendingArmPermission = true;
        a.onRequestPermissionsResult(101, new String[0], new int[] {0});
        check(a.starts == 1 && a.lastAutomatic && !a.pendingArmPermission && a.toasts == 0);
        MainActivity b = new MainActivity();
        b.pendingArmPermission = true;
        b.onRequestPermissionsResult(101, new String[0], new int[] {-1});
        check(b.starts == 0 && b.toasts == 1 && !b.pendingArmPermission);
        MainActivity c = new MainActivity();
        c.pendingArmPermission = true;
        c.onRequestPermissionsResult(101, new String[0], new int[0]);
        check(c.starts == 0 && c.toasts == 1 && !c.pendingArmPermission);
        MainActivity d = new MainActivity();
        d.onRequestPermissionsResult(101, new String[0], new int[] {0});
        check(d.starts == 1 && !d.lastAutomatic && !d.pendingArmPermission);
    }
}
'''
with tempfile.TemporaryDirectory() as td:
    src = Path(td) / 'PermissionSmoke.java'
    src.write_text(java)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', td, str(src)], check=True)
    subprocess.run(['java', '-cp', td, 'PermissionSmoke'], check=True)
print('PASS: microphone permission callback compiles and handles auto/manual grant/denial')
