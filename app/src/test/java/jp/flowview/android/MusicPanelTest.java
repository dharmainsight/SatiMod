package jp.flowview.android;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
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
        @Override protected void onCreate(Bundle state){setTheme(R.style.AppTheme);super.onCreate(state);setContentView(new android.widget.FrameLayout(this));}
    }
    private View described(View root,String text){
        if(text.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View v=described(g.getChildAt(i),text);if(v!=null)return v;}}return null;
    }
    @Test public void studioRendersAndPitchControlsFollowEngineState() throws Exception {
        try(var controller=Robolectric.buildActivity(Host.class).setup()){
            Host host=controller.get();ArrayList<JSONObject> commands=new ArrayList<>();MusicPanel panel=new MusicPanel(host,commands::add,c->{},()->{},()->{});Dialog d=panel.show();
            panel.update(new JSONObject().put("enabled",false));View plus=described(d.getWindow().getDecorView(),"キーを半音上げる");assertNotNull(plus);assertFalse(plus.isEnabled());
            panel.update(new JSONObject().put("enabled",true).put("pitch",-2).put("tempo",.85));assertTrue(plus.isEnabled());assertNull(described(d.getWindow().getDecorView(),"音程解析を有効にする"));plus.performClick();assertEquals("pitch",commands.get(commands.size()-1).optString("action"));assertEquals(-1,commands.get(commands.size()-1).optInt("value"));
            Shadows.shadowOf(Looper.getMainLooper()).idle();View view=d.getWindow().getDecorView();view.measure(View.MeasureSpec.makeMeasureSpec(412,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(860,View.MeasureSpec.EXACTLY));view.layout(0,0,412,860);
            Bitmap image=Bitmap.createBitmap(412,860,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));File dir=new File("build/ui-previews");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"music-studio.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}d.dismiss();panel.dismissed();
            assertEquals("observe",commands.get(commands.size()-1).optString("action"));
        }
    }
}
