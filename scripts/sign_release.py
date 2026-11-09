"""Sign with the one persistent key; verify identity, version and alignment before publication."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


def run(args, **kwargs):
    return subprocess.check_output([str(x) for x in args], text=True, **kwargs)


def main():
    secret_text = os.environ.pop('SATIMOD_SIGNING_JSON', '')
    if not secret_text:
        raise SystemExit('Register the SATIMOD_SIGNING_JSON repository secret; see docs/updates.md. No APK was published.')
    secret = json.loads(secret_text)
    code = int(os.environ['RELEASE_CODE'])
    name = os.environ['RELEASE_NAME']
    assert 1000 < code < 2100000000 and re.fullmatch(r'0\.4\.\d+', name)
    sdk = Path(os.environ['ANDROID_HOME'])
    build_tools = sdk / 'build-tools/37.0.0'
    expected_cert = Path('release-cert.sha256').read_text().strip().lower()
    unsigned = Path('app/build/outputs/apk/release/app-release-unsigned.apk')
    metadata = json.loads((unsigned.parent / 'output-metadata.json').read_text())
    assert metadata['applicationId'] == 'jp.flowview.android'
    assert len(metadata['elements']) == 1
    element = metadata['elements'][0]
    assert element['versionCode'] == code and element['versionName'] == name
    dist = Path('dist'); dist.mkdir(exist_ok=True)
    apk = dist / 'SatiMod-arm64.apk'
    with tempfile.TemporaryDirectory(prefix='satimod-sign-', dir=os.environ.get('RUNNER_TEMP')) as temp:
        temp = Path(temp); key = temp / 'release.p12'
        key.write_bytes(base64.b64decode(secret['keystore_base64'], validate=True)); key.chmod(0o600)
        password_file = temp / 'password'
        password_file.write_text(secret['password'] + '\n'); password_file.chmod(0o600)
        # apksigner consumes successive lines when the same password file is reused.
        key_password_file = temp / 'key-password'
        key_password_file.write_text(secret['password'] + '\n'); key_password_file.chmod(0o600)
        certificate = subprocess.check_output(['keytool', '-exportcert', '-keystore', str(key), '-alias', secret['alias'], '-storepass:file', str(password_file)])
        assert hashlib.sha256(certificate).hexdigest() == expected_cert, 'Signing key mismatch: do not rotate the release key.'
        aligned = temp / 'aligned.apk'
        run([build_tools/'zipalign', '-P', '16', '-f', '4', unsigned, aligned])
        run([build_tools/'apksigner', 'sign', '--ks', key, '--ks-key-alias', secret['alias'], '--ks-pass', f'file:{password_file}', '--key-pass', f'file:{key_password_file}', '--out', apk, aligned])
        verification = run([build_tools/'apksigner', 'verify', '--verbose', '--print-certs', apk])
        print(verification)  # Public certificates only; keep tool diagnostics in the build log.
        digests = re.findall(r'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\b', verification)
        assert digests and all(d.lower() == expected_cert for d in digests), 'APK certificate mismatch or missing certificate digest'
        run([build_tools/'zipalign', '-c', '-P', '16', '4', apk])
    badging = run([build_tools/'aapt2', 'dump', 'badging', apk])
    assert "package: name='jp.flowview.android'" in badging
    assert f"versionCode='{code}'" in badging and f"versionName='{name}'" in badging
    assert 'application-debuggable' not in badging
    assert "native-code: 'arm64-v8a'" in badging
    checksum = hashlib.sha256(apk.read_bytes()).hexdigest()
    (dist/'SHA256SUMS.txt').write_text(f'{checksum}  SatiMod-arm64.apk\n')
    record = dict(versionName=name, versionCode=code, applicationId='jp.flowview.android', certificateSha256=expected_cert, apkSha256=checksum, sourceCommit=os.environ['GITHUB_SHA'], runId=os.environ['GITHUB_RUN_ID'], pixelTested=False)
    (dist/'release.json').write_text(json.dumps(record, indent=2)+'\n')
    (dist/'release-notes.md').write_text(f'''音楽パネルに5帯域イコライザを搭載。\n\n初めて0.4系を入れる場合のみ、旧署名のSatiModを削除してからインストールしてください。端末内のログイン・設定は消えます。以後の0.4系はAPKを開いて上書き更新できます。\n\nJavaScript試験・Android単体試験・Lint・署名・16KiB ZIP整列を確認。Pixel実機確認は未実施です。\n\nversionCode: {code}\nソース: {os.environ['GITHUB_SHA']}\n''')
    print(f'Verified SatiMod {name}, versionCode {code}; certificate {expected_cert}')


if __name__ == '__main__':
    main()
