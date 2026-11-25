package jp.flowview.android;

import android.content.Context;
import android.graphics.Insets;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A landscape content viewport inside a portrait Android window.
 * The window's real status bar stays at the physical portrait top.
 * Android's ViewGroup applies the inverse child transform when dispatching touch events.
 */
final class FullscreenRotationLayout extends FrameLayout {
    private final View content;
    private final int[] location=new int[2];
    private int requestedTurn,appliedTurn;
    private Runnable layoutCallback;

    FullscreenRotationLayout(Context context,View content){
        super(context);
        this.content=content;
        setClipChildren(false);setClipToPadding(false);
        addView(content,new FrameLayout.LayoutParams(-1,-1));
    }
    void setLayoutCallback(Runnable callback){layoutCallback=callback;}
    void setTurn(int degrees){
        int next=degrees==90||degrees==-90?degrees:0;
        if(next==requestedTurn)return;
        requestedTurn=next;requestLayout();
    }
    int turn(){return appliedTurn;}

    // Keep the last landscape direction when upright, upside-down or flat.
    static int sensorTurn(int orientation,int previous){
        if(orientation>=60&&orientation<=120)return -90;
        if(orientation>=240&&orientation<=300)return 90;
        return previous;
    }
    static int effectiveTurn(int requested,int width,int height){
        return width>0&&height>width?requested:0;
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);
        int turn=effectiveTurn(requestedTurn,w,h);
        int cw=turn==0?w:h,ch=turn==0?h:w;
        content.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch,MeasureSpec.EXACTLY));
        setMeasuredDimension(w,h);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int w=r-l,h=b-t;
        int next=effectiveTurn(requestedTurn,w,h);
        boolean changedTurn=next!=appliedTurn;
        appliedTurn=next;
        content.setPivotX(0);content.setPivotY(0);
        content.setRotation(next);
        content.setTranslationX(next==90?w:0);
        content.setTranslationY(next==-90?h:0);
        content.layout(0,0,next==0?w:h,next==0?h:w);
        if((changed||changedTurn)&&layoutCallback!=null)post(layoutCallback);
    }
    static void physicalToLogical(int turn,int width,int height,float x,float y,float[] out){
        if(turn==90){out[0]=y;out[1]=width-x;}
        else if(turn==-90){out[0]=height-y;out[1]=x;}
        else{out[0]=x;out[1]=y;}
    }
    void windowToContent(float x,float y,float[] out){
        getLocationInWindow(location);
        physicalToLogical(appliedTurn,getWidth(),getHeight(),x-location[0],y-location[1],out);
    }
    static Insets logicalInsets(int turn,Insets insets){
        if(turn==90)return Insets.of(insets.top,insets.right,insets.bottom,insets.left);
        if(turn==-90)return Insets.of(insets.bottom,insets.left,insets.top,insets.right);
        return insets;
    }
    Insets logicalInsets(Insets insets){return logicalInsets(appliedTurn,insets);}
}
