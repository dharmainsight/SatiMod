# 更新用APKの提供

リポジトリを更新したら、PRとともに、前の配布版へ上書きできる署名済みAPKのダウンロードリンクを提供します。ユーザーがAPK不要と明示した場合だけ省略します。ファイルは差分パッチではなく、Androidがそのままインストールできる完全なAPKです。

## 更新を成立させる条件

- アプリIDは `jp.flowview.android`、署名鍵は既存版と同じものを使います。
- `versionCode` は直前の配布APKより大きくします。アプリ・内蔵拡張・npmのバージョンと診断表示も揃えます。
- 署名鍵は所有者の非公開保管先から取得します。以前の開発で保存したバックアップ名は `FlowView-signing-backup-private.zip` です。鍵やパスワードをチャット本文・リポジトリ・配布ファイルに含めません。
- 既存データを保つため、デバッグ署名への変更やアンインストールを案内して更新を代用しません。

## 作成手順

1. 配布対象のコミットと直前の配布APKを確定し、アプリに関係する変更をコミットします。
2. [ビルド環境](build.md#必要な環境)を用意し、必要な試験とリリースビルドを実行します。対象ソースに対して既に成功している試験は再実行不要です。

   ```sh
   npm ci
   npm test
   ./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
   ```

3. キーストアのパスワードを環境変数 `SATIMOD_SIGN_PASS` に安全に設定し、署名・更新互換性の検証を行います。パスワードをコマンド引数や履歴に直接書かないでください。

   ```sh
   python3 scripts/package-update.py \
     --previous-apk /secure/previous-release.apk \
     --keystore /secure/flowview.p12 \
     --key-alias YOUR_EXISTING_ALIAS
   ```

   `ANDROID_HOME` または `--sdk-root` でSDKを指定します。出力先は `dist/` です。スクリプトは前版・新版の署名検証、証明書一致、アプリID、versionCodeの増加、arm64、非デバッグ、内蔵拡張のソース一致、16KiB整列を確認してからAPKを出力します。

4. `SatiMod-<version>-arm64.apk`、`SHA256SUMS.txt`、`release.json` を保存します。公開配布はGitHub Releasesの対象コミットに添付します。チャット内で渡す場合は永続保存し、そのAPKへの直接のダウンロード導線を回答に含めます。APKをGitの通常ファイルとしてコミットしません。
5. PRと最終回答にはバージョン、更新元、APKリンク、主要変更、確認できた範囲を示します。実機未確認なら明記し、署名・ビルド成功を実機動作確認と混同しません。

## インストール

AndroidでAPKをダウンロードして開き、「更新」を選びます。必要な場合はダウンロードに使ったアプリに「不明なアプリのインストール」を許可します。既存のFlowView/SatiModを削除する必要はありません。

ADBを使う場合は `adb install -r SatiMod-<version>-arm64.apk` で更新します。端末で起動し、設定・ログイン状態・主要操作を確認します。

## 作成できない場合

鍵不足、署名不一致、ビルド失敗、アップロード失敗のどこで止まったかを記録し、ソースと作業状況をPRに残します。更新APKがない状態を完了として報告しません。バイナリの長期保存にGitHub Actionsの一時artifactだけを使わず、再開時にも配布APKを特定できるようにします。
