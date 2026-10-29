package jp.flowview.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class FullscreenSwipeTest {
    @Test public void movementOnlyPreviewsAndReleaseCommitsOnce(){
        FullscreenSwipe s=new FullscreenSwipe();s.start(200,80,1000,36);
        assertTrue(s.move(204,120,1120));assertEquals(.5f,s.progress(),.01f);
        assertTrue(s.move(205,180,1200));assertEquals(1f,s.progress(),.01f);
        assertTrue(s.move(205,200,1240)); // still tracking while the finger is held
        assertTrue(s.release(205,200,1300));assertFalse(s.release(205,200,1320));
    }
    @Test public void longHoldStillWaitsForRelease(){
        FullscreenSwipe s=new FullscreenSwipe();s.start(100,100,1000,36);
        assertTrue(s.move(100,200,1200));assertTrue(s.move(100,200,9000));
        assertTrue(s.release(100,200,10000));
    }
    @Test public void reversingOrReleasingShortSnapsBack(){
        FullscreenSwipe s=new FullscreenSwipe();s.start(100,100,1000,36);
        s.move(100,220,1200);s.move(100,130,1300);assertEquals(.375f,s.progress(),.01f);
        assertFalse(s.release(100,130,1400));
        s.start(100,100,2000,36);assertFalse(s.release(100,179,2200));
        s.start(100,100,3000,36);assertFalse(s.release(100,200,3010));
    }
    @Test public void topEdgeIsReservedForSystemBars(){
        FullscreenSwipe s=new FullscreenSwipe();
        for(float y:new float[]{0,20,36,60}){
            s.start(100,y,1000,60);assertFalse(s.move(100,200,1200));assertFalse(s.release(100,220,1400));
        }
        s.start(100,61,2000,60);assertTrue(s.release(100,180,2200));
    }
    @Test public void horizontalAndUpwardGesturesStayCancelled(){
        FullscreenSwipe s=new FullscreenSwipe();s.start(100,100,1000,36);
        assertFalse(s.move(250,110,1200));assertFalse(s.release(100,220,1500));
        s.start(100,200,2000,36);assertFalse(s.move(101,80,2200));assertFalse(s.release(100,300,2500));
    }
    @Test public void interruptionOrMultiTouchCancelsPreview(){
        FullscreenSwipe s=new FullscreenSwipe();s.start(100,100,1000,36);s.move(100,200,1200);s.cancel();
        assertEquals(0f,s.progress(),0f);assertFalse(s.release(100,250,1500));
        s.start(100,100,2000,36);assertTrue(s.release(100,200,2200));
    }
}
