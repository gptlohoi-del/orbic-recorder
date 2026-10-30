package com.example.orbicrecorder;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView status,details,path,logs;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){@Override public void run(){SharedPreferences p=getSharedPreferences("state",MODE_PRIVATE);boolean running=p.getBoolean("running",false);status.setText(running?"● ĐANG GHI / LỌC KHOẢNG LẶNG":"○ ĐÃ DỪNG");status.setTextColor(running?Color.rgb(15,130,60):Color.DKGRAY);details.setText("Tín hiệu: "+p.getString("db","—")+" dBFS\nTrạng thái: "+p.getString("capture","—")+"\nSố đoạn đã giữ: "+p.getInt("segments",0)+"\nThời lượng đã giữ: "+(p.getLong("keptMs",0L)/1000.0)+" giây\nNguồn: "+p.getString("source","—"));path.setText("File: "+p.getString("file","—")+"\nLỗi: "+p.getString("error","Không"));logs.setText(p.getString("log","Chưa có nhật ký"));handler.postDelayed(this,500);}};
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setPadding(0,12,0,8);return v;}
    private EditText number(LinearLayout root,String title,int value){root.addView(label(title,14));EditText e=new EditText(this);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);e.setSingleLine(true);e.setText(String.valueOf(value));root.addView(e);return e;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,24,28,32);scroll.addView(root);
        root.addView(label("ORBIC RECORDER • LỌC KHOẢNG LẶNG",20));root.addView(label("Chỉ lưu những đoạn vượt ngưỡng âm thanh, nối thành một file WAV. Không sử dụng Google Dịch.",14));
        Button start=new Button(this);start.setText("BẮT ĐẦU GHI");root.addView(start);Button stop=new Button(this);stop.setText("DỪNG VÀ LƯU WAV");root.addView(stop);
        status=label("",19);root.addView(status);details=label("",15);root.addView(details);path=label("",14);root.addView(path);
        root.addView(label("CÀI ĐẶT PHÁT HIỆN TIẾNG NÓI",18));SharedPreferences cfg=getSharedPreferences("config",MODE_PRIVATE);
        EditText threshold=number(root,"Ngưỡng tín hiệu (dBFS, từ -70 đến -10; mặc định -38)",cfg.getInt("threshold",-38));
        EditText minimum=number(root,"Thời gian tiếng đủ dài để giữ (ms, 60–1000)",cfg.getInt("minSpeech",180));
        EditText pre=number(root,"Giữ âm trước khi phát hiện tiếng (ms, 0–1000)",cfg.getInt("pre",300));
        EditText tail=number(root,"Giữ âm sau khi ngừng nói (ms, 100–2000)",cfg.getInt("tail",500));
        Button save=new Button(this);save.setText("LƯU CÀI ĐẶT");root.addView(save);
        root.addView(label("Lưu vào Music/OrbicRecorder. Cài đặt mới có hiệu lực ở lần bắt đầu ghi tiếp theo. Thuật toán dựa trên âm lượng, chưa phân biệt chính xác tiếng nói với tiếng ồn.",13));
        root.addView(label("NHẬT KÝ",17));logs=label("",12);logs.setTextIsSelectable(true);root.addView(logs);
        save.setOnClickListener(v->{try{int t=Integer.parseInt(threshold.getText().toString()),m=Integer.parseInt(minimum.getText().toString()),p=Integer.parseInt(pre.getText().toString()),h=Integer.parseInt(tail.getText().toString());if(t< -70||t> -10||m<60||m>1000||p<0||p>1000||h<100||h>2000)throw new IllegalArgumentException();cfg.edit().putInt("threshold",t).putInt("minSpeech",m).putInt("pre",p).putInt("tail",h).apply();Toast.makeText(this,"Đã lưu cài đặt",Toast.LENGTH_SHORT).show();}catch(Exception ex){Toast.makeText(this,"Giá trị ngoài khoảng cho phép",Toast.LENGTH_LONG).show();}});
        start.setOnClickListener(v->{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},100);Toast.makeText(this,"Cấp quyền micro rồi nhấn BẮT ĐẦU lại",Toast.LENGTH_LONG).show();return;}startForegroundService(new Intent(this,RecorderService.class).setAction("START"));});
        stop.setOnClickListener(v->startService(new Intent(this,RecorderService.class).setAction("STOP")));
        setContentView(scroll);
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
