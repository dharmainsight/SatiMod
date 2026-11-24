package jp.flowview.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class FullscreenSideLayoutTest {
    private FullscreenSideLayout.Plan plan(int w,int h,int vw,int vh,int left,int right){
        return FullscreenSideLayout.plan(w,h,vw,vh,left,0,right,0,48,80,96,48,4);
    }
    @Test public void bothGuttersKeepVideoUntouched(){
        var p=plan(900,400,16,9,30,0);
        assertNotNull(p.status);assertNotNull(p.toggle);
        assertTrue(p.status.x>=30&&p.status.x+p.status.width<=94);
        assertTrue(p.toggle.x>=806&&p.toggle.x+p.toggle.width<=900);
    }
    @Test public void cutoutUsesOtherSideWithoutOverlap(){
        for(int left:new int[]{0,80}){
            var p=plan(900,400,16,9,left,80-left);
            assertNotNull(p.status);assertNotNull(p.toggle);
            assertTrue(p.status.y+p.status.height<=p.toggle.y);
            if(left==80){assertTrue(p.status.x>=806&&p.toggle.x>=806);}
            else {assertTrue(p.status.x+p.status.width<=94&&p.toggle.x+p.toggle.width<=94);}
        }
    }
    @Test public void noRoomOrUnknownVideoHidesOverlays(){
        for(var p:new FullscreenSideLayout.Plan[]{plan(900,400,21,9,0,0),plan(900,400,0,0,0,0),plan(900,400,16,9,80,80)}){
            assertNull(p.status);assertNull(p.toggle);
        }
    }
    @Test public void shortWindowPrioritizesToggleAndVerticalVideoCapsWidth(){
        var p=plan(900,100,16,9,0,400);
        assertNull(p.status);assertNotNull(p.toggle);
        p=plan(900,400,9,16,30,0);
        assertEquals(80,p.status.width);assertEquals(80,p.toggle.width);
    }
    @Test public void geometrySweepNeverCoversVideoOrCutout(){
        for(int w=100;w<1500;w+=13)for(int h=50;h<800;h+=17)for(int vw=1;vw<25;vw+=3){
            var p=plan(w,h,vw,9,25,40);
            double margin=Math.max(0,(w-h*vw/9.0)/2);
            for(var s:new FullscreenSideLayout.Slot[]{p.status,p.toggle})if(s!=null){
                assertTrue(s.width>=48&&s.width<=80&&s.x>=25&&s.x+s.width<=w-40&&s.y>=0&&s.y+s.height<=h);
                assertTrue(s.x+s.width<=margin||s.x>=w-margin);
            }
            if(p.status!=null&&p.toggle!=null){
                var a=p.status;var b=p.toggle;
                assertTrue(a.x+a.width<=b.x||b.x+b.width<=a.x||a.y+a.height<=b.y||b.y+b.height<=a.y);
            }
        }
    }
}
