package jp.flowview.android;

/** Physical portrait top becomes one landscape gutter; the switch stays on the other. */
final class FullscreenSideLayout {
    static final class Slot {
        final int x,y,width,height;
        Slot(int x,int y,int width,int height){this.x=x;this.y=y;this.width=width;this.height=height;}
    }
    static boolean statusFits(int turn,int leftMargin,int rightMargin,int physicalTop,int gap){
        if((turn!=90&&turn!=-90)||physicalTop<=0||gap<0)return false;
        int margin=turn==90?leftMargin:rightMargin;
        return (long)margin>=(long)physicalTop+gap;
    }
    static Slot toggleSlot(int turn,int width,int height,int leftMargin,int rightMargin,
                           int left,int top,int right,int bottom,int minWidth,int maxWidth,
                           int toggleHeight,int gap){
        if((turn!=90&&turn!=-90)||width<=0||height<=0||leftMargin<0||rightMargin<0||(long)leftMargin+rightMargin>width)return null;
        boolean leftSide=turn==-90; // Never put the switch on the same side as SystemUI.
        int available=leftSide?leftMargin-left-gap:rightMargin-right-gap;
        int usableHeight=height-top-bottom;
        if(available<minWidth||usableHeight<toggleHeight)return null;
        int w=Math.min(maxWidth,available);
        int x=(leftSide?left:width-rightMargin+gap)+(available-w)/2;
        return new Slot(x,top+(usableHeight-toggleHeight)/2,w,toggleHeight);
    }
}
