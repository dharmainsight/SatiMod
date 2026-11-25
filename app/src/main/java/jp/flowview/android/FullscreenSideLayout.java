package jp.flowview.android;

/** Slots inside the pillarbox of a centered, contain-fitted video; never resize it. */
final class FullscreenSideLayout {
    static final class Slot {
        final int x,y,width,height;
        Slot(int x,int y,int width,int height){this.x=x;this.y=y;this.width=width;this.height=height;}
    }
    static final class Plan {
        final Slot status,toggle;
        Plan(Slot status,Slot toggle){this.status=status;this.toggle=toggle;}
    }
    static Plan plan(int width,int height,int videoWidth,int videoHeight,
                     int left,int top,int right,int bottom,int minWidth,int maxWidth,
                     int statusHeight,int toggleHeight,int gap){
        if(width<=0||height<=0||videoWidth<=0||videoHeight<=0)return new Plan(null,null);
        int margin=(int)Math.floor(Math.max(0,(width-height*(double)videoWidth/videoHeight)/2));
        return planGutters(width,height,margin,margin,left,top,right,bottom,minWidth,maxWidth,statusHeight,toggleHeight,gap);
    }
    static Plan planGutters(int width,int height,int leftMargin,int rightMargin,
                     int left,int top,int right,int bottom,int minWidth,int maxWidth,
                     int statusHeight,int toggleHeight,int gap){
        if(width<=0||height<=0||leftMargin<0||rightMargin<0||(long)leftMargin+rightMargin>width)return new Plan(null,null);
        int leftSpace=leftMargin-left-gap,rightSpace=rightMargin-right-gap,usableHeight=height-top-bottom;
        boolean l=leftSpace>=minWidth,r=rightSpace>=minWidth;
        if((!l&&!r)||usableHeight<toggleHeight)return new Plan(null,null);
        boolean toggleLeft=!r,statusLeft=l;
        int toggleY=top+(usableHeight-toggleHeight)/2,statusY=top+(usableHeight-statusHeight)/2;
        boolean showStatus=usableHeight>=statusHeight;
        if(toggleLeft==statusLeft){
            showStatus=usableHeight>=statusHeight+toggleHeight+gap;
            if(showStatus){statusY=top+(usableHeight-statusHeight-toggleHeight-gap)/2;toggleY=statusY+statusHeight+gap;}
        }
        Slot toggle=slot(toggleLeft,toggleY,toggleHeight,width,toggleLeft?leftMargin:rightMargin,left,right,gap,maxWidth);
        Slot status=showStatus?slot(statusLeft,statusY,statusHeight,width,statusLeft?leftMargin:rightMargin,left,right,gap,maxWidth):null;
        return new Plan(status,toggle);
    }
    private static Slot slot(boolean leftSide,int y,int h,int width,int margin,int left,int right,int gap,int maxWidth){
        int available=margin-(leftSide?left:right)-gap,w=Math.min(maxWidth,available);
        int x=(leftSide?left:width-margin+gap)+(available-w)/2;
        return new Slot(x,y,w,h);
    }
}
