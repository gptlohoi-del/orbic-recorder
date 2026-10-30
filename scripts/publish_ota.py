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
NATIVE_API = 1


def main():
    cli = argparse.ArgumentParser()
    cli.add_argument('--version', required=True, help='Semantic version, e.g. 1.0.1')
    cli.add_argument('--key', help='Private PEM file, for offline validation only')
    args = cli.parse_args()
    version = args.version
    if not re.fullmatch(r'[0-9]{1,5}\.[0-9]{1,5}\.[0-9]{1,5}', version):
        raise SystemExit('OTA version must be N.N.N, e.g. 1.0.1')
    previous = OUT / 'latest.json'
    if previous.is_file():
        older = json.loads(previous.read_text(encoding='utf-8'))['version']
        if tuple(map(int, version.split('.'))) <= tuple(map(int, older.split('.'))):
            raise SystemExit(f'New OTA version must exceed {older}')

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
        java_source = (ROOT / 'app/src/main/java/com/example/orbicrecorder/OtaManager.java').read_text(encoding='utf-8')
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
