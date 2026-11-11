# 検証状況

## 利用時間の分析（PR段階・2026-11-11）

- 右上の虫眼鏡を分析アイコンへ変更し、日平均・7/30/365日合計を表示。通常・全画面・分割・PiPの画面表示時間を端末内に記録する。導入前の履歴は取得しない。
- `UsageHistoryCheck`：JDK 21のコンパイラをsource/target 17で実行し、13件の検証に成功。二重開始/停止、途中保存、非表示の空白、午前0時の分割、平均の分母、期間境界・保持期限、時刻変更、再起動、夏時間を確認。
- `./gradlew :app:testDebugUnitTest :app:lintRelease`：Gradle配布物取得が `Network is unreachable` で停止。Android型検査・JUnit・Lint・APKビルドは未実施。
- 次の確認：マージ時のRelease APKワークフローでAndroid試験とLintを確認。Pixelで通常→全画面→PiP→復帰、分割、画面消灯/ロック解除、音声のみのバックグラウンド、プロセス終了・上書き更新後の保存値を確認する。実機確認は未実施。

単体集計の再検証（JDK 17以上）：
```sh
mkdir -p /tmp/satimod-usage-check
javac -d /tmp/satimod-usage-check app/src/main/java/jp/flowview/android/UsageHistory.java tests/java/UsageHistoryCheck.java
java -cp /tmp/satimod-usage-check jp.flowview.android.UsageHistoryCheck
```

## 音楽操作列の上部固定（PR段階・2026-11-11）

- Music Studioの見出し直下に、−10秒／再生・一時停止／+10秒／全画面／小窓の5ボタンを固定。キー・テンポ・イコライザ・リセットは従来のスクロール領域に残す。再生命令・全画面・PiP処理の接続は変更しない。
- Robolectricの回帰試験を追加。操作列の位置、設定スクロール領域との分離、10秒送り戻し・再生、一時停止表示、全画面・小窓の操作を確認する内容（この時点では未実行）。
- この作業環境ではGitHubへの接続は可能だがGradle/Android SDKの実行環境を確保できず、Android試験・Lint・APKビルドは未実施。mainへのマージで起動するRelease APKワークフローの結果を確認すること。
- Pixel 9aではパネルを開き、ボタンが見出し直下に留まること、設定をスクロールしても再生操作できること、全画面・小窓移行と再生継続を確認する。実機確認は未実施。

## EQの増減方向修正（PR段階）

- 報告：プラスとマイナスで挙動が逆。コードでは符号の反転はなく、正の帯域ゲインを合計して全体から減衰する処理を確認。上げるほど他帯域と全体音量が下がるため、この処理を削除し、EQ後段のピーク圧縮に置換。実機での原因確定と聴感確認は未実施。
- `npm ci --ignore-scripts --no-audit --no-fund`、`npm test`：60件成功。音声経路・ピーク圧縮の設定・フラット復帰・ソース再利用を模擬AudioContextで確認。
- `scripts/verify-equalizer-audio.cjs`：実エンジンとOfflineAudioContextで5帯域それぞれの−12/−6/0/+6/+12 dB、全帯域ブースト、無効化、定常音のピークを検証するスクリプトを追加。ローカルはChromiumのsocket作成が環境側で拒否され、実行未完了。[Audio regression tests（run 42837318555）](https://github.com/dharmainsight/SatiMod/actions/runs/42837318555)で実行し成功。対象ソースは`30d827feaa9d4f4158e5cca9b201299c9015e07b`。5帯域の増減方向と指定dB（許容誤差1 dB）、全帯域ブースト、無効化、定常音のピーク抑制を確認。Playwright 1.62.1と対応Chromiumが必要。単体試験と合成音の結果をPixel実機の結果と区別する。
- Androidコード変更なし。APKビルド・公開・Pixel 9aの確認は未実施。次の確認：同じ音源・端末音量で各帯域を0→+6→0→−6と動かし、キー/テンポ併用、フラット復帰、音割れを確認。

## SatiMod 0.4.4 — 2026-11-10（日本時間）

[公開APK](https://github.com/dharmainsight/SatiMod/releases/download/v0.4.4/SatiMod-arm64.apk)、versionCode 1004。ソース `c4fd1c2bc2e444cfb56dc864425a0495a53b0c70`、[Actions run 42718669925](https://github.com/dharmainsight/SatiMod/actions/runs/42718669925) 成功。

- JavaScript 60件、Android単体試験・Lint・リリースAPKビルドに成功。
- APK署名、固定証明書のSHA-256、アプリID、versionCode/name、arm64、非デバッグ、16KiB ZIP整列を確認して公開。
- 署名時のパスワードファイル共有によるEOF、Build Tools 37の `V3.0 Signer:` 表示による証明書抽出失敗を修正。署名鍵は変更していない。
- Pixel実機での起動・再生・イコライザ・PiPは未実施。初回だけ旧署名版の削除が必要で、端末内データが消える。次版以降は同じ署名で上書き更新する。

署名とAPKのハッシュはReleaseの `release.json`・`SHA256SUMS.txt`、運用は[更新手順](updates.md)に記録。


## 0.4系の自動配布 — 2026-11-09

[Actions run 42615943190](https://github.com/dharmainsight/SatiMod/actions/runs/42615943190)、対象ソース `891b2c5c084faa58128b2679e069f894f9cdbd22`。

- JavaScript：60件成功。
- Android：`:app:testDebugUnitTest :app:lintRelease :app:assembleRelease` が `BUILD SUCCESSFUL`。イコライザのパネル試験を含む。
- 署名・公開：`SATIMOD_SIGNING_JSON` 未登録のため停止。未署名APKのビルド成功を、更新APKの配布完了として扱わない。
- 次の作業：利用者が秘密情報を1件登録→上記失敗ジョブを再実行→署名とAPK公開の成功を確認→実在するダウンロードリンクを案内。
- Pixel実機：未実施。新しい署名版の初回のみ旧版削除が必要。APKが公開される前に削除しない。

詳細は[更新手順](updates.md)。

## イコライザ追加 — 2026-11-09（PR段階）

5帯域の音質調整、フラット復帰、キー・テンポとの併用を追加。バージョンは変更していません。

- `npm ci --ignore-scripts --no-audit --no-fund` と `npm test`：60件成功。模擬AudioContextで帯域・ゲイン・減衰、ソース再利用、リセットの独立性、不正入力、非同期キャンセル、音声拡張競合を確認。実音の周波数応答・聴感試験ではありません。
- Androidパネル試験にEQの状態表示と、状態反映による命令ループ防止を追加。`./gradlew :app:testDebugUnitTest :app:lintRelease` はGradle取得時の `Network is unreachable` により開始できず、未検証。APK未作成。
- 次の確認：Android試験・Lint・APKビルド。Pixel 9aで低音/高音、フラット復帰、キー/テンポ併用、パネル開閉、動画切替、PiP、Bluetooth、ページ更新を確認。実機確認は未実施。

## SatiMod 0.3.4 — PiP再生・音楽パネル（2026-11-01）

versionCodeは9です。小窓（PiP）への移行前に動画側で再生状態を保持し、全画面解除・リサイズによる停止を同じ動画に限って短時間復帰します。手動停止・音声フォーカス喪失・URL/音源/動画ノードの変更で復帰を取り消します。音楽ツールは既定で有効とし、トグルを廃止しました。下部パネルを開いたままページをスクロールできます。

| 対象 | 確認結果 |
| --- | --- |
| JavaScript | `npm test` 53件成功。追加3件を含む変更対象の再試験（PiP関連28件・音楽13件）も成功。合計56件の対象を確認 |
| Android JUnit・Lint・リリースAPK | 全体の25件と、修正後の音楽パネル1件に成功。ページスワイプ・縮小・閉じるを確認。リリースビルド成功、Lintはエラー0・警告16件。対象ソースは `bc9e829038e74b0aecb85d8b13a686a6bffae57a` |
| 更新互換性 | 前版0.3.3と署名証明書・アプリIDが一致。versionCode 9、arm64、非デバッグ、内蔵拡張のソース一致、16KiB整列を確認。[APK検証記録](releases/0.3.4.json)を参照 |
| Pixel 9a実機・エミュレーター | 未実施。以下の操作確認が必要 |

実機では通常動画を再生し、全画面→ホーム→小窓を試します。映像・音声・再生位置が継続し、小窓から全画面へ戻れることを確認します。手動停止した動画・通知で停止した動画は、切り替え後も停止を維持する必要があります。

「音楽」を開き、スイッチ操作なしでキー・テンポを変更します。そのまま上側のコメント欄をスクロールし、パネルが閉じないことを確認します。閉じる・再度開く・リセット、分割画面での縮小も確認します。更新インストール後はログインと保存設定を確認してください。

停止が残る場合は「メニュー → 再生の診断」の `presentation recovery` / `resize recovery` と操作を記録します。模擬DOMやAndroid単体試験の成功を、実YouTube・Pixel実機での再生確認と同一視しません。

実装の参照：[Android PiPの再生継続](https://developer.android.com/develop/ui/views/picture-in-picture#continuing_video_playback_while_in_pip)、[GeckoSessionの可視状態](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.html#setActive(boolean))。

## SatiMod 0.3.3 — 分割表示・下スワイプ修正（2026-10-29）

versionCodeは8です。全画面解除を指のリリース時に確定し、映像の追従・引き戻し・上端の通知操作除外を実装しました。分割時はプレーヤーを小さい表示領域に収め、サイズ変更直前に再生中だった動画だけを短時間・最大2回復帰します。

| 対象 | 確認結果 |
| --- | --- |
| JavaScript | `npm ci --offline --ignore-scripts --no-audit --no-fund` 成功。初回 `npm test` は43件成功。追加4件を含む対象再試験22件も成功 |
| Javaのスワイプ判定 | 実際の `FullscreenSwipe.java` をJDK 17でコンパイルし、標準assertによる単独ハーネスでリリース・長押し・引き戻し・上端・横/上方向・中断を確認 |
| Java構文 | 本番10ファイルをJDK 17のパーサーで確認。Android型検査の代替にはならない |
| Android JUnit・Lint・APK | Gradle配布物の取得が `Network is unreachable` で失敗。SDKも未配置のため未実施 |
| ブラウザー表示 | PlaywrightのChromium実行ファイルがなく起動不可。DOM試験と実描画を区別 |
| Pixel実機 | 未実施。元の停止原因の端末ログ確認と、修正後の再現操作が必要 |

### マージ前の実機確認

Pixel 9aでAPKを起動し、YouTubeの通常動画を再生します。Androidの履歴画面から画面分割にして、相手側にChatGPTを開きます。

- 分割比を2/3→1/2→1/3→1/2と変え、動画・音声・再生位置が継続すること。手動で停止してから同じ操作を行い、停止を維持すること。
- 全画面の映像中央から下へ引き、80dpを超えても指を置いたままでは戻らないこと。指を戻すと全画面に復帰し、十分に引いて離した場合だけ縦画面になること。
- 画面上端から通知バーを開閉し、全画面が維持されること。スワイプ中の2本指・回転・通知の割り込みでも遅れて画面が切り替わらないこと。
- 全画面で左右ダブルタップの10秒移動、ホームからPiP、縦画面でのバックグラウンド再生、通知からの一時停止を確認すること。

再生停止が残る場合は「メニュー → 再生の診断」の `multi-window` / `resize recovery` と、分割比・動画URL・停止直前の操作を記録します。

実装の参照：[Androidのマルチウィンドウ](https://developer.android.com/develop/adaptive-apps/guides/support-multi-window-mode)、[GeckoViewの描画サーフェス](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoView.html)。

## SatiMod 0.3.2 — 2026-10-29

0.3.1のソースを基に、画面・通知・拡張機能の表示名と開発プロジェクト名をSatiModへ変更しました。versionCodeは7です。音声処理・視聴表示のロジック、アプリID、設定保存名、内部プロトコルは維持しています。

| 対象 | 今回の確認 |
| --- | --- |
| JavaScript回帰試験 | `npm test`：37件成功、失敗・スキップ0件 |
| DOM統合試験 | 上記37件のうち1件に10項目の確認を含む |
| 改名と更新互換性 | Manifest・文字列リソース・拡張メタデータ・バージョン・アプリIDの整合を確認 |
| 公開対象 | 秘密鍵・個人用保存先ID・ビルド生成物を除外。第三者ライセンスを維持 |
| Androidビルド・JUnit・Lint | 今回は未実施 |
| 改名版APKの署名・配布 | 未実施 |
| Pixel実機・Androidエミュレーター | 今回は未実施 |

JavaScript試験は全画面/PiPの状態遷移、スクショ等で中断したジェスチャー、リンク操作、音声処理の起動失敗やキャンセル、合成音の移調・テンポの独立性を対象としています。実際のYouTube音源を再生した結果ではありません。

## 引き継いだ検証記録

[0.3.1のビルド記録](verification/baseline-0.3.1.json)は、前回作成したAPKの結果です。Androidテスト28件、JavaScriptテスト37件が成功し、Lintはエラー0・警告16、APKの署名・16KiB整列を確認しています。この記録を0.3.2のビルド確認として扱わないでください。

## 実機で確認すること

- 通常動画・Shortsのコメントを往復スクロールして、ページ更新や再生位置のリセットが起きないこと。
- 全画面と縦画面の往復、切替中のスクショ・回転で、黒画面や連続切替が起きないこと。
- 全画面から小窓へ移り再生を継続し、復帰時に全画面へ戻ること。縦画面からは音声のみで継続すること。
- キー・テンポ変更、原曲へリセット、シーク、動画の切替、Bluetooth、画面消灯、長時間再生で音質・遅延・操作を確認すること。

互換音声処理はメインスレッドで動くため、高負荷では音切れが生じる可能性があります。保護音源や別の音声拡張との競合では移調できない場合があります。実機の結果を追記するときは、端末・OS・対象ソースのコミット・操作・結果を併記してください。
