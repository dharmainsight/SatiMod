# SatiMod

**視聴の操作と音楽のキー・テンポを、自分に合わせるAndroidブラウザー。**

Pixel 9aでのYouTube視聴を起点に開発しています。GeckoViewを使い、動画の操作、バックグラウンド再生、Firefox for Android向け拡張機能、音楽の移調をまとめています。Google・YouTube・Mozillaの公式アプリではありません。

## できること

- **視聴ジェスチャー**：通常動画の映像上で上スワイプすると全画面、全画面中は下スワイプに映像が追従し、十分に引いて指を離すと縦画面へ。途中で戻すと全画面を維持し、画面上端は通知バー用に残します。左右ダブルタップで10秒移動。
- **画面分割**：小さいウィンドウでは上部ヘッダーを省略し、動画を表示領域に合わせます。サイズ変更に伴う停止は短時間だけ復帰を試み、手動の一時停止を優先します。
- **再生を続ける**：全画面からホームへ移動すると小窓（PiP）、縦画面から離れると音声のみのバックグラウンド再生。通知から再生を操作。
- **コメントを読む**：下方向のスワイプをページ更新に割り当てず、コメントのスクロールを維持。
- **ブラウザー操作**：戻る・進む・確認付き更新、リンク長押しコピー、URL入力、動画共有。
- **拡張機能**：Mozilla Add-onsからFirefox for Android対応拡張を検索・追加。署名済みXPIにも対応。ChromeのCRXを直接インストールする機能はありません。
- **Music Studio**：キーを±12半音、テンポを50〜200%で独立調整。音声処理は端末内で行います。

## 使い方

動画を再生し、下部の「音楽」から「この動画で使う」をONにします。キーの−/+、テンポのスライダーで調整できます。パネルを閉じても設定は継続します。

キー変更ではAudioWorkletを優先し、利用できない場合は同梱の互換処理を使います。一部の保護音源や別の音声拡張との競合では移調できません。音源・端末・再生状況による制限は[検証状況](docs/verification.md)を参照してください。

## 開発・配布状況

現在のソースは **0.3.3 / versionCode 8** です。SatiMod名義の署名済みAPKは、このリポジトリではまだ配布していません。自分で試す場合は[ビルド手順](docs/build.md)からデバッグAPKを作成できます。

FlowView 0.3.1を引き継いでいます。既存インストールとの更新互換性を保つため、アプリIDは `jp.flowview.android` のままです。上書き更新には同じ署名鍵が必要です。

## 開発を始める

```sh
git clone https://github.com/dharmainsight/SatiMod.git
cd SatiMod
npm ci
npm test
./gradlew :app:assembleDebug
```

AndroidのビルドにはJDK 17とAndroid SDKが必要です。詳細は[ビルドと署名](docs/build.md)、コードを読む入口は[構成](docs/architecture.md)、変更時の方針は[AGENTS.md](AGENTS.md)にあります。

ChatGPTから作業を続ける場合は、GitHub接続を有効にして `@GitHub dharmainsight/SatiMod` と対象機能を指定してください。

## データと外部通信

再生位置・設定・ブラウザーのセッションは端末に保存します。アプリ独自のアカウントや解析送信はありません。YouTubeへのアクセス・ログインと、拡張機能の検索・追加では各サービスと通信します。追加した拡張機能の通信やデータ利用は、その拡張機能の権限・仕様によります。

## ライセンス

GeckoViewとSoundTouchJSはMPL 2.0、Material ComponentsはApache 2.0です。[第三者ライセンス・通知](app/src/main/assets/licenses/NOTICE.txt)と[SoundTouchJSのソース](third_party/soundtouch/)を同梱しています。独自コードの再利用ライセンスは未設定です。
