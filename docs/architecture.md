# コードの入口

| 場所 | 役割 |
| --- | --- |
| `app/src/main/java/jp/flowview/android/MainActivity.java` | 画面、ナビゲーション、拡張管理、全画面・PiPへの接続 |
| `PlaybackService.java` | GeckoViewのセッション保持、バックグラウンド再生、通知・MediaSession |
| `PresentationState.java` / `PresentationProtocol.java` | 縦画面・全画面・PiPの状態とリビジョン管理 |
| `FullscreenSwipe.java` / `PlaybackRecovery.java` | ジェスチャー判定、再生復帰 |
| `MusicPanel.java` | キーとテンポの操作パネル |
| `AddonCatalog.java` | Mozilla Add-onsの検索結果の読み取り |
| `FlowUi.java` / `FlowIcon.java` | 共通UIとベクターアイコン |
| `app/src/main/assets/controller/` | YouTubeページに接続する内蔵WebExtension |
| `tests/` | JavaScript・DOM・音声処理の回帰テスト |
| `app/src/test/` | Androidの単体・Robolectricテスト |

Javaファイルの省略パスは `app/src/main/java/jp/flowview/android/` が基点です。

## 再生表示

Android側の表示状態を `PresentationState` で管理し、リビジョン付きの指示を内蔵拡張へ送ります。`content.js` は同じ動画ノードを維持しながらレイアウトを切り替えます。`gestures.js`、`background-play.js`、`links.js` がタッチ・再生継続・リンク操作を担当します。

## 音楽

`MusicPanel` → `PlaybackService` → `content.js` → `music-engine.js` の順で操作が届きます。テンポは動画の再生速度を使い、移調のときに音声処理を接続します。`music-worklet-source.js` がAudioWorklet方式、`music-compat.js` が互換方式です。SoundTouchJS本体とライセンスは `third_party/soundtouch/` にあります。

## 更新互換性

表示名はSatiModです。アプリID、Javaパッケージ、設定の保存名、WebExtension ID、内部メッセージ名に残る `flowview` / `FlowView` は互換性を保つ内部識別子です。表示名の変更だけを目的とした一括置換は行わないでください。
