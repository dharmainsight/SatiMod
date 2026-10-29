package jp.flowview.android;

/** Tracks a deliberate, single-finger downward swipe in density-independent pixels. */
final class FullscreenSwipe {
    private boolean tracking;
    private float x,y;
    private long start;
    void start(float x,float y,long time){this.x=x;this.y=y;start=time;tracking=true;}
    void cancel(){tracking=false;}
    boolean move(float x,float y,long time){
        if(!tracking)return false;
        float dx=Math.abs(x-this.x),dy=y-this.y;long elapsed=time-start;
        if(elapsed>1500){cancel();return false;}
        if(elapsed>=30&&dy>=80&&dy>dx*1.6f){cancel();return true;}
        return false;
    }
}
