package jp.flowview.android;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** A consistent 24-unit icon family; never depends on the installed emoji font. */
final class FlowIcon extends Drawable {
    private final String name;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int size;
    FlowIcon(String name,int color,int size){this.name=name;this.size=size;p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
    private void line(Canvas c,float...xy){Path path=new Path();path.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);c.drawPath(path,p);}
    @Override public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);
        switch(name){
            case "back":line(c,14,5,7,12,14,19);break;
            case "forward":line(c,10,5,17,12,10,19);break;
            case "refresh":c.drawArc(4,4,20,20,45,280,false,p);line(c,20,4,20,10,14,10);break;
            case "search":c.drawCircle(10.5f,10.5f,6.5f,p);line(c,16,16,21,21);break;
            case "close":line(c,6,6,18,18);line(c,18,6,6,18);break;
            case "plus":line(c,5,12,19,12);line(c,12,5,12,19);break;
            case "minus":line(c,5,12,19,12);break;
            case "music":line(c,9,17,9,5,20,3,20,15);c.drawOval(3,15,9,20,p);c.drawOval(14,13,20,18,p);line(c,9,9,20,7);break;
            case "extensions":line(c,4,10,4,4,10,4);c.drawArc(10,1,16,7,180,180,false,p);line(c,16,4,20,4,20,10);c.drawArc(17,10,23,16,270,180,false,p);line(c,20,16,20,20,14,20);c.drawArc(8,17,14,23,0,-180,false,p);line(c,8,20,4,20,4,16);c.drawArc(1,10,7,16,90,-180,false,p);break;
            case "more":p.setStyle(Paint.Style.FILL);for(int x:new int[]{5,12,19})c.drawCircle(x,12,1.6f,p);p.setStyle(Paint.Style.STROKE);break;
            case "play":line(c,8,4,20,12,8,20,8,4);break;
            case "pause":line(c,8,5,8,19);line(c,16,5,16,19);break;
            case "full":line(c,9,4,4,4,4,9);line(c,15,4,20,4,20,9);line(c,20,15,20,20,15,20);line(c,9,20,4,20,4,15);break;
            case "pip":c.drawRoundRect(3,5,21,19,2,2,p);c.drawRoundRect(12,11,19,17,1,1,p);break;
            case "home":line(c,3,11,12,3,21,11);line(c,6,9,6,21,10,21,10,15,14,15,14,21,18,21,18,9);break;
            case "check":line(c,4,12,9,17,20,6);break;
            default:c.drawCircle(12,12,8,p);
        }c.restore();
    }
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
}
