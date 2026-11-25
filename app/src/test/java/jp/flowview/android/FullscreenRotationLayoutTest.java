package jp.flowview.android;

import android.app.Activity;
import android.graphics.Insets;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class FullscreenRotationLayoutTest {
    private void layout(View v,int w,int h){
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
        v.layout(0,0,w,h);
    }
    @Test public void portraitWindowAndLandscapeContentKeepBothDimensions(){
        FrameLayout child=new FrameLayout(RuntimeEnvironment.getApplication());
        FullscreenRotationLayout host=new FullscreenRotationLayout(child.getContext(),child);
        for(int turn:new int[]{90,-90}){
            host.setTurn(turn);layout(host,684,1536);
            assertEquals(684,host.getWidth());assertEquals(1536,host.getHeight());
            assertEquals(1536,child.getWidth());assertEquals(684,child.getHeight());
            assertEquals(turn,host.turn());assertEquals(turn,child.getRotation(),0f);
            assertEquals(turn==90?684:0,child.getTranslationX(),0f);
            assertEquals(turn==-90?1536:0,child.getTranslationY(),0f);
        }
        host.setTurn(0);layout(host,684,1536);
        assertEquals(684,child.getWidth());assertEquals(1536,child.getHeight());
        assertEquals(0,child.getRotation(),0f);
        host.setTurn(90);layout(host,900,400);
        assertEquals(0,host.turn());assertEquals(900,child.getWidth());assertEquals(400,child.getHeight());
    }
    @Test public void physicalPortraitTopMapsToTheCorrectSide(){
        Insets physical=Insets.of(2,54,3,20);
        assertEquals(Insets.of(54,3,20,2),FullscreenRotationLayout.logicalInsets(90,physical));
        assertEquals(Insets.of(20,2,54,3),FullscreenRotationLayout.logicalInsets(-90,physical));
        float[] out=new float[2];
        FullscreenRotationLayout.physicalToLogical(90,684,1536,342,54,out);
        assertArrayEquals(new float[]{54,342},out,0f);
        FullscreenRotationLayout.physicalToLogical(-90,684,1536,342,54,out);
        assertArrayEquals(new float[]{1482,342},out,0f);
        assertEquals(-90,FullscreenRotationLayout.sensorTurn(90,90));
        assertEquals(90,FullscreenRotationLayout.sensorTurn(270,-90));
        assertEquals(-90,FullscreenRotationLayout.sensorTurn(-1,-90));
        assertEquals(90,FullscreenRotationLayout.sensorTurn(0,90));
    }
    @Test public void rotatedTouchDispatchReachesTheOppositeGutterButton(){
        // View.post() on a detached View queues work until it is attached. Use a
        // real Activity window, so the normal click runnable reaches the UI looper.
        try(var controller=Robolectric.buildActivity(Activity.class).setup()){
            Activity activity=controller.get();
            FrameLayout child=new FrameLayout(activity);
            View button=new View(activity);
            int[] clicks={0},touches={0};
            float[] local=new float[2];
            button.setOnClickListener(v->clicks[0]++);
            button.setOnTouchListener((v,e)->{
                touches[0]++;
                local[0]=e.getX();local[1]=e.getY();
                return false; // Keep Android's normal click handling.
            });
            FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(100,100);
            params.topMargin=292;child.addView(button,params);
            FullscreenRotationLayout host=new FullscreenRotationLayout(activity,child);
            activity.setContentView(host);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue("Click target must be attached to a window",button.isAttachedToWindow());
            for(int turn:new int[]{90,-90}){
                params.leftMargin=turn==90?1400:36;
                button.setLayoutParams(params);
                host.setTurn(turn);layout(host,684,1536);
                int prior=clicks[0];touches[0]=0;
                // Both logical opposite-gutter centers map to physical (342,1450).
                long now=SystemClock.uptimeMillis();
                MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,342,1450,0);
                MotionEvent up=MotionEvent.obtain(now,now,MotionEvent.ACTION_UP,342,1450,0);
                try{assertTrue(host.dispatchTouchEvent(down));assertTrue(host.dispatchTouchEvent(up));}
                finally{down.recycle();up.recycle();}
                assertEquals("Both touch events must reach the actual button",2,touches[0]);
                assertArrayEquals("Button-local coordinates must be its center",new float[]{50,50},local,0.1f);
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                assertEquals("Android must invoke the click callback",prior+1,clicks[0]);
            }
        }
    }
}
