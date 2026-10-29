package jp.flowview.android;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.app.PictureInPictureParams;
import android.app.PictureInPictureUiState;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.Html;
import android.util.Rational;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;
import org.mozilla.geckoview.WebRequestError;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A single viewing session, kept alive across rotation and extension dialogs. */
public final class MainActivity extends AppCompatActivity implements PlaybackService.Observer {
    private static final String HOME_URL = "https://m.youtube.com/";
    private static final String CONTROL_ID = "controls@flowview.local";
    private static final int BG=FlowUi.BG,PANEL=FlowUi.CARD,MINT=FlowUi.MINT;
    private FlowUi ui;
    private MusicPanel musicPanel;
    private static GeckoRuntime runtime;
    private GeckoSession session;
    private GeckoView web;
    private WebExtension controller;
    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout column, header, toolbar;
    private TextView address;
    private Button forwardButton;
    private ProgressBar progress;
    private boolean fullscreen, canBack, canForward, foreground, playing;
    private final PresentationState viewing=new PresentationState();
    private boolean consumingFullscreenSwipe;
    private final FullscreenSwipe fullscreenSwipe=new FullscreenSwipe();
    private String lastCopiedUrl="";
    private long lastCopyTime,pipTransitionGeneration;
    private String currentUrl = HOME_URL;
    private final Map<String,WebExtension.Action> actions = new HashMap<>();
    private final List<WebExtension> attachedExtensions = new ArrayList<>();
    private final List<Dialog> auxiliary = new ArrayList<>();
    private final Map<GeckoSession,Dialog> auxiliaryDialogs = new HashMap<>();
    private final Map<GeckoSession,TextView> auxiliaryLabels = new HashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("flowview",MODE_PRIVATE);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        createUi();
        if(runtime==null) runtime=GeckoRuntime.create(getApplicationContext(),new GeckoRuntimeSettings.Builder()
                .remoteDebuggingEnabled(false).consoleOutput(false)
                .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK).build());
        session=PlaybackService.retainedSession();
        final boolean retained=session!=null;
        if(!retained)session=new GeckoSession(new GeckoSessionSettings.Builder().useTrackingProtection(true).suspendMediaWhenInactive(false).build());
        configureSession(session,true);
        if(!retained)session.open(runtime);
        PlaybackService.attach(getApplicationContext(),session,this);
        playing=PlaybackService.isPlaying();
        viewing.requestFullscreen(retained&&!"inline".equals(PlaybackService.presentationMode()));
        canBack=PlaybackService.canGoBack();canForward=PlaybackService.canGoForward();
        forwardButton.setEnabled(canForward);forwardButton.setAlpha(canForward?1f:0.35f);
        web.setSession(session);
        applyPresentation();
        web.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updatePipParams());
        runtime.getWebExtensionController().setTabActive(session,true);
        configureExtensionPrompts();
        runtime.getWebExtensionController().list().accept(list->{for(WebExtension e:list)attachExtension(e);},e->{});
        String launch=launchUrl(getIntent());
        if(launch==null&&!retained)launch=restoreUrl();
        if(retained){currentUrl=PlaybackService.retainedUrl();address.setText(displayOrigin(currentUrl));}
        final String startUrl=launch;
        runtime.getWebExtensionController().ensureBuiltIn("resource://android/assets/controller/",CONTROL_ID).accept(ext->{
            if(isDestroyed())return;controller=ext;attachController();if(startUrl!=null)session.loadUri(startUrl);
        },error->{
            if(isDestroyed())return;if(startUrl!=null)session.loadUri(startUrl);
            alert("操作機能を開始できませんでした","動画の表示はできますが、追加ジェスチャーは使えません。アプリを終了して開き直してください。\n"+error.getClass().getSimpleName());
        });
        if(!prefs.getBoolean("introduced",false))new MaterialAlertDialogBuilder(this)
                .setTitle("SatiMod へようこそ")
                .setMessage("コメントはそのままスクロールできます。引っ張って更新はありません。\n\n通常動画の映像上で\n↑ 全画面にする\n↓ 縦画面に戻す（全画面中）\n左右のダブルタップ：10秒移動\n\n全画面でホームへ移動：ドラッグできる小窓\n縦画面でホームへ移動：音声のみ継続\n通知から再生・一時停止・停止できます。\n\n拡張はFirefox対応版を追加します。ChromeストアのCRXは直接インストールできません。")
                .setPositiveButton("始める",(d,w)->prefs.edit().putBoolean("introduced",true).apply()).show();
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){back();}});
    }

    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String text,Runnable click){return ui.button(text,null,click,false);}
    private void createUi(){
        ui=new FlowUi(this);
        musicPanel=new MusicPanel(this,this::sendMusic,c->{if("back10".equals(c))send("seek","delta",-10);else if("next10".equals(c))send("seek","delta",10);else command(c);},()->{viewing.requestFullscreen(true);applyPresentation();},this::enterPip);
        root=new FrameLayout(this);root.setBackgroundColor(BG);column=ui.column();root.addView(column,new FrameLayout.LayoutParams(-1,-1));
        header=ui.row();header.setPadding(dp(16),dp(6),dp(12),dp(8));header.setBackgroundColor(BG);
        MaterialButton home=ui.icon("play","YouTubeホームへ",()->navigate(HOME_URL),true);home.setCornerRadius(dp(16));header.addView(home,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout brand=ui.column();TextView name=ui.title("SatiMod",20);name.setLetterSpacing(0.02f);brand.addView(name);ui.gap(brand,4);address=ui.text("YouTubeブラウザー",11,FlowUi.MUTED);address.setSingleLine(true);address.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);brand.addView(address);brand.setContentDescription("URLを開く");brand.setOnClickListener(v->openInput());
        LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(0,-2,1);bl.setMargins(dp(12),0,dp(8),0);header.addView(brand,bl);header.addView(ui.icon("search","YouTubeで検索",this::search,false),new LinearLayout.LayoutParams(dp(48),dp(48)));column.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));column.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));progress.setVisibility(View.INVISIBLE);
        web=new GeckoView(this);web.setOverScrollMode(View.OVER_SCROLL_NEVER);web.setBackgroundColor(BG);column.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        toolbar=ui.row();toolbar.setBackground(ui.shape(FlowUi.SURFACE,26));toolbar.setPadding(dp(5),dp(5),dp(5),dp(5));toolbar.setElevation(dp(4));
        MaterialButton back=ui.nav("back","戻る",this::back,false);forwardButton=ui.nav("forward","進む",this::forward,false);forwardButton.setEnabled(false);forwardButton.setAlpha(.35f);
        Button[] buttons={back,forwardButton,ui.nav("refresh","更新",this::confirmReload,false),ui.nav("music","音楽",this::showMusic,true),ui.nav("extensions","拡張",this::extensionsMenu,false),ui.nav("more","メニュー",this::menu,false)};
        for(int i=0;i<buttons.length;i++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(60),i==3?1.2f:1);lp.setMargins(dp(1),0,dp(1),0);toolbar.addView(buttons[i],lp);}
        LinearLayout.LayoutParams dock=new LinearLayout.LayoutParams(-1,dp(70));dock.setMargins(dp(10),dp(8),dp(10),dp(8));column.addView(toolbar,dock);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Insets ime=insets.getInsets(WindowInsets.Type.ime());
            if(viewing.videoOnly()||isInPictureInPictureMode())root.setPadding(0,0,0,0);else root.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));return insets;
        });setContentView(root);
    }
    private void sendMusic(JSONObject data){
        try{if(!PlaybackService.send(new JSONObject().put("command","music").put("data",data)))musicPanel.update(new JSONObject().put("error","NoVideo"));}
        catch(Exception ignored){}
    }
    private void showMusic(){
        Dialog d=musicPanel.show();if(!auxiliary.contains(d))auxiliary.add(d);
        d.setOnDismissListener(x->{musicPanel.dismissed();auxiliary.remove(d);});
    }

    private void configureSession(GeckoSession s,boolean main){
        s.setContentDelegate(new GeckoSession.ContentDelegate(){
            @Override public void onFullScreen(GeckoSession source,boolean value){if(main&&(controller==null||!isYoutube(currentUrl)))setFullScreen(value);}
            @Override public void onTitleChange(GeckoSession source,String title){if(main)PlaybackService.setTitle(title);}
            @Override public void onContextMenu(GeckoSession source,int x,int y,ContextElement element){
                if(isWebLink(element.linkUri)){copyLink(element.linkUri);return;}
                if(element.type==ContextElement.TYPE_VIDEO&&isYoutube(element.baseUri))copyLink(element.baseUri);
                else if(element.type==ContextElement.TYPE_IMAGE&&!isYoutube(element.baseUri)&&isWebLink(element.srcUri))copyLink(element.srcUri);
            }
            @Override public void onCloseRequest(GeckoSession source){if(!main){Dialog d=auxiliaryDialogs.get(source);if(d!=null)d.dismiss();else source.close();}}
            @Override public void onCrash(GeckoSession source){if(main)alert("表示エンジンが停止しました","自動再読み込みはしません。メニューから再読み込みするか、アプリを開き直してください。");}
            @Override public void onKill(GeckoSession source){if(main)toast("メモリ不足で表示が停止しました。メニューから再読み込みできます。");}
        });
        s.setNavigationDelegate(new GeckoSession.NavigationDelegate(){
            @Override public void onLocationChange(GeckoSession source,String url,List<GeckoSession.PermissionDelegate.ContentPermission> permissions,Boolean gesture){
                if(main&&url!=null){currentUrl=url;PlaybackService.setPageUrl(url);address.setText(displayOrigin(url));if(isYoutube(url))prefs.edit().putString("lastUrl",url).apply();}
                else if(url!=null&&auxiliaryLabels.containsKey(source))auxiliaryLabels.get(source).setText("閉じる · "+displayOrigin(url));
            }
            @Override public void onCanGoBack(GeckoSession source,boolean value){if(main){canBack=value;PlaybackService.setCanGoBack(value);}}
            @Override public void onCanGoForward(GeckoSession source,boolean value){if(main){canForward=value;PlaybackService.setCanGoForward(value);forwardButton.setEnabled(value);forwardButton.setAlpha(value?1f:0.35f);}}
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession source,GeckoSession.NavigationDelegate.LoadRequest r){
                Uri u=Uri.parse(r.uri);String scheme=u.getScheme();
                if("https".equals(scheme)||"about".equals(scheme)||"moz-extension".equals(scheme))return GeckoResult.allow();
                if(main)toast("このリンクはSatiModでは開けません");return GeckoResult.deny();
            }
            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession source,String uri){return GeckoResult.fromValue(openAuxiliary("ページ · "+displayOrigin(uri),null,false));}
            @Override public GeckoResult<String> onLoadError(GeckoSession source,String uri,WebRequestError error){
                if(main){progress.setVisibility(View.INVISIBLE);toast("ページを読み込めませんでした。接続を確認し、メニューから再読み込みしてください。");}
                return null; // Preserve Gecko's certificate and network error handling.
            }
        });
        s.setProgressDelegate(new GeckoSession.ProgressDelegate(){
            @Override public void onPageStart(GeckoSession source,String url){if(main){progress.setVisibility(View.VISIBLE);progress.setProgress(5);}}
            @Override public void onProgressChange(GeckoSession source,int value){if(main)progress.setProgress(value);}
            @Override public void onPageStop(GeckoSession source,boolean success){if(main)progress.setVisibility(View.INVISIBLE);}
        });
        s.setPermissionDelegate(new GeckoSession.PermissionDelegate(){
            @Override public GeckoResult<Integer> onContentPermissionRequest(GeckoSession source,ContentPermission permission){
                boolean autoplay=permission.permission==PERMISSION_AUTOPLAY_AUDIBLE||permission.permission==PERMISSION_AUTOPLAY_INAUDIBLE;
                return GeckoResult.fromValue(autoplay?ContentPermission.VALUE_ALLOW:ContentPermission.VALUE_DENY);
            }
        });
        s.setPromptDelegate(new GeckoSession.PromptDelegate(){
            @Override public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession source,AlertPrompt p){
                GeckoResult<PromptResponse> result=new GeckoResult<>();new MaterialAlertDialogBuilder(MainActivity.this).setTitle(p.title).setMessage(p.message)
                        .setPositiveButton("OK",(d,w)->result.complete(p.dismiss())).setOnCancelListener(d->result.complete(p.dismiss())).show();return result;
            }
            @Override public GeckoResult<PromptResponse> onTextPrompt(GeckoSession source,TextPrompt p){
                GeckoResult<PromptResponse> result=new GeckoResult<>();EditText input=new EditText(MainActivity.this);input.setText(p.defaultValue);
                new MaterialAlertDialogBuilder(MainActivity.this).setTitle(p.title).setMessage(p.message).setView(input)
                        .setPositiveButton("OK",(d,w)->result.complete(p.confirm(input.getText().toString())))
                        .setNegativeButton("キャンセル",(d,w)->result.complete(p.dismiss())).setOnCancelListener(d->result.complete(p.dismiss())).show();return result;
            }
            @Override public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession source,ButtonPrompt p){
                GeckoResult<PromptResponse> result=new GeckoResult<>();new MaterialAlertDialogBuilder(MainActivity.this).setTitle(p.title).setMessage(p.message)
                        .setPositiveButton("OK",(d,w)->result.complete(p.confirm(ButtonPrompt.Type.POSITIVE)))
                        .setNegativeButton("キャンセル",(d,w)->result.complete(p.dismiss())).setOnCancelListener(d->result.complete(p.dismiss())).show();return result;
            }
        });
    }

    private void attachController(){PlaybackService.connectController(controller);}
    @Override public void onControllerMessage(JSONObject message){
        String type=message.optString("type");
        if("presentationRequest".equals(type)){
            if(!foreground||viewing.inPipOrEntering()||!PlaybackService.currentPresentation(message.optLong("revision",-1))){PlaybackService.syncPresentation();return;}
            String mode=message.optString("mode");
            if("inline".equals(mode))leaveFullScreen();
            else if("full".equals(mode)){viewing.requestFullscreen(true);applyPresentation();}
        }else if("presentationApplied".equals(type)){
            if(!PlaybackService.currentPresentation(message.optLong("revision",-1))||!PlaybackService.presentationMode().equals(message.optString("mode")))return;
            if(message.optBoolean("ok"))viewing.restored(message.optString("mode"));
            else if(!viewing.inPipOrEntering()&&fullscreen){
                PlaybackService.note("view unavailable: "+message.optString("error"));
                leaveFullScreen();toast("動画の準備ができてから全画面にしてください");
            }
        }
        else if("copyLink".equals(type))copyLink(message.optString("url"));
        else if("music".equals(type))musicPanel.update(message.optJSONObject("state"));
    }
    private void command(String command){send(command,null,null);}
    private void send(String command,String key,Object value){
        try{JSONObject obj=new JSONObject().put("command",command);if(key!=null)obj.put(key,value);
            if(!PlaybackService.send(obj))toast("動画ページの準備ができてから操作してください");
        }catch(Exception e){toast("操作を送れませんでした");}
    }
    private void sendSettings(){if(PlaybackService.controllerConnected())send("settings","gestures",prefs.getBoolean("gestures",true));}
    private void setFullScreen(boolean enabled){viewing.pageFullscreen(enabled);applyPresentation();}
    private void applyPresentation(){
        fullscreen=viewing.fullscreen();boolean videoOnly=viewing.videoOnly()||isInPictureInPictureMode();
        header.setVisibility(videoOnly?View.GONE:View.VISIBLE);toolbar.setVisibility(videoOnly?View.GONE:View.VISIBLE);progress.setVisibility(View.INVISIBLE);
        if(!isInPictureInPictureMode()&&!viewing.inPipOrEntering()){
            int orientation=fullscreen?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            if(getRequestedOrientation()!=orientation)setRequestedOrientation(orientation);
            WindowInsetsController bars=getWindow().getInsetsController();
            if(bars!=null){bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(fullscreen)bars.hide(WindowInsets.Type.systemBars());else bars.show(WindowInsets.Type.systemBars());}
        }
        PlaybackService.setPresentation(viewing.pageMode());root.requestApplyInsets();updatePipParams();
    }
    private void leaveFullScreen(){
        viewing.requestFullscreen(false);fullscreenSwipe.cancel();pipTransitionGeneration++;PlaybackService.cancelPipTransition();
        if(!PlaybackService.controllerConnected())session.exitFullScreen();
        applyPresentation();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        final int action=event.getActionMasked();
        if(consumingFullscreenSwipe){
            if(action==MotionEvent.ACTION_DOWN)consumingFullscreenSwipe=false;
            else {if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)consumingFullscreenSwipe=false;return true;}
        }
        if(!fullscreen||isInPictureInPictureMode()||!prefs.getBoolean("gestures",true)){fullscreenSwipe.cancel();return super.dispatchTouchEvent(event);}
        float density=getResources().getDisplayMetrics().density;
        if(action==MotionEvent.ACTION_DOWN)fullscreenSwipe.start(event.getX()/density,event.getY()/density,event.getEventTime());
        else if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL)fullscreenSwipe.cancel();
        else if((action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP)&&event.getPointerCount()==1
                &&fullscreenSwipe.move(event.getX()/density,event.getY()/density,event.getEventTime())){
            MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();
            consumingFullscreenSwipe=action!=MotionEvent.ACTION_UP;leaveFullScreen();return true;
        }
        if(action==MotionEvent.ACTION_UP)fullscreenSwipe.cancel();
        return super.dispatchTouchEvent(event);
    }
    @Override public void onPlaybackChanged(boolean value){playing=value;if(musicPanel!=null)musicPanel.playback(value);updateAwake();updatePipParams();}
    private void updateAwake(){if(playing&&foreground)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}

    private void playbackMenu(){
        new MaterialAlertDialogBuilder(this).setTitle("動画の操作").setItems(new String[]{"再生 / 一時停止","10秒戻す","10秒進める","全画面にする","再生速度を変える","小窓で見る（PiP）"},(d,w)->{
            switch(w){case 0:command("playpause");break;case 1:send("seek","delta",-10);break;case 2:send("seek","delta",10);break;case 3:viewing.requestFullscreen(true);applyPresentation();break;case 4:speedMenu();break;case 5:enterPip();break;}
        }).show();
    }
    private void speedMenu(){double[] speeds={0.5,0.75,1,1.25,1.5,1.75,2};new MaterialAlertDialogBuilder(this).setTitle("再生速度").setItems(new String[]{"0.5倍","0.75倍","1倍","1.25倍","1.5倍","1.75倍","2倍"},(d,w)->send("speed","value",speeds[w])).show();}
    private PictureInPictureParams pipParams(){
        PictureInPictureParams.Builder params=new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9))
                .setAutoEnterEnabled(fullscreen&&(playing||viewing.inPipOrEntering())).setSeamlessResizeEnabled(true);
        Rect bounds=new Rect();if(web.getGlobalVisibleRect(bounds)&&!bounds.isEmpty())params.setSourceRectHint(bounds);
        return params.build();
    }
    private void updatePipParams(){
        if(web==null||isDestroyed())return;
        try{setPictureInPictureParams(pipParams());}catch(IllegalStateException|IllegalArgumentException ignored){}
    }
    private void preparePip(boolean returnToFullscreen){
        if(viewing.inPipOrEntering())return;
        final long generation=++pipTransitionGeneration;
        viewing.beginPip(returnToFullscreen);fullscreenSwipe.cancel();PlaybackService.beginPipTransition();applyPresentation();
        root.postDelayed(()->{
            if(generation==pipTransitionGeneration&&!isDestroyed()&&!isInPictureInPictureMode()&&foreground&&hasWindowFocus()&&viewing.inPipOrEntering()){
                viewing.leftPip();PlaybackService.cancelPipTransition();applyPresentation();PlaybackService.note("PiP transition cancelled");
            }
        },1800);
    }
    // Auto-enter is configured in PiP params. UserLeaveHint also fires for non-PiP UI;
    // only an actual OS transition (or the explicit PiP action) changes presentation.
    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);fullscreenSwipe.cancel();
        if(!focused){consumingFullscreenSwipe=false;PlaybackService.command("cancelGesture");}
    }
    @Override public void onPictureInPictureUiStateChanged(PictureInPictureUiState state){
        super.onPictureInPictureUiStateChanged(state);
        if(Build.VERSION.SDK_INT>=35&&state.isTransitioningToPip())preparePip(fullscreen);
    }
    private void enterPip(){
        if(!PlaybackService.controllerConnected()){toast("動画を開いてから操作してください");return;}
        preparePip(fullscreen);
        final long generation=pipTransitionGeneration;
        root.postDelayed(()->{
            if(isDestroyed()||isFinishing()||generation!=pipTransitionGeneration||!viewing.inPipOrEntering())return;
            try{if(enterPictureInPictureMode(pipParams()))return;}
            catch(IllegalStateException|IllegalArgumentException ignored){}
            viewing.leftPip();PlaybackService.cancelPipTransition();applyPresentation();toast("Androidのアプリ設定でピクチャー イン ピクチャーを許可してください");
        },200);
    }
    @Override public void onPictureInPictureModeChanged(boolean inPip,Configuration config){
        super.onPictureInPictureModeChanged(inPip,config);
        if(inPip){preparePip(fullscreen);viewing.enteredPip();}
        else viewing.leftPip();
        if(session!=null){session.getCompositorController().onPipModeChanged(inPip);if(inPip)session.setActive(true);}
        PlaybackService.note(inPip?"PiP entered":"PiP left");
        if(!isFinishing())applyPresentation();
    }
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);PlaybackService.command("cancelGesture");fullscreenSwipe.cancel();consumingFullscreenSwipe=false;applyPresentation();}
    private void menu(){
        String gesture=prefs.getBoolean("gestures",true)?"動画ジェスチャー：ON → OFF":"動画ジェスチャー：OFF → ON";
        new MaterialAlertDialogBuilder(this).setTitle("SatiMod").setItems(new String[]{"URLを開く / 貼り付ける","動画を共有する","ページの先頭へ",gesture,"ページを再読み込みする","使い方・このアプリについて","再生の診断","再生の操作","YouTubeホームへ"},(d,w)->{
            switch(w){
                case 0:openInput();break;
                case 1:startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,currentUrl),"動画を共有"));break;
                case 2:command("top");break;
                case 3:prefs.edit().putBoolean("gestures",!prefs.getBoolean("gestures",true)).apply();sendSettings();break;
                case 4:confirmReload();break;
                case 5:about();break;
                case 7:playbackMenu();break;
                case 8:navigate(HOME_URL);break;
                case 6:
                    String report=PlaybackService.diagnostics();
                    new MaterialAlertDialogBuilder(this).setTitle("再生の診断").setMessage(report).setPositiveButton("コピー",(dialog,which)->{
                        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("SatiMod 再生診断",report));
                    }).setNegativeButton("閉じる",null).show();break;
            }
        }).show();
    }
    private void input(String title,String initial,java.util.function.Consumer<String> accept){
        EditText field=new EditText(this);field.setSingleLine(true);field.setText(initial);field.setSelectAllOnFocus(true);field.setPadding(dp(20),dp(16),dp(20),dp(16));
        new MaterialAlertDialogBuilder(this).setTitle(title).setView(field).setPositiveButton("開く",(d,w)->accept.accept(field.getText().toString().trim())).setNegativeButton("キャンセル",null).show();
    }
    private void openInput(){input("URL または検索語",currentUrl,text->{if(text.startsWith("https://"))navigate(text);else navigate("https://m.youtube.com/results?search_query="+Uri.encode(text));});}
    private void search(){input("YouTubeで検索","",q->navigate("https://m.youtube.com/results?search_query="+Uri.encode(q)));}
    private void navigate(String url){if(fullscreen)leaveFullScreen();session.loadUri(url);}
    private void back(){if(fullscreen){leaveFullScreen();return;}if(session!=null&&canBack)session.goBack();else moveTaskToBack(true);}
    private void forward(){if(session!=null&&canForward){if(fullscreen)leaveFullScreen();session.goForward();}}
    private void confirmReload(){
        new MaterialAlertDialogBuilder(this).setTitle("ページを更新しますか？").setMessage("再生位置や入力中の内容が変わることがあります。")
                .setPositiveButton("更新",(d,w)->{if(fullscreen)leaveFullScreen();session.reload();}).setNegativeButton("やめる",null).show();
    }
    private static boolean isWebLink(String value){
        try{if(value==null||value.length()>8192)return false;java.net.URI u=java.net.URI.create(value);
            return ("https".equals(u.getScheme())||"http".equals(u.getScheme()))&&u.getHost()!=null&&u.getRawUserInfo()==null;
        }catch(Exception e){return false;}
    }
    private void copyLink(String url){
        if(!isWebLink(url)||!foreground)return;
        long now=SystemClock.elapsedRealtime();if(url.equals(lastCopiedUrl)&&now-lastCopyTime<700)return;
        ClipboardManager clipboard=getSystemService(ClipboardManager.class);if(clipboard==null)return;
        clipboard.setPrimaryClip(ClipData.newPlainText("リンク",url));lastCopiedUrl=url;lastCopyTime=now;
        if(Build.VERSION.SDK_INT<33)toast("リンクをコピーしました");
    }
    private void configureExtensionPrompts(){
        runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate(){
            @Override public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(WebExtension ext,String[] permissions,String[] origins,String[] data){
                GeckoResult<WebExtension.PermissionPromptResponse> result=new GeckoResult<>();
                new MaterialAlertDialogBuilder(MainActivity.this).setTitle(extensionName(ext)+" を追加")
                        .setMessage(permissionText(permissions,origins,data))
                        .setPositiveButton("権限を許可して追加",(d,w)->result.complete(new WebExtension.PermissionPromptResponse(true,false,false)))
                        .setNegativeButton("キャンセル",(d,w)->result.complete(new WebExtension.PermissionPromptResponse(false,false,false)))
                        .setOnCancelListener(d->result.complete(new WebExtension.PermissionPromptResponse(false,false,false))).show();return result;
            }
            @Override public GeckoResult<AllowOrDeny> onOptionalPrompt(WebExtension ext,String[] permissions,String[] origins,String[] data){return permissionDecision(extensionName(ext),permissions,origins,data);}
            @Override public GeckoResult<AllowOrDeny> onUpdatePrompt(WebExtension ext,String[] permissions,String[] origins,String[] data){return permissionDecision(extensionName(ext)+" の更新",permissions,origins,data);}
        });
    }
    private GeckoResult<AllowOrDeny> permissionDecision(String name,String[] permissions,String[] origins,String[] data){
        GeckoResult<AllowOrDeny> r=new GeckoResult<>();new MaterialAlertDialogBuilder(this).setTitle(name+" の権限").setMessage(permissionText(permissions,origins,data))
                .setPositiveButton("許可",(d,w)->r.complete(AllowOrDeny.ALLOW)).setNegativeButton("拒否",(d,w)->r.complete(AllowOrDeny.DENY)).setOnCancelListener(d->r.complete(AllowOrDeny.DENY)).show();return r;
    }
    private String permissionText(String[] permissions,String[] origins,String[] data){
        return "この拡張は以下にアクセスできます。内容を確認してください。\n\n権限\n"+String.join("\n",permissions)+"\n\n対象サイト\n"+String.join("\n",origins)
                +(data.length>0?"\n\n要求されるデータ権限\n"+String.join("\n",data):"");
    }
    private String extensionName(WebExtension e){return e.metaData!=null&&e.metaData.name!=null?e.metaData.name:e.id;}
    private void attachExtension(WebExtension ext){
        if(CONTROL_ID.equals(ext.id)||isDestroyed())return;
        attachedExtensions.add(ext);
        ext.setActionDelegate(new WebExtension.ActionDelegate(){
            @Override public void onBrowserAction(WebExtension e,GeckoSession s,WebExtension.Action action){if(s==null||s==session)actions.put(e.id,action);}
            @Override public GeckoResult<GeckoSession> onTogglePopup(WebExtension e,WebExtension.Action action){return GeckoResult.fromValue(openAuxiliary(extensionName(e),null,true));}
            @Override public GeckoResult<GeckoSession> onOpenPopup(WebExtension e,WebExtension.Action action){return GeckoResult.fromValue(openAuxiliary(extensionName(e),null,true));}
        });
        ext.setTabDelegate(new WebExtension.TabDelegate(){
            @Override public GeckoResult<GeckoSession> onNewTab(WebExtension e,WebExtension.CreateTabDetails details){return GeckoResult.fromValue(openAuxiliary(extensionName(e),null,false));}
            @Override public void onOpenOptionsPage(WebExtension e){if(e.metaData!=null&&e.metaData.optionsPageUrl!=null)openAuxiliary(extensionName(e),e.metaData.optionsPageUrl,false);}
        });
    }
    private GeckoSession openAuxiliary(String title,String url,boolean popup){
        Dialog dialog=new Dialog(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setBackgroundColor(BG);
        Button close=button("閉じる · "+title,dialog::dismiss);box.addView(close,new LinearLayout.LayoutParams(-1,dp(52)));
        GeckoView child=new GeckoView(this);box.addView(child,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(box);
        // GeckoView marks extension popup sessions when it loads the action URL.
        GeckoSession aux=new GeckoSession();configureSession(aux,false);aux.open(runtime);child.setSession(aux);
        auxiliaryDialogs.put(aux,dialog);auxiliaryLabels.put(aux,close);
        dialog.setOnDismissListener(d->{aux.close();auxiliary.remove(dialog);auxiliaryDialogs.remove(aux);auxiliaryLabels.remove(aux);});auxiliary.add(dialog);dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*0.88));
        if(url!=null)aux.loadUri(url);return aux;
    }
    private void extensionsMenu(){
        FlowUi.Sheet sheet=ui.sheet("拡張機能","いつもの視聴に、好きな機能を。",610);Dialog d=sheet.dialog;
        ScrollView scroll=new ScrollView(this);LinearLayout body=ui.column();scroll.addView(body);sheet.body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout music=ui.card();LinearLayout heading=ui.row();heading.addView(ui.icon("music","内蔵の音楽ツール",()->{d.dismiss();showMusic();},true),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout words=ui.column();words.addView(ui.title("Music Studio",20));ui.gap(words,5);words.addView(ui.text("内蔵 · 追加インストール不要",12,MINT));LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(14);heading.addView(words,wp);music.addView(heading);ui.gap(music,14);music.addView(ui.text("再生中のキーとテンポを、±で好みに調整できます。",14,FlowUi.MUTED));ui.gap(music,16);music.addView(ui.button("音楽ツールを開く","music",()->{d.dismiss();showMusic();},true),new LinearLayout.LayoutParams(-1,dp(48)));body.addView(music);ui.gap(body,16);
        body.addView(ui.button("拡張を検索する","search",()->{d.dismiss();searchAddons();},false),new LinearLayout.LayoutParams(-1,dp(52)));ui.gap(body,10);
        body.addView(ui.button("インストール済みを管理","extensions",()->{d.dismiss();installedExtensions();},false),new LinearLayout.LayoutParams(-1,dp(52)));ui.gap(body,18);
        body.addView(ui.text("外部の音声拡張",12,FlowUi.MUTED));ui.gap(body,10);
        body.addView(ui.button("PitchShifter を追加","plus",()->{d.dismiss();installAddon("https://addons.mozilla.org/firefox/downloads/latest/pitchshifter/latest.xpi",null);},false),new LinearLayout.LayoutParams(-1,dp(48)));ui.gap(body,8);body.addView(ui.text("Firefox for Android対応。外部の音声拡張へ切り替えるときは、内蔵Music StudioをOFFにしてページを更新してください。",12,FlowUi.MUTED));ui.gap(body,12);
        body.addView(ui.button("URL・XPIから追加 / その他",null,()->{d.dismiss();addonsCatalog();},false),new LinearLayout.LayoutParams(-1,dp(48)));
        d.setOnDismissListener(x->auxiliary.remove(d));auxiliary.add(d);d.show();
    }
    private void addonsCatalog(){
        new MaterialAlertDialogBuilder(this).setTitle("拡張を追加").setItems(new String[]{"名前・キーワードで検索","uBlock Origin","SponsorBlock","Mozilla Add-onsのURLから追加","署名済みXPIファイルから追加"},(d,w)->{
            String[] slugs={"ublock-origin","sponsorblock"};
            if(w==0)searchAddons();
            else if(w<3)installAddon("https://addons.mozilla.org/firefox/downloads/latest/"+slugs[w-1]+"/latest.xpi",null);
            else if(w==4){Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("*/*");startActivityForResult(pick,41);}
            else input("Mozilla Add-onsのURL","",text->{
                Uri uri=Uri.parse(text);if(!"https".equals(uri.getScheme())||!"addons.mozilla.org".equals(uri.getHost())){toast("addons.mozilla.orgのHTTPS URLを指定してください");return;}
                Matcher match=Pattern.compile("/addon/([a-zA-Z0-9_-]+)").matcher(uri.getPath()==null?"":uri.getPath());
                if(match.find())installAddon("https://addons.mozilla.org/firefox/downloads/latest/"+match.group(1)+"/latest.xpi",null);
                else if(uri.getPath()!=null&&uri.getPath().endsWith(".xpi"))installAddon(text,null);
                else toast("拡張の紹介ページまたはXPIのURLを指定してください");
            });
        }).show();
    }
    private void searchAddons(){searchAddons("");}
    private void searchAddons(String initial){
        FlowUi.Sheet sheet=ui.sheet("拡張を探す","Firefox for Android · Mozilla Add-ons",820);Dialog dialog=sheet.dialog;LinearLayout box=sheet.body;
        LinearLayout queryRow=ui.row();queryRow.setPadding(dp(12),0,dp(4),0);queryRow.setBackground(ui.shape(FlowUi.CARD,20));
        EditText query=new EditText(this);query.setSingleLine(true);query.setTextSize(16);query.setTextColor(FlowUi.TEXT);query.setHintTextColor(FlowUi.MUTED);query.setHint("名前・キーワードで検索");query.setBackgroundColor(Color.TRANSPARENT);query.setPadding(0,0,dp(8),0);query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);query.setText(initial);
        queryRow.addView(query,new LinearLayout.LayoutParams(0,dp(56),1));Button searchButton=ui.icon("search","拡張を検索",()->{},true);queryRow.addView(searchButton,new LinearLayout.LayoutParams(dp(48),dp(48)));box.addView(queryRow);
        LinearLayout shortcuts=ui.row();String[] labels={"音楽","YouTube","広告ブロック"},terms={"pitch","YouTube","ublock"};
        for(int i=0;i<labels.length;i++){final String term=terms[i];Button chip=ui.button(labels[i],null,()->{dialog.dismiss();searchAddons(term);},false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(48),1);cp.setMargins(dp(2),dp(10),dp(2),0);shortcuts.addView(chip,cp);}box.addView(shortcuts);
        TextView status=ui.text("気になる機能を検索してください",12,FlowUi.MUTED);status.setPadding(0,dp(16),0,dp(12));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);box.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout results=ui.column();scroll.addView(results);box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout pages=ui.row();Button previous=ui.button("前へ","back",()->{},false),next=ui.button("次へ","forward",()->{},false);previous.setEnabled(false);next.setEnabled(false);LinearLayout.LayoutParams pg=new LinearLayout.LayoutParams(0,dp(48),1);pg.setMargins(dp(3),dp(10),dp(3),0);pages.addView(previous,pg);pages.addView(next,new LinearLayout.LayoutParams(pg));box.addView(pages);
        ExecutorService executor=Executors.newSingleThreadExecutor();
        class SearchState {
            boolean alive=true,busy=false;int page=1;String term="";
            void load(String word,int requestedPage){
                if(busy||!alive)return;
                try{AddonCatalog.searchUrl(word,requestedPage);}catch(IllegalArgumentException e){query.setError(e.getMessage());return;}
                busy=true;searchButton.setEnabled(false);previous.setEnabled(false);next.setEnabled(false);results.removeAllViews();status.setText("「"+word.trim()+"」を検索中…");
                InputMethodManager ime=getSystemService(InputMethodManager.class);if(ime!=null)ime.hideSoftInputFromWindow(query.getWindowToken(),0);
                executor.execute(()->{
                    try{AddonCatalog.Result found=AddonCatalog.search(word,requestedPage);
                        runOnUiThread(()->{
                            if(!alive||isDestroyed())return;busy=false;term=word.trim();page=requestedPage;searchButton.setEnabled(true);previous.setEnabled(page>1);next.setEnabled(found.hasMore);
                            status.setText(found.addons.isEmpty()?"見つかりませんでした。検索語を変えてください":"「"+term+"」 · "+page+"ページ目 · "+found.addons.size()+"件");
                            for(AddonCatalog.Addon addon:found.addons){
                                LinearLayout card=ui.card();card.setPadding(dp(16),dp(16),dp(16),dp(16));LinearLayout line=ui.row(),words=ui.column();
                                TextView name=ui.title(addon.name,16);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(name);
                                if(!addon.author.isEmpty()){ui.gap(words,6);TextView author=ui.text(addon.author,12,MINT);author.setSingleLine(true);author.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(author);}
                                line.addView(words,new LinearLayout.LayoutParams(0,-2,1));Button add=ui.icon("plus",addon.name+" を追加",()->{dialog.dismiss();installAddon(addon.downloadUrl,null);},false);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(48),dp(48));ap.leftMargin=dp(12);line.addView(add,ap);card.addView(line);
                                if(!addon.summary.isEmpty()){ui.gap(card,12);TextView summary=ui.text(Html.fromHtml(addon.summary,Html.FROM_HTML_MODE_LEGACY).toString(),13,FlowUi.MUTED);summary.setMaxLines(3);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(summary);}
                                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);results.addView(card,cp);
                            }
                            scroll.scrollTo(0,0);
                        });
                    }catch(Exception e){runOnUiThread(()->{if(!alive||isDestroyed())return;busy=false;searchButton.setEnabled(true);status.setText("検索できませんでした。通信状態を確認して再検索してください。");});}
                });
            }
        }
        SearchState state=new SearchState();searchButton.setOnClickListener(v->state.load(query.getText().toString(),1));
        query.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){state.load(query.getText().toString(),1);return true;}return false;});
        previous.setOnClickListener(v->state.load(state.term,state.page-1));next.setOnClickListener(v->state.load(state.term,state.page+1));
        dialog.setOnDismissListener(d->{state.alive=false;executor.shutdownNow();auxiliary.remove(dialog);});auxiliary.add(dialog);dialog.show();
        if(!initial.isEmpty())state.load(initial,1);
    }

    private void installAddon(String url,File temporary){
        toast("拡張を取得しています。権限の確認画面をお待ちください。");
        runtime.getWebExtensionController().install(url).accept(ext->{attachExtension(ext);if(temporary!=null)temporary.delete();toast(extensionName(ext)+" を追加しました");},error->{
            if(temporary!=null)temporary.delete();alert("拡張を追加できませんでした","キャンセル、通信状態、署名、Android対応を確認してください。\n"+error.getMessage());
        });
    }
    private void installedExtensions(){
        runtime.getWebExtensionController().list().accept(list->{
            List<WebExtension> available=new ArrayList<>();for(WebExtension e:list)if(!e.isBuiltIn)available.add(e);
            if(available.isEmpty()){alert("拡張機能","まだ追加されていません。「追加する」から選べます。");return;}
            String[] names=new String[available.size()];for(int i=0;i<names.length;i++)names[i]=extensionName(available.get(i))+"  "+(available.get(i).metaData.enabled?"ON":"OFF");
            new MaterialAlertDialogBuilder(this).setTitle("インストール済み").setItems(names,(d,w)->extensionMenu(available.get(w))).show();
        },e->toast("一覧を取得できませんでした"));
    }
    private void extensionMenu(WebExtension ext){
        new MaterialAlertDialogBuilder(this).setTitle(extensionName(ext)).setItems(new String[]{"拡張を開く","設定を開く",ext.metaData.enabled?"無効にする":"有効にする","更新を確認","削除する"},(d,w)->{
            WebExtensionController ec=runtime.getWebExtensionController();
            if(w==0){WebExtension.Action action=actions.get(ext.id);if(action!=null)action.click();else if(ext.metaData.optionsPageUrl!=null)openAuxiliary(extensionName(ext),ext.metaData.optionsPageUrl,false);else toast("この拡張には操作画面がありません");}
            else if(w==1){if(ext.metaData.optionsPageUrl!=null)openAuxiliary(extensionName(ext),ext.metaData.optionsPageUrl,false);else toast("設定画面はありません");}
            else if(w==2){(ext.metaData.enabled?ec.disable(ext,WebExtensionController.EnableSource.USER):ec.enable(ext,WebExtensionController.EnableSource.USER)).accept(e->{attachExtension(e);toast("変更しました");},e->toast("変更できませんでした"));}
            else if(w==3){ec.update(ext).accept(e->{if(e!=null)attachExtension(e);toast(e==null?"更新はありません":"更新しました");},e->toast("更新できませんでした"));}
            else new MaterialAlertDialogBuilder(this).setTitle("削除しますか？").setMessage(extensionName(ext)).setPositiveButton("削除",(a,b)->ec.uninstall(ext).accept(v->{actions.remove(ext.id);toast("削除しました");},e->toast("削除できませんでした"))).setNegativeButton("キャンセル",null).show();
        }).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=41||result!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();
        new Thread(()->{
            File tmp=null;
            try{tmp=File.createTempFile("addon-",".xpi",getCacheDir());
                try(InputStream input=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(tmp)){
                    if(input==null)throw new java.io.IOException("ファイルを開けません");byte[] buffer=new byte[32768];long total=0;int n;
                    while((n=input.read(buffer))!=-1){total+=n;if(total>50L*1024*1024)throw new java.io.IOException("50MB以下のXPIを選んでください");out.write(buffer,0,n);}
                }
                File file=tmp;runOnUiThread(()->installAddon(Uri.fromFile(file).toString(),file));
            }catch(Exception e){if(tmp!=null)tmp.delete();runOnUiThread(()->alert("読み込めませんでした",e.getMessage()));}
        },"addon-import").start();
    }

    private void about(){alert("SatiMod 0.3.2 · 試用版","通常動画の映像上で↑全画面、全画面中に↓で縦画面へ。左右をダブルタップすると10秒移動します。\n\nコメントとShortsの通常スクロールには介入しません。操作が競合する場合は動画ジェスチャーをOFFにできます。\n\n全画面再生中にホームへ移動すると、ドラッグで移動できる小窓（PiP）になります。縦画面から離れると、画面を出さずに音声を継続します。通知から一時停止・再開・停止できます。\n\nリンクや動画を長押しするとリンクをコピーします。拡張は名前・キーワードで検索できます。\n\n下部の「音楽」からMusic StudioをONにすると、キー±12半音・テンポ50〜200%を調整できます。キーとテンポは独立して調整できます。\n\n更新は下部バーまたはメニューから確認後に行います。OSがアプリを終了した場合は再読み込みが必要です。通常動画の再生位置は約5秒ごとに端末に保存し、次の起動時に復帰を試みます。\n\nGoogle/YouTubeの公式アプリではありません。YouTubeへのログイン・動画・拡張は各サービスに通信します。SatiMod独自の解析送信やアカウント作成はありません。\n\nGeckoView 158 / Mozilla Public License 2.0。改変なしで使用。ソース：https://hg.mozilla.org/releases/mozilla-release/rev/e4ce606692cd81e6898547e2edd7c552fefcbb04\n\nSoundTouchJS 2.1.1 / MPL 2.0。ソースとライセンス：https://github.com/cutterbl/SoundTouchJS\nMaterial Components 1.14.0 / Apache 2.0。ソース：https://github.com/material-components/material-components-android\n\nブラウザーエンジン更新は新しいAPKの導入が必要です。");}
    private void alert(String title,String message){
        if(isFinishing())return;
        AlertDialog.Builder b=new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null);
        if(title.startsWith("SatiMod 0.3.2"))b.setNeutralButton("ライセンス",(d,w)->openAuxiliary("オープンソースライセンス","about:license",false));
        b.show();
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private static boolean isYoutube(String value){try{Uri u=Uri.parse(value);String h=u.getHost();return "https".equals(u.getScheme())&&(u.getPort()==-1||u.getPort()==443)&&("www.youtube.com".equals(h)||"m.youtube.com".equals(h)||"youtube.com".equals(h)||"youtu.be".equals(h));}catch(Exception e){return false;}}
    private static String displayOrigin(String url){try{Uri u=Uri.parse(url);return u.getHost()!=null?u.getHost():u.getScheme();}catch(Exception e){return "ページ";}}
    private String launchUrl(Intent intent){
        if(intent==null)return null;
        if(Intent.ACTION_VIEW.equals(intent.getAction())){String u=intent.getDataString();return isYoutube(u)?u:null;}
        if(Intent.ACTION_SEND.equals(intent.getAction())){String text=intent.getStringExtra(Intent.EXTRA_TEXT);if(text==null)return null;Matcher m=Pattern.compile("https://[^\\s<>]+").matcher(text);while(m.find())if(isYoutube(m.group()))return m.group();}
        return null;
    }
    private String restoreUrl(){
        String url=prefs.getString("lastUrl",HOME_URL);if(!isYoutube(url))return HOME_URL;
        long position=prefs.getLong("position",0);
        if(url.equals(prefs.getString("positionUrl",""))&&position>3&&"/watch".equals(Uri.parse(url).getPath())){
            Uri u=Uri.parse(url);Uri.Builder b=u.buildUpon().clearQuery();for(String key:u.getQueryParameterNames())if(!"t".equals(key)&&!"start".equals(key))for(String v:u.getQueryParameters(key))b.appendQueryParameter(key,v);
            return b.appendQueryParameter("t",position+"s").build().toString();
        }return url;
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);String url=launchUrl(intent);if(url!=null&&session!=null)navigate(url);}
    @Override protected void onStart(){super.onStart();foreground=true;PlaybackService.setUiForeground(true);if(session!=null)session.setActive(true);updateAwake();updatePipParams();}
    @Override protected void onResume(){super.onResume();if(session!=null)session.setFocused(true);if(!isInPictureInPictureMode()&&viewing.inPipOrEntering())viewing.leftPip();applyPresentation();}
    @Override protected void onStop(){super.onStop();fullscreenSwipe.cancel();consumingFullscreenSwipe=false;foreground=false;PlaybackService.setUiForeground(false);updateAwake();if(session!=null)session.setActive(isInPictureInPictureMode());}
    @Override protected void onDestroy(){
        for(Dialog d:new ArrayList<>(auxiliary))d.dismiss();
        runtime.getWebExtensionController().setPromptDelegate(null);
        for(WebExtension e:attachedExtensions){e.setActionDelegate(null);e.setTabDelegate(null);}
        if(session!=null){
            web.releaseSession();if(isFinishing())PlaybackService.stop();
            if(!PlaybackService.detach(this))session.close();
        }
        super.onDestroy();
    }
}
