package jp.flowview.android;

/** Place only the switch in a measured video gutter. Never reserve video space. */
final class FullscreenSideLayout {
    static final class Slot {
        final int x,y,width,height;
        Slot(int x,int y,int width,int height){this.x=x;this.y=y;this.width=width;this.height=height;}
    }
    static Slot toggleSlot(int width,int height,int leftMargin,int rightMargin,
                           int left,int top,int right,int bottom,int minWidth,int maxWidth,
                           int toggleHeight,int gap){
        if(width<=0||height<=0||leftMargin<0||rightMargin<0||(long)leftMargin+rightMargin>width)return null;
        int leftSpace=leftMargin-left-gap,rightSpace=rightMargin-right-gap,usableHeight=height-top-bottom;
        if(usableHeight<toggleHeight)return null;
        // Prefer the right gutter; use the left one when the right camera cutout leaves too little room.
        boolean leftSide=rightSpace<minWidth;
        int available=leftSide?leftSpace:rightSpace;
        if(available<minWidth)return null;
        int w=Math.min(maxWidth,available);
        int x=(leftSide?left:width-rightMargin+gap)+(available-w)/2;
        return new Slot(x,top+(usableHeight-toggleHeight)/2,w,toggleHeight);
    }
}
