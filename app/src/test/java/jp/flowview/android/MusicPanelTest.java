package jp.flowview.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import org.json.JSONArray;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.Shadows;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MusicPanelTest {
    public static class Host extends AppCompatActivity {
        LinearLayout column;ScrollView page;int touches;
        @Override protected void onCreate(Bundle state){
            setTheme(R.style.AppTheme);super.onCreate(state);column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);
            page=new ScrollView(this);TextView comments=new TextView(this);comments.setText("動画とコメント\n".repeat(100));page.addView(comments);
            page.setOnTouchListener((v,e)->{touches++;return false;});column.addView(page,new LinearLayout.LayoutParams(-1,0,1));
            column.addView(new View(this),new LinearLayout.LayoutParams(-1,70));setContentView(column);
        }
    }
    private View described(View root,String text){
        if(text.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View v=described(g.getChildAt(i),text);if(v!=null)return v;}}return null;
    }
    private void layout(View view,int height){view.forceLayout();view.measure(View.MeasureSpec.makeMeasureSpec(412,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,412,height);}
    @Test public void studioIsReadyWithoutAToggleAndKeepsThePageInteractive() throws Exception {
        try(var controller=Robolectric.buildActivity(Host.class).setup()){
            Host host=controller.get();layout(host.column,860);ArrayList<JSONObject> commands=new ArrayList<>();MusicPanel panel=new MusicPanel(host,commands::add,c->{},()->{},()->{});panel.show(host.column);
            assertTrue(panel.isShowing());assertEquals("enable",commands.get(0).optString("action"));assertTrue(commands.get(0).optBoolean("value"));
            View plus=described(host.column,"キーを半音上げる");assertNotNull(plus);assertNull(described(host.column,"キー・テンポの操作を有効にする"));
            panel.update(new JSONObject().put("enabled",true).put("pitch",-2).put("tempo",.85));assertTrue(plus.isEnabled());plus.performClick();assertEquals("pitch",commands.get(commands.size()-1).optString("action"));assertEquals(-1,commands.get(commands.size()-1).optInt("value"));
            panel.update(new JSONObject().put("enabled",true).put("pitch",-2).put("tempo",.85).put("eq",new JSONArray("[4,2,0,-3,1]")));
            Slider bass=(Slider)described(host.column,"60 Hzの音量、マイナス12からプラス12デシベル");assertNotNull(bass);assertEquals(4f,bass.getValue(),0f);
            int beforeBinding=commands.size();panel.update(new JSONObject().put("enabled",true).put("eq",new JSONArray("[0,0,0,0,0]")));assertEquals(beforeBinding,commands.size());assertEquals(0f,bass.getValue(),0f);
            Shadows.shadowOf(Looper.getMainLooper()).idle();layout(host.column,860);
            assertSame(host.page,host.column.getChildAt(0));assertTrue(host.page.getHeight()>400);
            int[] actions={MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP};
            for(int i=0;i<actions.length;i++){
                MotionEvent e=MotionEvent.obtain(0,i*100,actions[i],100,240-i*60,0);host.column.dispatchTouchEvent(e);e.recycle();
            }
            assertTrue("Page must receive touch input while the panel is open",host.touches>=2);assertTrue(host.page.getScrollY()>0);assertTrue(panel.isShowing());
            Bitmap image=Bitmap.createBitmap(412,860,Bitmap.Config.ARGB_8888);host.column.draw(new Canvas(image));File dir=new File("build/ui-previews");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"music-studio.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
            panel.toggle(host.column);assertFalse(panel.isShowing());assertEquals(2,host.column.getChildCount());
            panel.toggle(host.column);assertTrue(panel.isShowing());assertEquals(3,host.column.getChildCount());
            layout(host.column,360);layout(host.column,360);assertTrue("Page height after resize: "+host.page.getHeight(),host.page.getHeight()>100);
            described(host.column,"音楽パネルを閉じる").performClick();assertFalse(panel.isShowing());assertEquals(2,host.column.getChildCount());
            assertEquals("observe",commands.get(commands.size()-1).optString("action"));assertFalse(commands.get(commands.size()-1).optBoolean("value"));
        }
    }
    @Test public void transportControlsStayAboveScrollableMusicSettings() throws Exception {
        try(var controller=Robolectric.buildActivity(Host.class).setup()){
            Host host=controller.get();layout(host.column,860);
            ArrayList<String> actions=new ArrayList<>();int[] fullscreens={0},pips={0};
            MusicPanel panel=new MusicPanel(host,c->{},actions::add,()->fullscreens[0]++,()->pips[0]++);
            panel.show(host.column);
            LinearLayout studio=(LinearLayout)host.column.getChildAt(1);
            LinearLayout transport=(LinearLayout)studio.getChildAt(1);
            assertEquals(5,transport.getChildCount());
            MaterialButton back=(MaterialButton)transport.getChildAt(0);
            MaterialButton play=(MaterialButton)transport.getChildAt(1);
            MaterialButton forward=(MaterialButton)transport.getChildAt(2);
            assertEquals("−10秒",back.getText().toString());
            assertEquals("+10秒",forward.getText().toString());
            assertSame(transport,described(studio,"再生").getParent());
            assertSame(transport,described(studio,"全画面").getParent());
            assertSame(transport,described(studio,"小窓").getParent());
            ScrollView settings=(ScrollView)studio.getChildAt(3);
            assertNotNull(described(settings,"キーを半音上げる"));
            assertNotNull(described(settings,"60 Hzの音量、マイナス12からプラス12デシベル"));
            assertNull("Playback stays visible when settings scroll",described(settings,"再生"));
            back.performClick();forward.performClick();play.performClick();
            assertArrayEquals(new String[]{"back10","next10","playpause"},actions.toArray(new String[0]));
            panel.playback(true);
            assertSame(transport,described(studio,"一時停止").getParent());
            described(studio,"全画面").performClick();
            assertEquals(1,fullscreens[0]);assertFalse(panel.isShowing());
            panel.show(host.column);
            described(host.column,"小窓").performClick();
            assertEquals(1,pips[0]);assertFalse(panel.isShowing());
        }
    }

}