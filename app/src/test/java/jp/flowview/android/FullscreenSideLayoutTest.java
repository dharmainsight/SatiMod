package jp.flowview.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class FullscreenSideLayoutTest {
    private FullscreenSideLayout.Slot slot(int w,int h,int leftMargin,int rightMargin,int left,int top,int right,int bottom){
        return FullscreenSideLayout.toggleSlot(w,h,leftMargin,rightMargin,left,top,right,bottom,48,80,48,4);
    }
    @Test public void rightGutterPreferredAndCutoutFallsBackToLeft(){
        var s=slot(900,400,94,94,30,24,0,0);
        assertNotNull(s);assertTrue(s.x>=806&&s.x+s.width<=900&&s.y>=24);
        s=slot(900,400,94,94,0,24,80,0);
        assertNotNull(s);assertTrue(s.x>=0&&s.x+s.width<=94);
    }
    @Test public void noRoomHidesOnlySwitch(){
        assertNull(slot(900,400,0,0,0,24,0,0));
        assertNull(slot(900,400,94,94,80,24,80,0));
        assertNull(slot(900,60,94,94,0,24,0,0));
        assertNull(slot(900,400,500,500,0,24,0,0));
    }
    @Test public void measuredPixelGuttersAndCutout(){
        var s=FullscreenSideLayout.toggleSlot(1536,684,160,159,80,54,0,0,100,150,100,8);
        assertNotNull(s);assertTrue(s.x>=1377&&s.x+s.width<=1536&&s.y>=54);
        s=FullscreenSideLayout.toggleSlot(1536,684,160,159,0,54,80,0,100,150,100,8);
        assertNotNull(s);assertTrue(s.x+s.width<=160&&s.y>=54);
    }
    @Test public void geometrySweepNeverCoversVideoOrCutout(){
        int count=0;
        for(int w=100;w<1500;w+=13)for(int h=50;h<800;h+=17)for(int vw=1;vw<25;vw+=3){
            int margin=(int)Math.floor(Math.max(0,(w-h*vw/9.0)/2));
            var s=slot(w,h,margin,margin,25,24,40,0);
            if(s!=null){
                assertTrue(s.width>=48&&s.width<=80&&s.x>=25&&s.x+s.width<=w-40&&s.y>=24&&s.y+s.height<=h);
                assertTrue(s.x+s.width<=margin||s.x>=w-margin);
            }
            count++;
        }
        assertTrue(count>10000);
    }
}
