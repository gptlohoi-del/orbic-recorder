package com.example.orbicrecorder;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    Spinner source; TextView status, level, log, path; Handler handler = new Handler(Looper.getMainLooper());
    final String[] modes={"MIC", "VOICE_RECOGNITION", "UNPROCESSED"};
    final Runnable refresh=new Runnable(){ public void run(){
        android.content.SharedPreferences p=getSharedPreferences("state",MODE_PRIVATE);
        boolean running=p.getBoolean("running",false);
        status.setText(running?"● ĐANG GHI ÂM":"○ ĐÃ DỪNG"); status.setTextColor(running?Color.rgb(20,130,55):Color.DKGRAY);
        level.setText("Mức âm thanh: "+p.getInt("level",0)+"%   |   "+p.getString("db","—")+" dBFS\n"+
          "Nguồn: "+p.getString("source","—")+"\n"+
          "Trạng thái: "+p.getString("capture","—"));
        path.setText("File: "+p.getString("file","—")+"\nLỗi: "+p.getString("error","Không"));
        log.setText(p.getString("log","Chưa có nhật ký"));
        source.setEnabled(!running);
        handler.postDelayed(this,500);
    }};
    @Override public void onCreate(Bundle b){super.onCreate(b);
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,26,28,28);scroll.addView(root);
        TextView title=new TextView(this);title.setText("ORBIC RECORDER • MICRO TEST");title.setTextSize(20);title.setTextColor(Color.BLACK);root.addView(title);
        TextView hint=new TextView(this);hint.setText("Thử ghi âm nền đồng thời với Google Dịch. Mỗi lần chỉ thử một chế độ.");hint.setPadding(0,12,0,16);root.addView(hint);
        source=new Spinner(this);source.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,modes));root.addView(source);
        int saved=getPreferences(MODE_PRIVATE).getInt("mode",0);source.setSelection(saved);
        source.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){getPreferences(MODE_PRIVATE).edit().putInt("mode",pos).apply();}});
        Button start=new Button(this);start.setText("BẮT ĐẦU GHI");root.addView(start);
        Button stop=new Button(this);stop.setText("DỪNG VÀ LƯU WAV");root.addView(stop);
        Button translate=new Button(this);translate.setText("MỞ GOOGLE DỊCH");root.addView(translate);
        status=new TextView(this);status.setTextSize(21);status.setPadding(0,20,0,12);root.addView(status);
        level=new TextView(this);level.setTextSize(16);root.addView(level);
        path=new TextView(this);path.setPadding(0,16,0,10);root.addView(path);
        TextView ltitle=new TextView(this);ltitle.setText("NHẬT KÝ (giữ tối đa 50 dòng)");ltitle.setTextSize(16);root.addView(ltitle);
        log=new TextView(this);log.setTextSize(12);log.setTextIsSelectable(true);root.addView(log);
        Button clear=new Button(this);clear.setText("XÓA NHẬT KÝ");root.addView(clear);
        start.setOnClickListener(v->{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},100);Toast.makeText(this,"Cấp quyền micro rồi nhấn BẮT ĐẦU lại",Toast.LENGTH_LONG).show();return;}
            Intent i=new Intent(this,RecorderService.class).setAction("START").putExtra("mode",source.getSelectedItemPosition());startForegroundService(i);
        });
        stop.setOnClickListener(v->startService(new Intent(this,RecorderService.class).setAction("STOP")));
        translate.setOnClickListener(v->{Intent i=getPackageManager().getLaunchIntentForPackage("com.google.android.apps.translate");if(i!=null)startActivity(i);else Toast.makeText(this,"Không tìm thấy Google Dịch; mở thủ công",Toast.LENGTH_LONG).show();});
        clear.setOnClickListener(v->getSharedPreferences("state",MODE_PRIVATE).edit().putString("log","").apply());
        setContentView(scroll);
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
