# コードの入口

| 場所 | 役割 |
| --- | --- |
| `app/src/main/java/jp/flowview/android/MainActivity.java` | 画面、ナビゲーション、拡張管理、全画面・PiPへの接続 |
| `PlaybackService.java` | GeckoViewのセッション保持、バックグラウンド再生、通知・MediaSession |
| `PresentationState.java` / `PresentationProtocol.java` | 縦画面・全画面・PiPの状態とリビジョン管理 |
| `FullscreenSwipe.java` | ジェスチャー判定 |
| `MusicPanel.java` | キーとテンポの操作パネル |
| `AddonCatalog.java` | Mozilla Add-onsの検索結果の読み取り |
| `FlowUi.java` / `FlowIcon.java` | 共通UIとベクターアイコン |
| `app/src/main/assets/controller/` | YouTubeページに接続する内蔵WebExtension |
| `tests/` | JavaScript・DOM・音声処理の回帰テスト |
| `app/src/test/` | Androidの単体・Robolectricテスト |

Javaファイルの省略パスは `app/src/main/java/jp/flowview/android/` が基点です。

## 再生表示

Android側の表示状態を `PresentationState` で管理し、リビジョン付きの指示を内蔵拡張へ送ります。`content.js` は同じ動画ノードを維持しながらレイアウトを切り替えます。`gestures.js`、`background-play.js`、`viewport-playback.js`、`links.js` がタッチ・再生継続・リンク操作を担当します。

全画面中の下スワイプはAndroid側が判定し、リビジョン付きの `fullscreenDrag` で既存プレーヤーにCSS変形を適用します。移動中は表示状態を変更せず、上端36dpまたはシステムバー領域の大きい方を除外します。80dp以上引いた状態で指を離すと180msの収束後に縦画面へ戻ります。短い操作・引き戻し・複数指・キャンセル・フォーカス喪失・回転では切り替えません。動画ノードとGeckoViewの描画サーフェスは維持します。

分割時は向きをウィンドウに任せ、高さ540dp未満でアプリのヘッダーを省略します。モバイルYouTubeの通常動画ではWeb表示領域が420px未満になるとプレーヤー高を領域内に収めます。`viewport-playback.js` はリサイズ直前の再生状態を保持し、同じ動画ノード・URL・音源だけを1.2秒以内、最大2回復帰します。全画面・PiPの表示変更では、DOM全画面解除より先に同じ復帰処理を準備し、2.2秒以内に制限します。リサイズと表示変更の試行回数は共有し、Android側から別途動画を選び直して再生するタイマーは持ちません。タッチ・キー・クリック・ネイティブの再生/停止命令・ページ離脱は復帰をキャンセルします。音声フォーカス喪失による `pause` もこの経路に含みます。

## 音楽

`MusicPanel` → `PlaybackService` → `content.js` → `music-engine.js` の順で操作が届きます。Music Studioは既定で有効ですが、操作するまで動画の速度や音声経路には触れません。初回のキー変更は現在の再生速度を引き継ぎます。テンポは動画の再生速度を使い、移調のときに音声処理を接続します。パネルはGeckoViewと同じ画面内の下部に配置し、高さを表示領域の45%・最大360dpに制限します。外側のページ操作を遮らず、下部の音楽ボタンを再度押す・閉じる・戻る・全画面/PiP移行で閉じます。拡張メニューの「音楽ツールを開く」は表示中なら表示を維持します。`music-worklet-source.js` がAudioWorklet方式、`music-compat.js` が互換方式です。SoundTouchJS本体とライセンスは `third_party/soundtouch/` にあります。

## 更新互換性

表示名はSatiModです。アプリID、Javaパッケージ、設定の保存名、WebExtension ID、内部メッセージ名に残る `flowview` / `FlowView` は互換性を保つ内部識別子です。表示名の変更だけを目的とした一括置換は行わないでください。
