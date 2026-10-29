package jp.flowview.android;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

final class FlowUi {
    static final int BG=Color.rgb(11,17,19),SURFACE=Color.rgb(19,29,31),CARD=Color.rgb(28,40,42),LINE=Color.rgb(49,66,64),TEXT=Color.rgb(237,245,240),MUTED=Color.rgb(158,179,174),MINT=Color.rgb(164,244,205),INK=Color.rgb(7,59,40),LILAC=Color.rgb(212,195,255);
    final Activity a;FlowUi(Activity a){this.a=a;}
    int dp(float value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
    TextView text(String value,int size,int color){TextView v=new TextView(a);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);v.setLineSpacing(dp(3),1);return v;}
    TextView title(String value,int size){TextView v=text(value,size,TEXT);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    LinearLayout column(){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(a);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    LinearLayout card(){LinearLayout v=column();v.setPadding(dp(18),dp(18),dp(18),dp(18));v.setBackground(shape(CARD,24));return v;}
    void gap(LinearLayout box,int height){box.addView(new View(a),new LinearLayout.LayoutParams(1,dp(height)));}
    MaterialButton button(String label,String icon,Runnable action,boolean filled){
        MaterialButton b=new MaterialButton(a);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setMaxLines(1);b.setCornerRadius(dp(18));b.setInsetTop(0);b.setInsetBottom(0);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(dp(48));b.setMinimumWidth(dp(48));b.setPadding(dp(12),0,dp(12),0);
        int[][] states={new int[]{-android.R.attr.state_enabled},new int[]{}};
        b.setBackgroundTintList(new ColorStateList(states,new int[]{SURFACE,filled?MINT:CARD}));b.setTextColor(new ColorStateList(states,new int[]{0x66788D86,filled?INK:TEXT}));b.setRippleColor(ColorStateList.valueOf(0x3378E1BE));
        if(icon!=null){b.setIcon(new FlowIcon(icon,filled?INK:TEXT,dp(22)));b.setIconTint(new ColorStateList(states,new int[]{0x66788D86,filled?INK:TEXT}));b.setIconSize(dp(22));b.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);b.setIconPadding(dp(label.isEmpty()?0:8));}
        b.setOnClickListener(v->action.run());return b;
    }
    MaterialButton icon(String icon,String description,Runnable action,boolean filled){MaterialButton b=button("",icon,action,filled);b.setContentDescription(description);b.setTooltipText(description);b.setPadding(dp(12),0,dp(12),0);return b;}
    MaterialButton nav(String icon,String label,Runnable action,boolean filled){MaterialButton b=button(label,icon,action,filled);b.setIconGravity(MaterialButton.ICON_GRAVITY_TOP);b.setIconPadding(dp(3));b.setPadding(dp(4),dp(6),dp(4),dp(5));b.setTextSize(11);b.setCornerRadius(dp(20));if(!filled)b.setBackgroundTintList(ColorStateList.valueOf(SURFACE));return b;}
    static final class Sheet {final BottomSheetDialog dialog;final LinearLayout body;Sheet(BottomSheetDialog d,LinearLayout b){dialog=d;body=b;}}
    Sheet sheet(String title,String subtitle,int height){
        BottomSheetDialog d=new BottomSheetDialog(a);LinearLayout box=column();box.setBackground(shape(SURFACE,30));box.setPadding(dp(20),dp(12),dp(20),dp(12));
        View handle=new View(a);handle.setBackground(shape(LINE,4));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(34),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(18);box.addView(handle,hp);
        LinearLayout heading=row(),words=column();words.addView(title(title,26));if(!subtitle.isEmpty()){gap(words,7);words.addView(text(subtitle,12,MUTED));}
        heading.addView(words,new LinearLayout.LayoutParams(0,-2,1));heading.addView(icon("close","閉じる",d::dismiss,false),new LinearLayout.LayoutParams(dp(48),dp(48)));box.addView(heading);gap(box,18);
        LinearLayout content=column();box.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        int h=Math.min(dp(height),a.getResources().getDisplayMetrics().heightPixels-dp(56));
        d.setContentView(box,new android.view.ViewGroup.LayoutParams(-1,h));d.getBehavior().setSkipCollapsed(true);d.getBehavior().setMaxWidth(dp(680));
        d.setOnShowListener(x->{d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);if(d.getWindow()!=null)d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);});
        return new Sheet(d,content);
    }
}
