# ビルドと署名

## 必要な環境

このリポジトリに固定している構成です。

| 項目 | バージョン |
| --- | --- |
| JDK | 17 |
| Gradle Wrapper | 9.8.2 |
| Android Gradle Plugin | 9.4.1 |
| Android compile SDK | 37.2 |
| Android Build Tools | 37.0.0 |
| Android min / target SDK | 31 / 36 |
| 対象ABI | arm64-v8a |
| GeckoView | 158.0.20261021001945 |
| Node.js | 22以上 |

Android StudioのSDK Manager等で上記SDK・Build Toolsを準備し、SDKのライセンスを確認・承諾します。`ANDROID_HOME`を設定するか、未追跡の`local.properties`に `sdk.dir=/absolute/path/to/Android/Sdk` を設定してください。依存ライブラリの取得にはインターネット接続が必要です。

## 検証・デバッグAPK

```sh
npm ci
npm test
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
```

Windowsでは `./gradlew` の代わりに `gradlew.bat` を使います。デバッグAPKは `app/build/outputs/apk/debug/app-debug.apk` に生成されます。

端末でAPKを開き、必要に応じて開いたアプリに「不明なアプリのインストール」を許可します。ADBを使う場合は次のコマンドでインストールできます。

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

デバッグ署名は配布版の署名と異なります。配布版へ上書きできない場合は、データを消すアンインストールで回避せず、別のテスト端末・エミュレーターを利用するか、所有者の同じ鍵でリリースAPKを署名してください。

## リリースAPK

```sh
./gradlew :app:assembleRelease
```

出力は `app/build/outputs/apk/release/app-release-unsigned.apk` です。署名前にBuild Toolsの `zipalign -P 16` で整列し、`apksigner` で署名してから署名と整列を確認します。署名済みAPKへの変更は署名を無効にするため、署名後にZIP編集を行わないでください。

```sh
zipalign -P 16 -f -v 4 app/build/outputs/apk/release/app-release-unsigned.apk SatiMod-aligned.apk
apksigner sign --ks /secure/path/release.p12 --out SatiMod-0.3.2-arm64.apk SatiMod-aligned.apk
apksigner verify --verbose --print-certs SatiMod-0.3.2-arm64.apk
zipalign -c -P 16 -v 4 SatiMod-0.3.2-arm64.apk
```

上記ツールはAndroid SDKの `build-tools/37.0.0/` にあります。秘密鍵・パスワード・端末データはリポジトリに保存しません。既存の配布版を更新する鍵は所有者が別途管理します。

## 任意のブラウザー試験

`tests/browser.cjs` はPlaywright用の模擬動画・コメント画面テストです。通常の `npm test` には含めていません。Playwrightと対応Chromiumを用意した環境で実行してください。実YouTube・GeckoView・Androidの動作を保証するテストではありません。
