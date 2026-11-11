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

2026-11-10に初回公開成功。利用者へ渡す前に最新版のリリースとAPKの実在を確認する。

## ChatGPTから更新APKを渡す手順

GitHub ReleasesのAPK直リンクは、利用者のAndroid環境でダウンロード完了直前に停止する事象が繰り返し報告されている（2026-11-11）。今後は直リンクだけを更新案内として渡さず、次の手順で別経路のファイルを用意する。

1. 対象変更を含むReleaseとActions成功を確認する。GitHub Releasesは署名済みAPKの正本として維持する。
2. 公開済みAPKを取得し、同じReleaseのSHA256SUMS.txtまたはrelease.jsonのSHA-256と一致することを確認する。再ビルド・再署名はしない。
3. APKを `SatiMod-<version>-arm64.apk` のように版付きの名前にし、ChatGPTにファイルとして保存・添付する。利用者への主リンクは、その保存済みファイルのダウンロードリンクにする。
4. 0.4系からはアプリを削除せず、APKを開いて上書き更新するよう案内する。版・含まれる変更・未実施の実機確認も簡潔に記載する。
5. 別経路の保存が失敗した場合は、配布完了とせず理由を伝える。GitHub直リンクへの差し戻しだけで解決扱いにしない。

個人用ファイルID、期限付きURL、署名秘密情報はこのリポジトリへ記録しない。別セッションでも毎回この手順を適用する。

## 作業を引き継ぐ場合

1. main、最新Release、Actionsの最新結果を読む。
2. 失敗した場合はジョブのログから原因を確認し、必要な変更だけ行う。
3. APK公開と実機動作を区別する。起動・音声・イコライザ・PiPはPixelで別途確認する。
4. 上記の別経路でAPKを保存・添付する。完了報告にはその実在するAPKリンク・版・上書き可否・未検証項目を記載する。マージだけで更新完了としない。

## 現在の到達点

2026-11-10（日本時間）、[v0.4.4](https://github.com/dharmainsight/SatiMod/releases/tag/v0.4.4)を公開済み。versionCode 1004、ソース `c4fd1c2bc2e444cfb56dc864425a0495a53b0c70`、[Actions run 42718669925](https://github.com/dharmainsight/SatiMod/actions/runs/42718669925)。JavaScript 60件、Android単体試験・Lint・ビルド、固定証明書との一致、APK署名・アプリID・版・ABI・非デバッグ・16KiB ZIP整列、Releases公開まで成功。署名鍵の初回登録は完了しており、再登録・再生成は不要。

APK SHA-256: `249a0428c1bec874498508b2f032a9048f1fa0497998995503b782e1b9cb79b1`。

利用者にはAPKをダウンロード後、初回のみ旧署名版を削除してインストールするよう案内する。以後は同じ鍵の更新APKを開いて上書きできる。Pixel実機での起動・イコライザ・PiPは未確認。従来0.3.7復旧版とのソース一致も未確認。
