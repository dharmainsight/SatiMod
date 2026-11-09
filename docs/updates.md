# SatiModの更新配布

## 方針

2026-11-09、利用者の希望で新しい署名鍵へ切り替える。初回だけ旧版を削除する（端末内のログイン・設定は消える）。以後は同じアプリID `jp.flowview.android` と同じ鍵を維持し、APKを開くだけで上書き更新できるようにする。

新しい鍵の公開証明書SHA-256は `release-cert.sha256` に固定する。秘密鍵はリポジトリ・ログ・Releasesへ入れない。鍵を作り直して更新失敗を回避しない。

## 初回の設定（スマホ）

1. 個別に渡した `SatiMod-signing-secret-KEEP-PRIVATE.txt` を開き、全文をコピーする。このファイルには署名鍵とパスワードが含まれるため、公開・共有しない。安全な場所にバックアップする。
2. GitHubでこのリポジトリの Settings → Secrets and variables → Actions → New repository secret を開く。
3. Nameへ `SATIMOD_SIGNING_JSON`、Secretへコピーした全文を貼り付け、Add secretを押す。登録は1件だけ。
4. Actions → Release APK → Run workflow → main → Run workflow を押す。
5. 成功するとReleasesに `SatiMod-arm64.apk` が公開される。失敗したら旧アプリは削除せず、その実行ログを確認する。

## 以後の更新

mainへアプリ・ビルド関連の変更をマージすると自動実行する。説明文だけの変更では実行しない。手動実行もできる。`versionCode = 1000 + github.run_number`、表示版 `0.4.<run_number>` とする。ワークフローを作り直して実行番号をリセットしない。再実行は同じバージョンなので、公開済みAPKを上書きしない。新しい更新は新規実行にする。

JavaScript試験、Android単体試験・Lint・ビルドが通った後、Secretsの鍵で署名する。証明書、アプリID、バージョン、ABI、非デバッグ、16KiB ZIP整列を検証してからGitHub Releasesへ公開する。Actions artifactと依存キャッシュへのアップロードは行わない。

公開後の固定ダウンロード先：
`https://github.com/dharmainsight/SatiMod/releases/latest/download/SatiMod-arm64.apk`

このURLは最初の公開が成功するまでは使えない。利用者へ渡す前に実在するリリースとAPKを確認する。

## 作業を引き継ぐ場合

1. main、最新Release、Actionsの最新結果を読む。
2. 失敗した場合はジョブのログから原因を確認し、必要な変更だけ行う。
3. APK公開と実機動作を区別する。起動・音声・イコライザ・PiPはPixelで別途確認する。
4. 完了報告には実在するAPKリンク・版・上書き可否・未検証項目を記載する。マージだけで更新完了としない。

## 現在の到達点

新しい鍵を作成・バックアップ済み。PR #8・#9をマージし、自動ビルド・署名・検証・公開の設定をmainへ反映。2026-11-09のActions run 42615943190（ソース891b2c5c084faa58128b2679e069f894f9cdbd22）でJavaScript 60件、Android単体試験・Lint・リリースAPKビルドに成功。署名ステップは `SATIMOD_SIGNING_JSON` 未登録で停止した。署名・公開・Pixel動作は未確認。GitHub Secrets登録後、この失敗ジョブを再実行して署名・公開を確認する。設定は1件のみで、鍵は再生成しない。従来の0.3.7復旧版とのソース一致は未確認であり、新規インストールでも起動確認が必要。
