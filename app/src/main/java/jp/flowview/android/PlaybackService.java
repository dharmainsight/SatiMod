package jp.flowview.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.net.Uri;
import org.json.JSONObject;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.AllowOrDeny;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.ArrayDeque;

/** Owns the browser playback session while no Activity is attached. */
public final class PlaybackService extends Service {
    interface Observer { void onPlaybackChanged(boolean playing); void onControllerMessage(JSONObject message); }
    private static final String CHANNEL="flowview_playback",PLAY="flowview.PLAY",PAUSE="flowview.PAUSE",STOP="flowview.STOP";
    private static final int NOTIFICATION=73;
    private static PlaybackService instance;
    private static Context app;
    private static GeckoSession browser;
    private static org.mozilla.geckoview.MediaSession engine;
    private static WebExtension controller;
    private static WebExtension.Port port;
    private static final PresentationProtocol presentation=new PresentationProtocol();
    private static String pendingTransport;
    private static final Handler handler=new Handler(Looper.getMainLooper());
    private static final PlaybackRecovery recovery=new PlaybackRecovery();
    private static final ArrayDeque<String> events=new ArrayDeque<>();
    private static WeakReference<Observer> observer=new WeakReference<>(null);
    private static boolean playing,uiForeground,starting;
    private static boolean canBack,canForward;
    private static long position=PlaybackState.PLAYBACK_POSITION_UNKNOWN,duration;
    private static float speed=1f;
    private static String title="SatiMod",pageUrl="https://m.youtube.com/";
    private MediaSession controls;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean hasFocus,resumeAfterFocus;

    static void note(String event){if(events.size()>=24)events.removeFirst();events.addLast((SystemClock.elapsedRealtime()/1000)+" "+event);}
    static String diagnostics(){return "SatiMod 0.3.2 / Android API "+Build.VERSION.SDK_INT+"\nmode="+presentation.mode()+" revision="+presentation.revision()+" playing="+playing+" bridge="+(port!=null)+" engine="+(engine!=null&&engine.isActive())+"\n"+String.join("\n",events);}
    static boolean controllerConnected(){return port!=null;}
    static String presentationMode(){return presentation.mode();}
    static boolean currentPresentation(long revision){return presentation.accepts(revision);}
    static boolean send(JSONObject message){
        if(port==null)return false;
        try{port.postMessage(message);return true;}catch(RuntimeException error){note("bridge send failed");return false;}
    }
    static boolean command(String command){try{return send(new JSONObject().put("command",command));}catch(Exception impossible){return false;}}
    static void setPresentation(String value){if(presentation.change(value)){note("view "+value+" #"+presentation.revision());syncPresentation();}}
    static void syncPresentation(){try{send(new JSONObject().put("command","presentation").put("mode",presentation.mode()).put("revision",presentation.revision()));}catch(Exception ignored){}}
    private static boolean youtube(String value){
        try{Uri u=Uri.parse(value);String h=u.getHost();return "https".equals(u.getScheme())&&(u.getPort()==-1||u.getPort()==443)
                &&("m.youtube.com".equals(h)||"www.youtube.com".equals(h)||"youtube.com".equals(h));}catch(Exception e){return false;}
    }
    static void connectController(WebExtension extension){
        controller=extension;
        browser.getWebExtensionController().setMessageDelegate(extension,new WebExtension.MessageDelegate(){
            @Override public void onConnect(WebExtension.Port incoming){
                if(incoming.sender.session!=browser||!incoming.sender.isTopLevel()||!"controls@flowview.local".equals(incoming.sender.webExtension.id)||!youtube(incoming.sender.url)){incoming.disconnect();return;}
                if(port!=null&&port!=incoming){port.setDelegate(null);port.disconnect();}
                port=incoming;note("bridge connected");
                incoming.setDelegate(new WebExtension.PortDelegate(){
                    @Override public void onPortMessage(Object value,WebExtension.Port from){
                        if(from!=port||!(value instanceof JSONObject))return;
                        JSONObject message=(JSONObject)value;String type=message.optString("type");
                        if("ready".equals(type)){
                            try{send(new JSONObject().put("command","settings").put("gestures",app.getSharedPreferences("flowview",MODE_PRIVATE).getBoolean("gestures",true)));}catch(Exception ignored){}
                            syncPresentation();if(pendingTransport!=null)command(pendingTransport);
                        }else if("media".equals(type))pageState(message.optBoolean("playing"));
                        else if("interaction".equals(type)){recovery.cancel();pendingTransport=null;}
                        else if("playResult".equals(type)){pendingTransport=null;note(message.optBoolean("ok")?"video play accepted":"video play failed: "+message.optString("error","unknown"));if(!message.optBoolean("ok")&&instance!=null)instance.publish();}
                        else if("position".equals(type)){
                            String url=message.optString("url");double time=message.optDouble("time",0);
                            if(youtube(url)&&Double.isFinite(time)&&time>=0&&time<7*86400)app.getSharedPreferences("flowview",MODE_PRIVATE).edit().putString("positionUrl",url).putLong("position",(long)time).apply();
                        }
                        Observer owner=observer.get();if(owner!=null)owner.onControllerMessage(message);
                    }
                    @Override public void onDisconnect(WebExtension.Port old){if(port==old){port=null;note("bridge disconnected");}}
                });
            }
        },"flowview");
    }
    private static void disconnectController(){
        if(browser!=null&&controller!=null)browser.getWebExtensionController().setMessageDelegate(controller,null,"flowview");
        if(port!=null){port.setDelegate(null);port.disconnect();port=null;}controller=null;pendingTransport=null;recovery.cancel();
    }
    static void cancelPipTransition(){recovery.cancel();}
    static void beginPipTransition(){
        int token=recovery.begin(SystemClock.elapsedRealtime(),playing);note("PiP transition, playing="+playing);
        for(long delay:new long[]{250,800,1600})handler.postDelayed(()->{
            if(recovery.takeAttempt(token,SystemClock.elapsedRealtime(),playing)){note("PiP resume");if(instance!=null)instance.playInternal();}
        },delay);
    }

    static GeckoSession retainedSession(){return browser!=null&&browser.isOpen()?browser:null;}
    static String retainedUrl(){return pageUrl;}
    static boolean canGoBack(){return canBack;}
    static boolean canGoForward(){return canForward;}
    static void setCanGoBack(boolean value){canBack=value;}
    static void setCanGoForward(boolean value){canForward=value;}
    static void attach(Context context,GeckoSession session,Observer callback){
        if(browser!=session){disconnectController();presentation.change("inline");playing=false;canBack=false;canForward=false;engine=null;position=PlaybackState.PLAYBACK_POSITION_UNKNOWN;duration=0;title="SatiMod";}
        app=context.getApplicationContext();browser=session;observer=new WeakReference<>(callback);
        session.setMediaSessionDelegate(new org.mozilla.geckoview.MediaSession.Delegate(){
            @Override public void onActivated(GeckoSession s,org.mozilla.geckoview.MediaSession media){engine=media;note("media activated");}
            @Override public void onDeactivated(GeckoSession s,org.mozilla.geckoview.MediaSession media){if(engine==media){engine=null;note("media deactivated");if(port==null)state(false);}}
            @Override public void onPlay(GeckoSession s,org.mozilla.geckoview.MediaSession media){engine=media;state(true);}
            @Override public void onPause(GeckoSession s,org.mozilla.geckoview.MediaSession media){state(false);}
            @Override public void onStop(GeckoSession s,org.mozilla.geckoview.MediaSession media){state(false);}
            @Override public void onMetadata(GeckoSession s,org.mozilla.geckoview.MediaSession media,org.mozilla.geckoview.MediaSession.Metadata metadata){setTitle(metadata.title);}
            @Override public void onPositionState(GeckoSession s,org.mozilla.geckoview.MediaSession media,org.mozilla.geckoview.MediaSession.PositionState value){
                position=Double.isFinite(value.position)&&value.position>=0?(long)(value.position*1000):PlaybackState.PLAYBACK_POSITION_UNKNOWN;
                duration=Double.isFinite(value.duration)&&value.duration>0?(long)(value.duration*1000):0;
                speed=Double.isFinite(value.playbackRate)&&value.playbackRate>0?(float)value.playbackRate:1f;
                if(instance!=null)instance.publish();
            }
        });
    }
    static void setTitle(String value){if(value!=null&&!value.trim().isEmpty()&&!value.equals(title)){title=value;if(instance!=null)instance.publish();}}
    static void setPageUrl(String value){if(value!=null)pageUrl=value;}
    static void setUiForeground(boolean value){uiForeground=value;if(value&&playing)ensureRunning();}
    static boolean isPlaying(){return playing;}
    static void pageState(boolean value){if((value&&"play".equals(pendingTransport))||(!value&&"pause".equals(pendingTransport)))pendingTransport=null;state(value);}
    private static void state(boolean value){
        boolean changed=playing!=value;playing=value;if(changed)note(value?"playing":"paused");
        if(browser!=null)browser.setPriorityHint(value?GeckoSession.PRIORITY_HIGH:GeckoSession.PRIORITY_DEFAULT);
        Observer o=observer.get();if(o!=null)o.onPlaybackChanged(value);
        if(value)ensureRunning();if(instance!=null&&changed)instance.publish();
    }
    private static void ensureRunning(){
        if(app==null||(!uiForeground&&instance==null)||starting||instance!=null)return;
        try{starting=true;app.startForegroundService(new Intent(app,PlaybackService.class));}
        catch(RuntimeException unavailable){starting=false;}
    }
    private static void stopRunning(){if(app!=null)app.stopService(new Intent(app,PlaybackService.class));starting=false;}
    static boolean detach(Observer owner){
        if(observer.get()==owner)observer.clear();
        if(browser==null)return false;
        if(instance!=null){
            browser.setProgressDelegate(null);browser.setPromptDelegate(null);
            browser.setPermissionDelegate(new GeckoSession.PermissionDelegate(){
                @Override public GeckoResult<Integer> onContentPermissionRequest(GeckoSession s,ContentPermission permission){
                    boolean autoplay=permission.permission==PERMISSION_AUTOPLAY_AUDIBLE||permission.permission==PERMISSION_AUTOPLAY_INAUDIBLE;
                    return GeckoResult.fromValue(autoplay?ContentPermission.VALUE_ALLOW:ContentPermission.VALUE_DENY);
                }
            });
            browser.setContentDelegate(new GeckoSession.ContentDelegate(){
                @Override public void onCrash(GeckoSession s){pause();stopRunning();}
                @Override public void onKill(GeckoSession s){pause();stopRunning();}
            });
            browser.setNavigationDelegate(new GeckoSession.NavigationDelegate(){
                @Override public void onLocationChange(GeckoSession s,String url,List<GeckoSession.PermissionDelegate.ContentPermission> p,Boolean gesture){setPageUrl(url);}
                @Override public void onCanGoBack(GeckoSession s,boolean value){setCanGoBack(value);}
                @Override public void onCanGoForward(GeckoSession s,boolean value){setCanGoForward(value);}
                @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s,LoadRequest r){return r.uri.startsWith("https://")?GeckoResult.allow():GeckoResult.deny();}
            });
            browser.setActive(false);return true;
        }
        pause();stopRunning();disconnectController();browser=null;engine=null;return false;
    }
    static void pause(){recovery.cancel();pendingTransport="pause";command("pause");if(engine!=null)engine.pause();state(false);}
    static void stop(){pause();stopRunning();}
    private void play(){recovery.cancel();resumeAfterFocus=false;playInternal();}
    private boolean acquireFocus(){
        if(hasFocus)return true;
        hasFocus=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if(!hasFocus)note("audio focus unavailable");return hasFocus;
    }
    private void playInternal(){
        if(!acquireFocus())return;
        pendingTransport="play";boolean sent=command("play");note("play command: "+(sent?"page":"engine"));
        if(!sent&&engine!=null&&engine.isActive())engine.play();
    }
    private void stopPlayback(){resumeAfterFocus=false;pause();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    private PendingIntent action(String name,int id){return PendingIntent.getService(this,id,new Intent(this,PlaybackService.class).setAction(name),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}

    @Override public void onCreate(){
        super.onCreate();instance=this;starting=false;
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"動画の再生",NotificationManager.IMPORTANCE_LOW));
        controls=new MediaSession(this,"SatiMod playback");
        controls.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){play();}
            @Override public void onPause(){resumeAfterFocus=false;pause();}
            @Override public void onStop(){stopPlayback();}
            @Override public void onSeekTo(long millis){if(engine!=null)engine.seekTo(millis/1000.0,false);}
            @Override public void onCustomAction(String action,android.os.Bundle extras){if(STOP.equals(action))stopPlayback();}
        });
        controls.setActive(true);
        audio=getSystemService(AudioManager.class);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change->{
                    if(change==AudioManager.AUDIOFOCUS_GAIN){hasFocus=true;if(resumeAfterFocus){resumeAfterFocus=false;play();}else if(!playing){audio.abandonAudioFocusRequest(focus);hasFocus=false;}}
                    else {hasFocus=false;resumeAfterFocus=playing&&(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);pause();}
                }).build();
        IntentFilter filter=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisy,filter);
    }
    private final BroadcastReceiver noisy=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){resumeAfterFocus=false;pause();}};
    private void publish(){
        if(controls==null)return;
        if(!playing&&hasFocus&&!resumeAfterFocus&&!"play".equals(pendingTransport)){audio.abandonAudioFocusRequest(focus);hasFocus=false;}
        controls.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).putString(MediaMetadata.METADATA_KEY_ARTIST,"SatiMod · YouTube").putLong(MediaMetadata.METADATA_KEY_DURATION,duration).build());
        controls.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SEEK_TO)
                .addCustomAction(STOP,"停止",android.R.drawable.ic_menu_close_clear_cancel)
                .setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,position,playing?speed:0f).build());
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        controls.setSessionActivity(open);
        Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_stat_play).setContentTitle(title)
                .setContentText(playing?"再生中":"一時停止中").setContentIntent(open).setOnlyAlertOnce(true).setOngoing(playing)
                .setCategory(Notification.CATEGORY_TRANSPORT).setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing?"一時停止":"再生",action(playing?PAUSE:PLAY,1)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"停止",action(STOP,2)).build())
                .setDeleteIntent(action(STOP,3)).setStyle(new Notification.MediaStyle().setMediaSession(controls.getSessionToken()).setShowActionsInCompactView(0,1)).build();
        startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        // Android 15+ allows focus requests only from the foreground or a foreground service.
        if(playing&&!hasFocus&&!acquireFocus())pause();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(browser==null){stopSelf();return START_NOT_STICKY;}
        String action=intent==null?null:intent.getAction();
        if(STOP.equals(action)){stopPlayback();return START_NOT_STICKY;}
        if(PAUSE.equals(action)){resumeAfterFocus=false;pause();}else if(PLAY.equals(action))play();
        publish();return START_NOT_STICKY;
    }
    @Override public void onTaskRemoved(Intent rootIntent){stopPlayback();}
    @Override public void onDestroy(){
        instance=null;starting=false;
        if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);
        unregisterReceiver(noisy);if(controls!=null){controls.setActive(false);controls.release();}
        if(observer.get()==null&&browser!=null){GeckoSession old=browser;disconnectController();browser=null;engine=null;playing=false;old.close();}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
