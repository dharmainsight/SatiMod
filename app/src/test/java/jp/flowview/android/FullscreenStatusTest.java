package jp.flowview.android;

import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class FullscreenStatusTest {
    @Test public void systemBarPolicyDoesNotDependOnVideoGeometry(){
        List<String> calls=new ArrayList<>();
        WindowInsetsController controller=(WindowInsetsController)Proxy.newProxyInstance(
                WindowInsetsController.class.getClassLoader(),new Class<?>[]{WindowInsetsController.class},
                (proxy,method,args)->{
                    if("show".equals(method.getName())||"hide".equals(method.getName()))
                        calls.add(method.getName()+":"+args[0]);
                    return null;
                });
        int status=WindowInsets.Type.statusBars(),nav=WindowInsets.Type.navigationBars();
        FullscreenStatus.applyBars(controller,true,true);
        assertEquals(List.of("hide:"+nav,"show:"+status),calls);
        calls.clear();
        FullscreenStatus.applyBars(controller,true,false);
        assertEquals(List.of("hide:"+nav,"hide:"+status),calls);
        calls.clear();
        FullscreenStatus.applyBars(controller,false,false);
        assertEquals(List.of("show:"+WindowInsets.Type.systemBars()),calls);
    }

    private WindowInsets bars(boolean statusVisible,boolean navigationVisible){
        int status=WindowInsets.Type.statusBars(),nav=WindowInsets.Type.navigationBars();
        return new WindowInsets.Builder()
                .setInsets(status,Insets.of(0,24,0,0)).setVisible(status,statusVisible)
                .setInsets(nav,Insets.of(0,0,0,24)).setVisible(nav,navigationVisible)
                .build();
    }
    private void layout(FrameLayout root){
        root.measure(View.MeasureSpec.makeMeasureSpec(1536,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(684,View.MeasureSpec.EXACTLY));
        root.layout(0,0,1536,684);
    }
    @Test public void videoViewportIdenticalWithNativeStatusBarShownOrHidden(){
        FrameLayout root=new FrameLayout(RuntimeEnvironment.getApplication());
        View video=new View(root.getContext());root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        FullscreenStatus.applyContentInsets(root,bars(true,true),false);layout(root);
        assertEquals(24,root.getPaddingTop());assertEquals(24,root.getPaddingBottom());
        assertEquals(636,video.getHeight());
        for(boolean statusVisible:new boolean[]{true,false,true}){
            FullscreenStatus.applyContentInsets(root,bars(statusVisible,false),true);layout(root);
            assertEquals(0,root.getPaddingTop());assertEquals(0,root.getPaddingBottom());
            assertEquals(1536,video.getWidth());assertEquals(684,video.getHeight());
            assertEquals(0,video.getTop());
        }
        FullscreenStatus.applyContentInsets(root,bars(true,true),false);layout(root);
        assertEquals(24,root.getPaddingTop());assertEquals(636,video.getHeight());
    }
}
