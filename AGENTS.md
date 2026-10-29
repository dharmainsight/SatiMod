# SatiModの作業ガイド

- 最初に `README.md`、`docs/architecture.md`、`docs/verification.md` と変更対象のソースを読む。
- 公開名はSatiMod。既存インストールの更新互換性のため `jp.flowview.android`、保存設定名、拡張ID、内部メッセージ名は維持する。
- 視聴表示は `PresentationState` / `PresentationProtocol` を起点にする。回転・PiP・Web側イベントで互いに表示を切り替え続ける経路を作らない。
- 動画ノードやブラウザーのセッションを不用意に作り直さず、再生位置とユーザーによる一時停止を尊重する。
- 通常の修正は作業ブランチ・最小限の検証・PRまで進める。マージとAPK公開は依頼範囲に従う。
- JavaScriptは `npm ci && npm test`、Androidは `./gradlew :app:testDebugUnitTest :app:lintRelease`。変更に関係する検証だけを選び、合格済み試験を理由なく繰り返さない。
- 合成音・模擬DOMの成功とPixel実機の再生確認を区別する。未実施のビルド・試験を完了扱いにしない。
- 署名鍵、パスワード、端末のログイン情報、個人用の保存先ID、APK、ビルド生成物をコミットしない。APKを公開する場合はGitHub Releasesを使う。
- 第三者のソースとライセンスを維持する。アプリの利用条件を独断で新設・変更しない。
