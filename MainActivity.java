package com.example.orbicrecorder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout root;private TextView status,stats,path;private Button start,pause,stop;private WaveView wave;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int ink,muted,panel;private boolean dark;
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(ink);if(bold)v.setTypeface(null,1);return v;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void gap(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private String clock(long ms){long sec=Math.max(0,ms/1000);return String.format(Locale.US,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);}
    private final Runnable refresh=new Runnable(){@Override public void run(){if(status==null)return;SharedPreferences p=getSharedPreferences("state",MODE_PRIVATE);boolean running=p.getBoolean("running",false),paused=p.getBoolean("paused",false);String capture=p.getString("capture","Sẵn sàng");status.setText(running?(paused?"⏸ TẠM DỪNG":"● "+capture):"○ SẴN SÀNG / ĐÃ DỪNG");stats.setText("Tổng thời gian: "+clock(p.getLong("elapsedMs",0))+"     Đã giữ: "+clock(p.getLong("keptMs",0))+"\nSố đoạn: "+p.getInt("segments",0)+"     Tín hiệu: "+p.getString("db","—")+" dBFS");path.setText("File: "+p.getString("file","Chưa có")+"\nLỗi: "+p.getString("error","Không"));wave.addLevel(p.getInt("level",0),running&&!paused);start.setEnabled(!running);pause.setEnabled(running);pause.setText(paused?"▶ TIẾP TỤC":"⏸ TẠM DỪNG");stop.setEnabled(running);handler.postDelayed(this,120);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);render();}
    private void render(){SharedPreferences cfg=getSharedPreferences("config",MODE_PRIVATE);String theme=cfg.getString("theme","system");int night=getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK;dark="dark".equals(theme)||("system".equals(theme)&&night==android.content.res.Configuration.UI_MODE_NIGHT_YES);ink=dark?Color.WHITE:Color.rgb(28,40,50);muted=dark?Color.rgb(180,192,203):Color.rgb(90,105,120);panel=dark?Color.rgb(32,45,56):Color.rgb(237,243,246);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(dark?Color.rgb(16,25,34):Color.WHITE);root=vertical();root.setPadding(dp(18),dp(16),dp(18),dp(24));scroll.addView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView heading=text("ORBIC RECORDER",21,true);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));Button settings=button("⚙ CÀI ĐẶT");top.addView(settings);root.addView(top);settings.setOnClickListener(v->settingsDialog());
        TextView subtitle=text("Voice Filter v3.1 • WAV 16 kHz",13,false);subtitle.setTextColor(muted);root.addView(subtitle);gap(root,16);
        LinearLayout waveBox=vertical();waveBox.setPadding(dp(12),dp(12),dp(12),dp(12));waveBox.setBackgroundColor(panel);TextView waveTitle=text("SÓNG ÂM ĐẦU VÀO",13,true);waveBox.addView(waveTitle);wave=new WaveView(this);waveBox.addView(wave,new LinearLayout.LayoutParams(-1,dp(85)));root.addView(waveBox);gap(root,14);
        status=text("Sẵn sàng",17,true);root.addView(status);gap(root,8);stats=text("",15,false);root.addView(stats);gap(root,8);
        TextView buffer=text("Giữ trước: "+(cfg.getInt("pre",1000)/1000f)+" giây   •   Giữ sau: "+(cfg.getInt("tail",1000)/1000f)+" giây",13,false);buffer.setTextColor(muted);root.addView(buffer);gap(root,16);
        start=button("● BẮT ĐẦU GHI");root.addView(start,new LinearLayout.LayoutParams(-1,dp(56)));gap(root,6);
        LinearLayout actions=new LinearLayout(this);pause=button("⏸ TẠM DỪNG");stop=button("■ DỪNG VÀ LƯU");actions.addView(pause,new LinearLayout.LayoutParams(0,dp(56),1));actions.addView(stop,new LinearLayout.LayoutParams(0,dp(56),1));root.addView(actions);gap(root,12);
        Button playback=button("▶ NGHE BẢN GHI GẦN NHẤT");root.addView(playback);playback.setOnClickListener(v->playLast());gap(root,8);
        path=text("",12,false);path.setTextColor(muted);path.setTextIsSelectable(true);root.addView(path);gap(root,10);
        TextView note=text("Bộ lọc dựa trên âm lượng: tiếng ồn đủ lớn vẫn có thể được giữ. Cài đặt mới áp dụng từ lần ghi tiếp theo.",12,false);note.setTextColor(muted);root.addView(note);
        start.setOnClickListener(v->{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},100);Toast.makeText(this,"Cấp quyền micro, sau đó nhấn BẮT ĐẦU",Toast.LENGTH_LONG).show();return;}startForegroundService(new Intent(this,RecorderService.class).setAction("START"));});
        pause.setOnClickListener(v->{boolean paused=getSharedPreferences("state",MODE_PRIVATE).getBoolean("paused",false);startService(new Intent(this,RecorderService.class).setAction(paused?"RESUME":"PAUSE"));});
        stop.setOnClickListener(v->startService(new Intent(this,RecorderService.class).setAction("STOP")));setContentView(scroll);handler.removeCallbacks(refresh);handler.post(refresh);
    }
    private void playLast(){String s=getSharedPreferences("state",MODE_PRIVATE).getString("uri","");if(s.isEmpty()){Toast.makeText(this,"Chưa có bản ghi để nghe",Toast.LENGTH_SHORT).show();return;}try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(Uri.parse(s),"audio/wav");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Nghe bản ghi"));}catch(Exception ex){Toast.makeText(this,"Không tìm thấy ứng dụng phát WAV",Toast.LENGTH_LONG).show();}}
    private EditText number(LinearLayout root,String label,int value){TextView t=text(label,14,false);root.addView(t);EditText e=new EditText(this);e.setSingleLine(true);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);e.setText(String.valueOf(value));root.addView(e);return e;}
    private void settingsDialog(){SharedPreferences cfg=getSharedPreferences("config",MODE_PRIVATE);ScrollView sc=new ScrollView(this);LinearLayout layout=vertical();layout.setPadding(dp(20),dp(10),dp(20),dp(10));sc.addView(layout);
        EditText threshold=number(layout,"Ngưỡng âm thanh dBFS (-70 đến -10)",cfg.getInt("threshold",-38));
        EditText min=number(layout,"Xác nhận tiếng nói (ms, 60–1000)",cfg.getInt("minSpeech",180));
        EditText pre=number(layout,"Giữ trước khi nói (ms, 0–5000)",cfg.getInt("pre",1000));
        EditText tail=number(layout,"Giữ sau khi nói (ms, 100–5000)",cfg.getInt("tail",1000));
        layout.addView(text("Giao diện",16,true));RadioGroup group=new RadioGroup(this);String[] labels={"Theo hệ thống","Sáng","Tối"},values={"system","light","dark"};int selected=0;String current=cfg.getString("theme","system");for(int j=0;j<3;j++){RadioButton r=new RadioButton(this);r.setId(1000+j);r.setText(labels[j]);group.addView(r);if(values[j].equals(current))selected=j;}group.check(1000+selected);layout.addView(group);
        TextView warning=text("Pause sẽ bỏ qua âm thanh trong lúc tạm dừng. Sau khi tiếp tục, bộ đệm được tạo mới. Lưu WAV vào Music/OrbicRecorder.",13,false);layout.addView(warning);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("CÀI ĐẶT").setView(sc).setNegativeButton("ĐÓNG",(d,w)->{}).setPositiveButton("LƯU",null).setNeutralButton("MẶC ĐỊNH",null).create();dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{int t=Integer.parseInt(threshold.getText().toString()),m=Integer.parseInt(min.getText().toString()),p=Integer.parseInt(pre.getText().toString()),h=Integer.parseInt(tail.getText().toString());if(t< -70||t> -10||m<60||m>1000||p<0||p>5000||h<100||h>5000)throw new IllegalArgumentException();String th=values[group.getCheckedRadioButtonId()-1000];cfg.edit().putInt("threshold",t).putInt("minSpeech",m).putInt("pre",p).putInt("tail",h).putString("theme",th).apply();dialog.dismiss();render();Toast.makeText(this,"Đã lưu cài đặt",Toast.LENGTH_SHORT).show();}catch(Exception ex){Toast.makeText(this,"Giá trị không hợp lệ",Toast.LENGTH_LONG).show();}});dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{cfg.edit().putInt("threshold",-38).putInt("minSpeech",180).putInt("pre",1000).putInt("tail",1000).putString("theme","system").apply();dialog.dismiss();render();});});dialog.show();
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private static class WaveView extends View {private final int[] bars=new int[48];private int pos=0;private boolean live=false;private final Paint p=new Paint(3);WaveView(Context c){super(c);}void addLevel(int level,boolean active){bars[pos]=level;pos=(pos+1)%bars.length;live=active;invalidate();}@Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth()/(float)bars.length,mid=getHeight()/2f;for(int i=0;i<bars.length;i++){int v=bars[(pos+i)%bars.length];float h=Math.max(3,(getHeight()-8)*Math.min(100,v)/100f);p.setColor(!live?0xff8294a5:v>75?0xffe3a12c:0xff22a87a);c.drawRoundRect(i*w+2,mid-h/2,i*w+w-2,mid+h/2,3,3,p);}}}
}
