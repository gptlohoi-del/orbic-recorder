OTA history for the live GitHub repository must be preserved.

This FULL SOURCE ZIP intentionally does NOT contain the old ota/latest.json
or ota/bundles/*.zip (v1/v2 historical bundles), to avoid overwriting the
published manifest in your existing GitHub repo.

Keep all existing GitHub ota/latest.json and ota/bundles/*.zip files.
Publishing workflow will automatically create signed ota/latest.json and
ota/bundles/8.0.1.zip (or a newer version compatible with nativeApi 8) using OTA_SIGNING_KEY_B64.
