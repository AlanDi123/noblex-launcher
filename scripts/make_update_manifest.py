#!/usr/bin/env python3
"""Create update-manifest.json from a verified release APK (never from guesses)."""

import argparse
import hashlib
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

APPLICATION_ID = "io.github.alandi123.noblexlauncher"


def badging(apk: Path, aapt2: str) -> str:
    return subprocess.run([aapt2, "dump", "badging", str(apk)], check=True, capture_output=True, text=True).stdout


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--aapt2", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--notes", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("update-manifest.json"))
    args = parser.parse_args()

    info = badging(args.apk, args.aapt2)
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", info, re.M)
    if not package:
        raise SystemExit("Could not read package information from the APK.")
    name, version_code, version_name = package.group(1), int(package.group(2)), package.group(3)
    if name != APPLICATION_ID:
        raise SystemExit(f"Unexpected package {name}.")
    if args.tag != f"v{version_name}":
        raise SystemExit(f"Tag {args.tag} does not match APK versionName {version_name}.")
    min_sdk = int(re.search(r"^(?:min)?[sS]dkVersion:'(\d+)'", info, re.M).group(1))
    native = re.search(r"^native-code: (.*)$", info, re.M)
    abis = re.findall(r"'([^']+)'", native.group(1)) if native else ["universal"]

    data = args.apk.read_bytes()
    manifest = {
        "applicationId": name,
        "version": version_name,
        "versionCode": version_code,
        "minSdk": min_sdk,
        "supportedAbis": abis,
        "apkUrl": f"https://github.com/{args.repository}/releases/download/{args.tag}/{args.apk.name}",
        "sha256": hashlib.sha256(data).hexdigest(),
        "sizeBytes": len(data),
        "releaseDate": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "releaseNotes": args.notes.read_text(encoding="utf-8")[:4000],
        "mandatory": False,
    }
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {args.output} for {name} {version_name} ({version_code}).")


if __name__ == "__main__":
    main()
