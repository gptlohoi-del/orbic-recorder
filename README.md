# Orbic Recorder Voice Filter v3.1

Android Java native app (Android 10+, tested target: Android 12 Orbic Q10). Voice activity detection by **RMS amplitude threshold**, not AI voice recognition.

## Features
- Live audio level history (simple bars), status, elapsed capture time, kept duration, segment count.
- START, PAUSE/RESUME (skips audio while paused), STOP AND SAVE; WAV 16 kHz mono PCM16.
- Configurable threshold -70..-10 dBFS, minimum confirmation 60..1000 ms, pre-roll 0..5000 ms (default 1000), post-roll 100..5000 ms (default 1000).
- Light/dark/system themes; default reset; open latest WAV in audio player.
- Foreground microphone service; MediaStore Music/OrbicRecorder output.
- No Google Translate integration; no OTA/PiP yet.

## Build
Upload contents of this folder (including hidden `.github/`) to your GitHub repo. Actions > Build Orbic Recorder v3.1 APK > Run workflow; download `orbic-recorder-v31-apk` artifact.

## Caveats
- Amplitude gating may include noises or miss quiet speech. Test before important recordings.
- Pre-roll is up to 1s *before threshold-crossing*; min confirmation frames are retained as well. Tail is up to 1s after last loud frame.
- Pause discards captured frames and resets pre-roll on resume; total timer counts unpaused time.
- Files are finalized at STOP; abrupt process termination may leave unfinished temporary WAV.
- APK built as debug; if signatures differ, installing over a prior version can fail. Back up recordings first.
