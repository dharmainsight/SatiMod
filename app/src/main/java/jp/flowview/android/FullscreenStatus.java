package jp.flowview.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.Date;

/** Native status summary in unused video gutters, not a relocated Android system bar. */
final class FullscreenStatus {
    private static final String KEY="fullscreenSideStatus";
    private final Activity activity;
    private final FrameLayout root;
    private final View web;
    private final SharedPreferences prefs;
    private final FlowUi ui;
    private final TextView status;
    private final Button toggle;
    private double viewportWidth,viewportHeight,videoLeft,videoRight=1;
    private String lastResult="not entered";
    private boolean active;
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(status.getVisibility()!=View.VISIBLE)return;
            refresh();
            root.postDelayed(this,5000);
        }
    };

    FullscreenStatus(Activity activity,FrameLayout root,View web,SharedPreferences prefs,FlowUi ui){
        this.activity=activity;this.root=root;this.web=web;this.prefs=prefs;this.ui=ui;
        status=ui.text("",12,FlowUi.TEXT);
        status.setGravity(Gravity.CENTER);status.setMaxLines(5);
        status.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        status.setVisibility(View.GONE);root.addView(status,new FrameLayout.LayoutParams(0,0));
        toggle=ui.button("",null,this::toggle,false);
        toggle.setTextSize(12);toggle.setPadding(0,0,0,0);toggle.setVisibility(View.GONE);
        root.addView(toggle,new FrameLayout.LayoutParams(0,0));
        updateToggleLabel();
    }
    private boolean enabled(){return prefs.getBoolean(KEY,true);}
    String menuTitle(){return "横画面ステータス："+(enabled()?"ON → OFF":"OFF → ON");}
    void toggle(){prefs.edit().putBoolean(KEY,!enabled()).apply();updateToggleLabel();update();}
    private void updateToggleLabel(){
        toggle.setText(enabled()?"隠す":"表示");
        toggle.setContentDescription(enabled()?"ステータス表示を隠す":"ステータスを表示する");
    }
    void setActive(boolean value){active=value;update();}
    void clearVideo(){setVideoBounds(0,0,0,1);}
    void setVideoBounds(double width,double height,double left,double right){
        if(!Double.isFinite(width)||!Double.isFinite(height)||!Double.isFinite(left)||!Double.isFinite(right)
                ||width<=0||height<=0||width>100000||height>100000||left<0||right>1||right<=left){
            width=0;height=0;left=0;right=1;
        }
        viewportWidth=width;viewportHeight=height;videoLeft=left;videoRight=right;update();
    }
    String diagnostics(){return "fullscreen status="+lastResult+" enabled="+enabled();}
    void update(){
        WindowInsets insets=root.getRootWindowInsets();
        // The video-only column must actually occupy the whole root before drawing.
        if(!active){hide();return;}
        int width=web.getWidth(),height=web.getHeight();
        if(insets==null||width<=0||height<=0||width!=root.getWidth()||height!=root.getHeight()
                ||web.getLeft()!=0||web.getTop()!=0||root.getPaddingLeft()!=0||root.getPaddingTop()!=0
                ||root.getPaddingRight()!=0||root.getPaddingBottom()!=0){
            lastResult="waiting for full native layout "+width+"x"+height+"/"+root.getWidth()+"x"+root.getHeight();hide();return;
        }
        if(viewportWidth<=0||viewportHeight<=0){
            lastResult="no geometry from controller";hide();return;
        }
        if(Math.abs((viewportWidth/viewportHeight)/(width/(double)height)-1)>.05){
            lastResult="viewport/native aspect mismatch";hide();return;
        }
        int leftMargin=(int)Math.floor(videoLeft*width),rightMargin=(int)Math.floor((1-videoRight)*width);
        Insets safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout());
        FullscreenSideLayout.Plan plan=FullscreenSideLayout.planGutters(width,height,leftMargin,rightMargin,
                safe.left,safe.top,safe.right,safe.bottom,ui.dp(48),ui.dp(80),ui.dp(96),ui.dp(48),ui.dp(4));
        lastResult="gutters="+leftMargin+"/"+rightMargin+" cutout="+safe.left+"/"+safe.right
                +" status="+(plan.status!=null)+" toggle="+(plan.toggle!=null);
        boolean wasVisible=status.getVisibility()==View.VISIBLE;
        place(toggle,plan.toggle);
        place(status,enabled()?plan.status:null);
        boolean visible=status.getVisibility()==View.VISIBLE;
        if(!visible)root.removeCallbacks(tick);
        else if(!wasVisible){refresh();root.removeCallbacks(tick);root.postDelayed(tick,5000);}
    }
    private void hide(){
        status.setVisibility(View.GONE);toggle.setVisibility(View.GONE);root.removeCallbacks(tick);
    }
    @SuppressLint("RtlHardcoded") // The slots use physical display coordinates.
    private void place(View view,FullscreenSideLayout.Slot slot){
        if(slot==null){view.setVisibility(View.GONE);return;}
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)view.getLayoutParams();
        if(lp.width!=slot.width||lp.height!=slot.height||lp.leftMargin!=slot.x||lp.topMargin!=slot.y){
            lp=new FrameLayout.LayoutParams(slot.width,slot.height,Gravity.TOP|Gravity.LEFT);
            lp.leftMargin=slot.x;lp.topMargin=slot.y;view.setLayoutParams(lp);
        }
        view.setVisibility(View.VISIBLE);
    }
    boolean toggleHit(float x,float y){
        if(toggle.getVisibility()!=View.VISIBLE)return false;
        int[] position=new int[2];toggle.getLocationInWindow(position);
        return x>=position[0]&&x<position[0]+toggle.getWidth()&&y>=position[1]&&y<position[1]+toggle.getHeight();
    }
    private void refresh(){
        String time=DateFormat.getTimeFormat(activity).format(new Date());
        BatteryManager battery=activity.getSystemService(BatteryManager.class);
        int level=battery==null?-1:battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        String charge=level>=0&&level<=100?level+"%":"—";
        String network=networkLabel();
        status.setText(time+"\n"+charge+"\n"+network);
        status.setContentDescription("時刻 "+time+"、電池残量 "+charge+"、通信 "+network);
    }
    private String networkLabel(){
        ConnectivityManager manager=activity.getSystemService(ConnectivityManager.class);
        if(manager==null)return "不明";
        Network network=manager.getActiveNetwork();
        NetworkCapabilities caps=network==null?null:manager.getNetworkCapabilities(network);
        if(caps==null)return "未接続";
        if(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return "VPN";
        if(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return "Wi-Fi";
        if(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))return "モバイル";
        if(caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))return "有線";
        return "接続中";
    }
}
