package jp.flowview.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class FullscreenSwipeTest {
    @Test public void downwardGestureFiresDuringMovementOnlyOnce(){
        FullscreenSwipe s=new FullscreenSwipe();s.start(200,80,1000);
        assertFalse(s.move(204,130,1120));assertTrue(s.move(205,162,1200));
        assertFalse(s.move(205,200,1240));
    }
    @Test public void horizontalSeekingAndUpwardSwipesDoNotExit(){
        FullscreenSwipe s=new FullscreenSwipe();s.start(100,100,1000);
        assertFalse(s.move(250,110,1200));assertFalse(s.move(160,180,1250));
        s.start(100,200,2000);assertFalse(s.move(101,80,2200));
    }
    @Test public void ShortAndSlowAndAccidentalGesturesDoNotExit(){
        FullscreenSwipe s=new FullscreenSwipe();s.start(100,100,1000);
        assertFalse(s.move(100,179,1200));assertFalse(s.move(100,185,2600));
        assertFalse(s.move(100,280,2700));
        s.start(100,100,3000);assertFalse(s.move(100,200,3010));
    }
    @Test public void CancellationForMultiTouchPreventsExit(){
        FullscreenSwipe s=new FullscreenSwipe();s.start(100,100,1000);s.cancel();
        assertFalse(s.move(100,200,1250));
        s.start(100,100,2000);assertTrue(s.move(100,200,2200));
    }
}
