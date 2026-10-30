# Orbic Recorder – Micro Conflict Test (Android 12)

Source project for an experimental APK. Choose MIC, VOICE_RECOGNITION, or UNPROCESSED before recording. Record audio, open Google Translate, start/stop its microphone, then inspect the WAV and in-app log.

## Build via GitHub Actions
Upload the **contents** of this project (including `.github`) to the root of your GitHub repo, commit to `main`, then open **Actions > Build Orbic Recorder Test APK > Run workflow**. Download the `orbic-recorder-test-apk` artifact after success and install the APK.

**Important:** Debug APK signing may differ from a previously installed APK. If Android rejects the update due to a signature mismatch, do not uninstall the old app before backing up your recordings and other app data. For long-term production use, configure a stable release signing key.

## Test
1. Grant microphone permission.
2. Choose MIC, start recording and play steady audio into the 3.5 mm input.
3. Open Google Translate and start microphone listening for ~20 seconds; stop listening and wait another ~20 seconds.
4. Return to Recorder, stop, and inspect the WAV in `Music/OrbicRecorder/`.
5. Repeat with VOICE_RECOGNITION and UNPROCESSED.
6. Compare logs and recordings. Android may mute one capture stream; changing the audio source does not override OS policy.

Note: The amplitude indicator reports the input samples, not the true source signal if Android has silenced the stream. Very low levels can also reflect silence at the mixer. Logs are in app preferences and are visible in the app; they do not include audio content.

This is a diagnostic native app, **not yet** the Hybrid/OTA/overlay production release.
