#!/usr/bin/env python3
"""Pack 3 web assets and sign latest.json using an offline/private RSA key.

Do not commit signing private key. Inject private PEM via GitHub Actions secret.
"""
import argparse
import base64
import hashlib
import json
import os
import pathlib
import re
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/ui'
OUT = ROOT / 'ota'
FILES = ('index.html', 'app.css', 'app.js')
OWNER = 'gptlohoi-del'
REPO = 'orbic-recorder'
NATIVE_API = 5
VERSION_RE = re.compile(r'[0-9]{1,5}\.[0-9]{1,5}\.[0-9]{1,5}\Z')


def version_tuple(text):
    if not isinstance(text, str) or not VERSION_RE.fullmatch(text):
        raise SystemExit(f'Invalid OTA version: {text!r} (expected N.N.N)')
    return tuple(map(int, text.split('.')))


def next_ota_version(java_source):
    # The installed APK must see a version strictly newer than its embedded UI.
    match = re.search(r'BUILTIN_VERSION\s*=\s*"([0-9.]+)"', java_source)
    if not match:
        raise SystemExit('Cannot find BUILTIN_VERSION in OtaManager.java')
    newest = version_tuple(match.group(1))

    latest = OUT / 'latest.json'
    if latest.is_file():
        older = json.loads(latest.read_text(encoding='utf-8'))['version']
        newest = max(newest, version_tuple(older))

    # Also avoid reusing a bundle number if latest.json is out of sync.
    for bundle in (OUT / 'bundles').glob('*.zip'):
        if VERSION_RE.fullmatch(bundle.stem):
            newest = max(newest, version_tuple(bundle.stem))

    major, minor, patch = newest
    if patch >= 99999:
        if minor >= 99999:
            if major >= 99999:
                raise SystemExit('OTA version components exceed 99999')
            return f'{major + 1}.0.0'
        return f'{major}.{minor + 1}.0'
    return f'{major}.{minor}.{patch + 1}'


def main():
    cli = argparse.ArgumentParser()
    cli.add_argument('--version', help='Optional manual override; normally version is automatic')
    cli.add_argument('--key', help='Private PEM file, for offline validation only')
    args = cli.parse_args()
    previous = OUT / 'latest.json'
    java_path = ROOT / 'app/src/main/java/com/example/orbicrecorder/OtaManager.java'
    java_source = java_path.read_text(encoding='utf-8')
    api_match = re.search(r'public static final int NATIVE_API\s*=\s*(\d+)', java_source)
    if not api_match or int(api_match.group(1)) != NATIVE_API:
        raise SystemExit('OTA nativeApi does not match OtaManager.java; update script before publishing')
    minimum = next_ota_version(java_source)
    version = args.version or minimum
    if version_tuple(version) < version_tuple(minimum):
        raise SystemExit(f'New OTA version must be at least {minimum}')
    print(f'Chosen OTA version: {version} (minimum acceptable: {minimum})')

    bundle_folder = OUT / 'bundles'
    bundle_folder.mkdir(parents=True, exist_ok=True)
    bundle_path = bundle_folder / (version + '.zip')
    if bundle_path.exists():
        raise SystemExit('This OTA version is already published; choose a new version')
    for name in FILES:
        if not (ASSETS / name).is_file():
            raise SystemExit(f'Missing web asset: {name}')

    # Small, immutable, version-specific file for GitHub raw download.
    with zipfile.ZipFile(bundle_path, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name in FILES:
            archive.write(ASSETS / name, arcname=name)

    sha = hashlib.sha256(bundle_path.read_bytes()).hexdigest()
    url = f'https://raw.githubusercontent.com/{OWNER}/{REPO}/main/ota/bundles/{version}.zip'
    payload = f'{version}\n{sha}\n{url}\n{NATIVE_API}\n'.encode('utf-8')
    private = args.key
    temporary = None
    if not private:
        secret = os.environ.get('OTA_SIGNING_KEY_B64', '')
        if not secret:
            raise SystemExit('Missing OTA_SIGNING_KEY_B64 secret. See README.')
        temporary = tempfile.NamedTemporaryFile(mode='wb', delete=False)
        temporary.write(base64.b64decode(secret, validate=True))
        temporary.close()
        os.chmod(temporary.name, 0o600)
        private = temporary.name
    try:
        # Prevent publishing packages that every installed APK would reject.
        match = re.search(r'PUBLIC_KEY_B64\s*=\s*"([A-Za-z0-9+/=]+)"', java_source)
        if not match:
            raise SystemExit('Cannot find pinned OTA public key in OtaManager.java')
        embedded = base64.b64decode(match.group(1), validate=True)
        supplied_public = subprocess.run(
            ['openssl', 'pkey', '-in', private, '-pubout', '-outform', 'DER'],
            capture_output=True, check=True
        ).stdout
        if supplied_public != embedded:
            raise SystemExit('OTA secret does NOT match public key embedded in the APK; do not publish')
        signature = subprocess.run(
            ['openssl', 'dgst', '-sha256', '-sign', private],
            input=payload, capture_output=True, check=True
        ).stdout
    finally:
        if temporary is not None:
            pathlib.Path(temporary.name).unlink(missing_ok=True)
    manifest = {
        'version': version,
        'nativeApi': NATIVE_API,
        'sha256': sha,
        'url': url,
        'signature': base64.b64encode(signature).decode('ascii'),
    }
    previous.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'OTA v{version}: {bundle_path.relative_to(ROOT)}')
    print(f'SHA-256: {sha}')
    print('Manifest: ota/latest.json (signature included)')


if __name__ == '__main__':
    main()
