#!/usr/bin/env python3
"""Package a built release APK only after verifying in-place update compatibility."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "jp.flowview.android"
CERTIFICATE = "364e65b9dee8ce3a44405daddab21f5904410e3c158e8f42ba2abafa77a24e4a"


def run(args):
    result = subprocess.run([str(arg) for arg in args], cwd=ROOT, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f"{Path(str(args[0])).name} failed:\n{result.stdout[-5000:]}")
    return result.stdout


def identity(aapt, apk):
    text = run([aapt, "dump", "badging", apk])
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", text, re.M)
    if not match:
        raise ValueError("Cannot read APK identity")
    return {"package": match[1], "version_code": int(match[2]), "version": match[3]}, text


def certificates(signer, apk):
    output = run([*signer, "verify", "--verbose", "--print-certs", apk])
    found = re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F]{64})", output)
    if not found:
        raise ValueError("No verified APK signing certificate")
    return {value.lower() for value in found}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--previous-apk", type=Path, required=True)
    parser.add_argument("--keystore", type=Path, required=True)
    parser.add_argument("--key-alias", required=True)
    parser.add_argument("--password-env", default="SATIMOD_SIGN_PASS")
    parser.add_argument("--sdk-root", type=Path, default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    parser.add_argument("--output-dir", type=Path, default=ROOT / "dist")
    args = parser.parse_args()
    if not args.sdk_root or not os.environ.get(args.password_env):
        parser.error("Set ANDROID_HOME/--sdk-root and the password environment variable")
    previous, keystore = args.previous_apk.resolve(), args.keystore.resolve()
    if not previous.is_file() or not keystore.is_file():
        parser.error("Previous release APK and existing keystore must be files")
    build = (ROOT / "app/build.gradle").read_text()
    tools_version = re.search(r"buildToolsVersion\s+'([^']+)'", build)[1]
    version = re.search(r"versionName\s+'([^']+)'", build)[1]
    version_code = int(re.search(r"versionCode\s+(\d+)", build)[1])
    if json.loads((ROOT / "app/src/main/assets/controller/manifest.json").read_text())["version"] != version:
        raise ValueError("Built-in extension version must match the app")
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){2}(?:[a-zA-Z0-9.-]*)", version):
        raise ValueError("Version must be safe for an APK filename")
    run(["git", "diff", "--exit-code", "HEAD", "--", "app", "build.gradle", "settings.gradle", "gradle.properties", "gradle"])
    untracked = run(["git", "ls-files", "--others", "--exclude-standard", "app"]).strip()
    if untracked:
        raise ValueError("Commit app sources before packaging")
    source_commit = run(["git", "rev-parse", "HEAD"]).strip()
    sdk = args.sdk_root.resolve() / "build-tools" / tools_version
    java_home = os.environ.get("JAVA_HOME")
    java = Path(java_home) / "bin/java" if java_home else "java"
    signer = [java, "-jar", sdk / "lib/apksigner.jar"]
    aapt, zipalign = sdk / "aapt", sdk / "zipalign"
    raw = ROOT / "app/build/outputs/apk/release/app-release-unsigned.apk"
    if not raw.is_file():
        raise ValueError("Run :app:assembleRelease first")
    old, _ = identity(aapt, previous)
    current, manifest = identity(aapt, raw)
    if old["package"] != PACKAGE or current["package"] != PACKAGE:
        raise ValueError("Application ID differs from the installed app")
    if current["version_code"] <= old["version_code"]:
        raise ValueError("versionCode must be greater than the previous release")
    if current["version"] != version or current["version_code"] != version_code:
        raise ValueError("Built APK is stale: its version differs from source")
    if "application-debuggable" in manifest or "native-code: 'arm64-v8a'" not in manifest:
        raise ValueError("Expected a non-debuggable arm64 APK")
    if certificates(signer, previous) != {CERTIFICATE}:
        raise ValueError("Previous APK does not match the established release certificate")
    with zipfile.ZipFile(raw) as archive:
        files = [file for file in (ROOT / "app/src/main/assets/controller").iterdir() if file.is_file()]
        if {"assets/controller/" + file.name for file in files} != {name for name in archive.namelist() if name.startswith("assets/controller/") and not name.endswith("/")}:
            raise ValueError("Built controller file list differs from source")
        for file in files:
            if file.is_file() and archive.read("assets/controller/" + file.name) != file.read_bytes():
                raise ValueError("Built controller assets differ from source: " + file.name)
    out = args.output_dir.resolve()
    out.mkdir(parents=True, exist_ok=True)
    name = f"SatiMod-{version}-arm64.apk"
    if (out / name).exists():
        raise ValueError("Output APK already exists; use an empty output directory")
    with tempfile.TemporaryDirectory(prefix=".update-", dir=out) as folder:
        staging = Path(folder)
        aligned, signed = staging / "aligned.apk", staging / name
        run([zipalign, "-P", "16", "-f", "4", raw, aligned])
        run([*signer, "sign", "--ks", keystore, "--ks-key-alias", args.key_alias,
             "--ks-pass", "env:" + args.password_env, "--key-pass", "env:" + args.password_env,
             "--out", signed, aligned])
        if certificates(signer, signed) != {CERTIFICATE}:
            raise ValueError("New APK signing certificate differs from the previous release")
        run([zipalign, "-c", "-P", "16", "4", signed])
        with signed.open("rb") as apk_file:
            digest = hashlib.file_digest(apk_file, "sha256").hexdigest()
        record = {**current, "source_commit": source_commit, "previous_version": old["version"],
                  "previous_version_code": old["version_code"], "file_name": name,
                  "size_bytes": signed.stat().st_size, "sha256": digest,
                  "certificate_sha256": CERTIFICATE, "signature_verified": True,
                  "zipalign_16k_verified": True, "controller_assets_match": True,
                  "device_update_tested": False}
        signed.replace(out / name)
        (out / "SHA256SUMS.txt").write_text(f"{digest}  {name}\n")
        (out / "release.json").write_text(json.dumps(record, indent=2) + "\n")
    print(json.dumps(record, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, KeyError) as error:
        raise SystemExit(str(error))
