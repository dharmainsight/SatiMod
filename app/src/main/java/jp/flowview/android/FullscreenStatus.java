package jp.flowview.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

/** SystemUI remains at portrait top, with the switch on the opposite landscape gutter. */
final class FullscreenStatus {
    private static final String KEY="fullscreenSideStatus";
    private final Activity activity;
    private final FrameLayout root;
    private final FullscreenRotationLayout rotation;
    private final View web;
    private final SharedPreferences prefs;
    private final FlowUi ui;
    private final Button toggle;
    private double viewportWidth,viewportHeight,videoLeft,videoRight=1;
    private String lastResult="not entered";
    private boolean active,statusFits;
    private Boolean requestedBar;

    FullscreenStatus(Activity activity,FrameLayout root,View web,SharedPreferences prefs,FlowUi ui,FullscreenRotationLayout rotation){
        this.activity=activity;this.root=root;this.web=web;this.prefs=prefs;this.ui=ui;this.rotation=rotation;
        toggle=ui.button("",null,this::toggle,false);
        toggle.setTextSize(12);toggle.setPadding(0,0,0,0);toggle.setVisibility(View.GONE);
        root.addView(toggle,new FrameLayout.LayoutParams(0,0));
        updateToggleLabel();
    }

    @SuppressWarnings("deprecation")
    static void configureEdgeToEdge(Window window){
        window.setDecorFitsSystemWindows(false);
        // The visible status bar must not change the window bounds at a display cutout.
        WindowManager.LayoutParams attrs=window.getAttributes();
        attrs.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        window.setAttributes(attrs);
        // Android 15+ enforces a transparent status bar for our target SDK.
        if(Build.VERSION.SDK_INT<35)window.setStatusBarColor(Color.TRANSPARENT);
    }

    static void applyBars(WindowInsetsController controller,boolean fullscreen,boolean statusEnabled){
        if(controller==null)return;
        controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if(!fullscreen){controller.show(WindowInsets.Type.systemBars());return;}
        controller.hide(WindowInsets.Type.navigationBars());
        if(statusEnabled)controller.show(WindowInsets.Type.statusBars());
        else controller.hide(WindowInsets.Type.statusBars());
    }

    static void applyContentInsets(View root,WindowInsets insets,boolean videoOnly){
        // Fullscreen and PiP always have the same zero padding, even when bars appear.
        Insets padding=Insets.NONE;
        if(!videoOnly){
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            Insets ime=insets.getInsets(WindowInsets.Type.ime());
            padding=Insets.of(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));
        }
        if(root.getPaddingLeft()!=padding.left||root.getPaddingTop()!=padding.top
                ||root.getPaddingRight()!=padding.right||root.getPaddingBottom()!=padding.bottom)
            root.setPadding(padding.left,padding.top,padding.right,padding.bottom);
    }

    private boolean enabled(){return prefs.getBoolean(KEY,true);}
    String menuTitle(){return "横画面の通知バー："+(enabled()?"ON → OFF":"OFF → ON");}
    void applySystemBars(boolean fullscreen){
        boolean visible=active&&rotation.turn()!=0&&statusFits&&enabled();
        applyBars(activity.getWindow().getInsetsController(),fullscreen,visible);
        requestedBar=fullscreen?visible:null;
    }
    private void updateBar(boolean fits){
        statusFits=fits;
        if(!active)return;
        boolean visible=enabled()&&fits;
        WindowInsetsController bars=activity.getWindow().getInsetsController();
        if(bars!=null&&(requestedBar==null||requestedBar!=visible)){
            requestedBar=visible;
            if(visible)bars.show(WindowInsets.Type.statusBars());
            else bars.hide(WindowInsets.Type.statusBars());
        }
    }
    void toggle(){
        prefs.edit().putBoolean(KEY,!enabled()).apply();updateToggleLabel();
        // The switch never changes the viewport, rotation, video node or playback.
        update();
    }
    private void updateToggleLabel(){
        toggle.setText(enabled()?"隠す":"表示");
        String description=enabled()?"通知バーを隠す":"通知バーを表示する";
        toggle.setContentDescription(description);toggle.setTooltipText(description);
    }
    void setActive(boolean value){active=value;if(!value){statusFits=false;requestedBar=null;}update();}
    void clearVideo(){setVideoBounds(0,0,0,1);}
    void setVideoBounds(double width,double height,double left,double right){
        if(!Double.isFinite(width)||!Double.isFinite(height)||!Double.isFinite(left)||!Double.isFinite(right)
                ||width<=0||height<=0||width>100000||height>100000||left<0||right>1||right<=left){
            width=0;height=0;left=0;right=1;
        }
        viewportWidth=width;viewportHeight=height;videoLeft=left;videoRight=right;update();
    }
    String diagnostics(){return "fullscreen native status="+lastResult+" enabled="+enabled();}
    void update(){update(rotation.getRootWindowInsets());}
    void update(WindowInsets insets){
        if(!active){hide();return;}
        int width=web.getWidth(),height=web.getHeight(),turn=rotation.turn();
        if(turn==0||insets==null||width<=0||height<=0||width!=root.getWidth()||height!=root.getHeight()
                ||web.getLeft()!=0||web.getTop()!=0||root.getPaddingLeft()!=0||root.getPaddingTop()!=0
                ||root.getPaddingRight()!=0||root.getPaddingBottom()!=0){
            lastResult="waiting for rotated full layout "+width+"x"+height+" turn="+turn;updateBar(false);hide();return;
        }
        String bar="barVisible="+insets.isVisible(WindowInsets.Type.statusBars())+" ";
        if(viewportWidth<=0||viewportHeight<=0){
            lastResult=bar+"no geometry from controller";updateBar(false);hide();return;
        }
        if(Math.abs((viewportWidth/viewportHeight)/(width/(double)height)-1)>.05){
            lastResult=bar+"viewport/native aspect mismatch";updateBar(false);hide();return;
        }
        int leftMargin=(int)Math.floor(videoLeft*width),rightMargin=(int)Math.floor((1-videoRight)*width);
        Insets physical=insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout()|WindowInsets.Type.statusBars());
        Insets safe=rotation.logicalInsets(physical);
        boolean fits=FullscreenSideLayout.statusFits(turn,leftMargin,rightMargin,physical.top,ui.dp(2));
        FullscreenSideLayout.Slot slot=FullscreenSideLayout.toggleSlot(turn,width,height,leftMargin,rightMargin,
                safe.left,safe.top,safe.right,safe.bottom,ui.dp(48),ui.dp(80),ui.dp(48),ui.dp(4));
        lastResult=bar+"turn="+turn+" gutters="+leftMargin+"/"+rightMargin+" statusExtent="+physical.top
                +" fits="+fits+" toggle="+(slot!=null);
        updateBar(fits);place(slot);
    }
    private void hide(){toggle.setVisibility(View.GONE);}
    @SuppressLint("RtlHardcoded") // The slots use physical display coordinates.
    private void place(FullscreenSideLayout.Slot slot){
        if(slot==null){hide();return;}
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)toggle.getLayoutParams();
        if(lp.width!=slot.width||lp.height!=slot.height||lp.leftMargin!=slot.x||lp.topMargin!=slot.y){
            lp=new FrameLayout.LayoutParams(slot.width,slot.height,Gravity.TOP|Gravity.LEFT);
            lp.leftMargin=slot.x;lp.topMargin=slot.y;toggle.setLayoutParams(lp);
        }
        toggle.setVisibility(View.VISIBLE);
    }
    boolean toggleHit(float x,float y){
        if(toggle.getVisibility()!=View.VISIBLE)return false;
        return x>=toggle.getLeft()&&x<toggle.getRight()&&y>=toggle.getTop()&&y<toggle.getBottom();
    }
}
