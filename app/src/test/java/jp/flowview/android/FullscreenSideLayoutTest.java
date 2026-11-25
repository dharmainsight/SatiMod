package jp.flowview.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class FullscreenSideLayoutTest {
    private FullscreenSideLayout.Slot slot(int turn,int w,int h,int lm,int rm,int left,int top,int right,int bottom){
        return FullscreenSideLayout.toggleSlot(turn,w,h,lm,rm,left,top,right,bottom,48,80,48,4);
    }
    @Test public void measuredPixelBarsAndToggleAreOpposite(){
        for(int turn:new int[]{90,-90}){
            assertTrue(FullscreenSideLayout.statusFits(turn,160,159,54,4));
            var s=FullscreenSideLayout.toggleSlot(turn,1536,684,160,159,
                    turn==90?54:0,0,turn==-90?54:0,0,100,150,100,8);
            assertNotNull(s);
            if(turn==90)assertTrue(s.x>=1377&&s.x+s.width<=1536);
            else assertTrue(s.x>=0&&s.x+s.width<=160);
        }
    }
    @Test public void noRoomHidesBarOrSwitchIndependently(){
        assertFalse(FullscreenSideLayout.statusFits(90,50,160,54,4));
        assertTrue(FullscreenSideLayout.statusFits(-90,50,160,54,4));
        assertFalse(FullscreenSideLayout.statusFits(0,160,160,54,4));
        assertFalse(FullscreenSideLayout.statusFits(90,160,160,0,4));
        assertNull(slot(90,900,400,100,0,24,0,0,0));
        assertNull(slot(-90,900,400,0,100,0,0,24,0));
        assertNull(slot(90,900,400,94,94,0,0,80,0));
        assertNull(slot(90,900,60,94,94,0,24,0,0));
        assertNull(slot(90,900,400,500,500,0,0,0,0));
    }
    @Test public void geometrySweepNeverCoversVideoOrCutout(){
        for(int turn:new int[]{90,-90})for(int w=100;w<1500;w+=13)for(int h=50;h<800;h+=17)for(int vw=1;vw<25;vw+=3){
            int margin=(int)Math.floor(Math.max(0,(w-h*vw/9.0)/2));
            var s=slot(turn,w,h,margin,margin,25,24,40,0);
            if(s!=null){
                assertTrue(s.width>=48&&s.width<=80&&s.x>=25&&s.x+s.width<=w-40&&s.y>=24&&s.y+s.height<=h);
                if(turn==90)assertTrue(s.x>=w-margin);
                else assertTrue(s.x+s.width<=margin);
            }
            if(FullscreenSideLayout.statusFits(turn,margin,margin,54,4))assertTrue(margin>=58);
        }
    }
}
