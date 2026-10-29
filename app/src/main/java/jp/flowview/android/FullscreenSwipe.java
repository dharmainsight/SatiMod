package jp.flowview.android;

/** A drag previews on MOVE and commits only on UP. All distances are in dp. */
final class FullscreenSwipe {
    private boolean tracking,dragging;
    private float x,y,distance;
    private long start;
    void start(float x,float y,long time,float topEdge){
        cancel();this.x=x;this.y=y;start=time;tracking=y>topEdge;
    }
    void cancel(){tracking=false;dragging=false;distance=0;}
    float distance(){return distance;}
    float progress(){return Math.min(1f,distance/80f);}
    boolean move(float x,float y,long time){
        if(!tracking)return false;
        float dx=Math.abs(x-this.x),dy=y-this.y;
        if(!dragging&&(dy< -12||(dx>12&&dx*1.6f>=dy))){cancel();return false;}
        if(dy>12&&dy>dx*1.6f)dragging=true;
        distance=dy>dx*1.6f?Math.max(0,dy):0;
        return dragging;
    }
    boolean release(float x,float y,long time){
        move(x,y,time);
        boolean commit=tracking&&dragging&&distance>=80&&time-start>=30;
        cancel();return commit;
    }
}
