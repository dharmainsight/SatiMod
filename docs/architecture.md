# コードの入口

| 場所 | 役割 |
| --- | --- |
| `app/src/main/java/jp/flowview/android/MainActivity.java` | 画面、ナビゲーション、拡張管理、全画面・PiPへの接続 |
| `PlaybackService.java` | GeckoViewのセッション保持、バックグラウンド再生、通知・MediaSession |
| `PresentationState.java` / `PresentationProtocol.java` | 縦画面・全画面・PiPの状態とリビジョン管理 |
| `FullscreenSwipe.java` | ジェスチャー判定 |
| `MusicPanel.java` | キー・テンポ・イコライザの操作パネル |
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

`MusicPanel` → `PlaybackService` → `content.js` → `music-engine.js` の順で操作が届きます。Music Studioは既定で有効ですが、操作するまで動画の速度や音声経路には触れません。初回のキー変更は現在の再生速度を引き継ぎます。テンポは動画の再生速度を使い、移調のときに音声処理を接続します。パネルはGeckoViewと同じ画面内の下部に配置し、高さを表示領域の45%・最大360dpに制限します。見出し直下に再生・10秒送り戻し・全画面・PiPの操作列を固定し、キー・テンポ・イコライザは独立したスクロール領域に表示します。外側のページ操作を遮らず、閉じる・戻る・全画面/PiP移行で閉じます。`music-worklet-source.js` がAudioWorklet方式、`music-compat.js` が互換方式です。SoundTouchJS本体とライセンスは `third_party/soundtouch/` にあります。

## 更新互換性

表示名はSatiModです。アプリID、Javaパッケージ、設定の保存名、WebExtension ID、内部メッセージ名に残る `flowview` / `FlowView` は互換性を保つ内部識別子です。表示名の変更だけを目的とした一括置換は行わないでください。

## イコライザ

音楽の `eq` 命令は5整数（各−12〜+12 dB）を受け取り、状態の `eq` 配列をパネルへ返します。60 / 230 / 910 / 3600 / 14000 Hz、Q=1のpeakingフィルターを、既存MediaElementSourceと移調ノードの後段に接続します。移調0では移調ノードを迂回し、全帯域0ではフィルターを迂回します。ソースは動画ノードごとに再利用します。スライダーの値をそのまま各フィルターのgainに渡し、正のゲインの合計による全帯域の減衰は行いません。フィルターの後段にDynamicsCompressorNode（threshold −1 dB、knee 0、ratio 20、attack 0秒、release 0.1秒）を置き、大きなピークを抑えます。これは厳密なピークリミッターではなく、音割れの完全な防止は保証しません。フラット・無効化ではこのノードも迂回します。

キー・テンポのリセットはEQを維持し、フラット操作はEQだけを戻します。パネルの開閉では状態を維持し、ページ再読み込みでは初期化します。動画ノード切替では設定を引き継ぎます。保護音源・音声拡張の競合による制限は移調と共通です。

## 利用時間

`UsageTracker` はMainActivityのonStart/onStopと画面ON/OFF・ロック解除に接続し、画面表示中（全画面・分割・PiPを含む）の時間を記録する。PlaybackServiceは計測しない。15秒ごと・停止時・分析表示時に日別合計だけを `satimod_usage` のSharedPreferencesへ保存する。終了したプロセスの計測開始時刻は復元しないため、強制終了後の空白を加算しない（直前の未保存分は最大約15秒失われ得る）。

`UsageHistory` はelapsedRealtimeによる経過時間を、端末のローカル日付の午前0時で分割する。集計は今日を含む7/30/365日、日平均は記録開始から今日までの暦日数（1〜365日、未使用日を含む）を分母とする。365日より古い日別値は削除する。OSの使用状況権限は要求せず、導入前の利用履歴は取得しない。分析画面は開いた時点の値を表示する。検索は既存のURL・検索語入力から利用できる。
