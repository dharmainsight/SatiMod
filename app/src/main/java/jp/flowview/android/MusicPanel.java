package jp.flowview.android;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import org.json.JSONObject;
import java.util.Locale;
import java.util.function.Consumer;

/** Music controls render actual page-engine state; audio stays inside the viewing page. */
final class MusicPanel {
    private final FlowUi ui;private final Consumer<JSONObject> send;private final Consumer<String> transport;private final Runnable fullscreen,pip;
    private Dialog dialog;private TextView pitchValue,tempoValue,status;
    private MaterialButton minus,plus,slower,faster,play;private MaterialSwitch enabledSwitch;private Slider speed;
    private boolean binding,enabled,playing,busy;private int pitch;private float tempo=1;
    MusicPanel(Activity a,Consumer<JSONObject> send,Consumer<String> transport,Runnable fullscreen,Runnable pip){ui=new FlowUi(a);this.send=send;this.transport=transport;this.fullscreen=fullscreen;this.pip=pip;}
    private void command(String action,Object value){try{JSONObject data=new JSONObject().put("action",action);if(value!=null)data.put("value",value);send.accept(data);}catch(Exception ignored){}}
    Dialog show(){
        if(dialog!=null&&dialog.isShowing())return dialog;
        FlowUi.Sheet sheet=ui.sheet("Music Studio","キーとテンポを、自分に合わせて。",550);dialog=sheet.dialog;
        ScrollView scroll=new ScrollView(ui.a);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);LinearLayout body=ui.column();scroll.addView(body);sheet.body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout toggle=ui.row(),words=ui.column();words.addView(ui.title("この動画で使う",16));status=ui.text("オフ",12,FlowUi.MUTED);ui.gap(words,5);words.addView(status);toggle.addView(words,new LinearLayout.LayoutParams(0,-2,1));enabledSwitch=new MaterialSwitch(ui.a);enabledSwitch.setContentDescription("キー・テンポの操作を有効にする");toggle.addView(enabledSwitch);body.addView(toggle);ui.gap(body,16);
        enabledSwitch.setOnCheckedChangeListener((v,checked)->{if(!binding){busy=checked;status.setText(checked?"操作を有効にしています…":"オフ");command("enable",checked);}});
        LinearLayout controls=ui.card();controls.addView(ui.text("キー / 半音",12,FlowUi.MUTED));ui.gap(controls,12);
        LinearLayout key=ui.row();minus=ui.icon("minus","キーを半音下げる",()->command("pitch",Math.max(-12,pitch-1)),false);plus=ui.icon("plus","キーを半音上げる",()->command("pitch",Math.min(12,pitch+1)),true);
        pitchValue=ui.title("±0",38);pitchValue.setGravity(android.view.Gravity.CENTER);pitchValue.setFontFeatureSettings("tnum");
        key.addView(minus,new LinearLayout.LayoutParams(ui.dp(56),ui.dp(56)));key.addView(pitchValue,new LinearLayout.LayoutParams(0,ui.dp(58),1));key.addView(plus,new LinearLayout.LayoutParams(ui.dp(56),ui.dp(56)));controls.addView(key);ui.gap(controls,18);
        View divider=new View(ui.a);divider.setBackgroundColor(FlowUi.LINE);controls.addView(divider,new LinearLayout.LayoutParams(-1,ui.dp(1)));ui.gap(controls,18);
        LinearLayout speedTitle=ui.row();speedTitle.addView(ui.text("テンポ",12,FlowUi.MUTED),new LinearLayout.LayoutParams(0,-2,1));tempoValue=ui.title("100%",24);tempoValue.setFontFeatureSettings("tnum");speedTitle.addView(tempoValue);controls.addView(speedTitle);ui.gap(controls,8);
        LinearLayout speedRow=ui.row();slower=ui.icon("minus","テンポを5%下げる",()->command("tempo",Math.max(.5,Math.round((tempo-.05)*100)/100.0)),false);faster=ui.icon("plus","テンポを5%上げる",()->command("tempo",Math.min(2,Math.round((tempo+.05)*100)/100.0)),false);
        speed=new Slider(ui.a);speed.setValueFrom(50);speed.setValueTo(200);speed.setStepSize(5);speed.setValue(100);speed.setContentDescription("再生テンポ、50%から200%");speed.setLabelFormatter(v->Math.round(v)+"%");
        speed.addOnChangeListener((v,value,fromUser)->{if(fromUser&&!binding)command("tempo",value/100.0);});speedRow.addView(slower,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));speedRow.addView(speed,new LinearLayout.LayoutParams(0,ui.dp(48),1));speedRow.addView(faster,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));controls.addView(speedRow);body.addView(controls);ui.gap(body,12);
        LinearLayout player=ui.row();MaterialButton back=ui.button("−10秒",null,()->transport.accept("back10"),false),next=ui.button("+10秒",null,()->transport.accept("next10"),false);
        play=ui.icon(playing?"pause":"play",playing?"一時停止":"再生",()->transport.accept("playpause"),true);
        for(MaterialButton b:new MaterialButton[]{back,play,next,ui.icon("full","全画面",()->{dialog.dismiss();fullscreen.run();},false),ui.icon("pip","小窓",()->{dialog.dismiss();pip.run();},false)}){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ui.dp(52),1);lp.setMargins(ui.dp(2),0,ui.dp(2),0);player.addView(b,lp);}body.addView(player);ui.gap(body,12);
        body.addView(ui.button("原曲のキー・テンポに戻す","refresh",()->command("reset",null),false),new LinearLayout.LayoutParams(-1,ui.dp(48)));ui.gap(body,8);
        dialog.show();command("query",null);return dialog;
    }
    void dismissed(){command("observe",false);dialog=null;}
    void playback(boolean value){playing=value;if(play!=null){play.setIcon(new FlowIcon(value?"pause":"play",FlowUi.INK,ui.dp(24)));play.setContentDescription(value?"一時停止":"再生");}}
    void update(JSONObject state){
        if(state==null||dialog==null||!dialog.isShowing())return;
        binding=true;enabled=state.optBoolean("enabled");busy=state.optBoolean("busy");pitch=Math.max(-12,Math.min(12,state.optInt("pitch",0)));double t=state.optDouble("tempo",1);tempo=Double.isFinite(t)?(float)Math.max(.5,Math.min(2,t)):1;
        enabledSwitch.setChecked(enabled||busy);enabledSwitch.setEnabled(true);
        pitchValue.setText(pitch==0?"±0":String.format(Locale.JAPAN,"%+d",pitch));tempoValue.setText(Math.round(tempo*100)+"%");speed.setValue(Math.round(tempo*20)*5);speed.setEnabled(enabled);
        minus.setEnabled(enabled&&!busy&&pitch>-12);plus.setEnabled(enabled&&!busy&&pitch<12);slower.setEnabled(enabled&&tempo>.5);faster.setEnabled(enabled&&tempo<2);
        String e=state.optString("error");String msg;
        if(!e.isEmpty())switch(e){
            case "NoVideo":msg="動画を再生してから有効にしてください";break;
            case "AudioAlreadyConnected":msg="他の音声拡張をOFFにしてページを更新してください";break;
            case "ProtectedMedia":case "CrossOriginMedia":msg="この音源ではキーを変更できません。テンポは変更できます";break;
            case "ResumeTimeout":msg="動画を一度タップして再生し、キー変更をやり直してください";break;
            case "VideoChanged":msg="動画が切り替わりました。キー変更をやり直してください";break;
            default:String code=state.optString("detail");msg="移調を開始できません"+(code.matches("P[1-4]")?"（"+code+"）":"")+"。テンポは変更できます";
        }
        else msg=busy?"キーを変更しています…":enabled?(pitch==0?"オン · キー・テンポを調整できます":"オン · キーを変更して再生中"):"オフ";
        status.setText(msg);binding=false;
    }
}
