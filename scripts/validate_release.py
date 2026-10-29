"""Validate the committed release inputs; no network or third-party packages."""
import hashlib
import re
import sys
from pathlib import Path
from xml.etree import ElementTree


def validate(root, tag):
    manifest = ElementTree.parse(root / 'android/AndroidManifest.xml').getroot()
    version = manifest.attrib['{http://schemas.android.com/apk/res/android}versionName']
    if not re.fullmatch(r'v\d+\.\d+(?:\.\d+)?', tag) or tag != 'v' + version:
        raise ValueError(f'Tag {tag!r} does not match Android version {version!r}')
    expected = {}
    for line in (root / 'SHA256SUMS.txt').read_text(encoding='utf-8-sig').splitlines():
        if not line.strip():
            continue
        digest, name = line.split(None, 1)
        name = name.strip().lstrip('*')
        if name not in ('PhoneWheel.apk', 'PhoneWheelReceiver.exe') or name in expected:
            raise ValueError('Unexpected or duplicate checksum entry')
        if not re.fullmatch(r'[a-fA-F0-9]{64}', digest):
            raise ValueError('Invalid SHA256')
        expected[name] = digest.lower()
    if set(expected) != {'PhoneWheel.apk', 'PhoneWheelReceiver.exe'}:
        raise ValueError('Both APK and EXE checksums are required')
    for name, digest in expected.items():
        path = root / 'dist' / name
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f'Missing release asset: {name}')
        if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError(f'Checksum mismatch: {name}')
    notes = (root / 'RELEASE_NOTES.md').read_text(encoding='utf-8-sig')
    if f'PhoneWheel {version}' not in notes:
        raise ValueError('Release notes must identify the current version')
    return version


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python scripts/validate_release.py vVERSION')
    try:
        version = validate(Path(__file__).resolve().parents[1], sys.argv[1])
    except (ValueError, OSError, KeyError) as error:
        raise SystemExit(str(error))
    print(f'PASS: PhoneWheel {version}, APK + EXE checksums and release notes')
