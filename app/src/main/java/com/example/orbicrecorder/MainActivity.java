package com.example.orbicrecorder;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    TextView status;
    Handler handler = new Handler(Looper.getMainLooper());
    Runnable refresh;
    @Override public void onCreate(Bundle b) { super.onCreate(b);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(28,40,28,24); root.setBackgroundColor(Color.WHITE);
        TextView title = new TextView(this); title.setText("ORBiC Q10 • THỬ GHI ÂM ĐỒNG THỜI"); title.setTextSize(20); title.setTextColor(Color.BLACK); root.addView(title);
        status = new TextView(this); status.setTextSize(16); status.setTextColor(Color.DKGRAY); status.setPadding(0,24,0,24); root.addView(status);
        add(root,"BẮT ĐẦU GHI",()->start());
        add(root,"MỞ GOOGLE DỊCH",()->{ Intent i=getPackageManager().getLaunchIntentForPackage("com.google.android.apps.translate"); if(i!=null)startActivity(i);else Toast.makeText(this,"Chưa tìm thấy Google Dịch",Toast.LENGTH_LONG).show(); });
        add(root,"DỪNG GHI",()->send("STOP"));
        TextView hint=new TextView(this); hint.setText("Bước thử: Bắt đầu ghi → mở Google Dịch → nhấn MICRO → nói 15 giây → dừng ghi. Quan sát mức tín hiệu và nghe file WAV.\n\nNếu Android 12 chặn chia sẻ micro, file có thể im lặng. Ứng dụng không vượt qua giới hạn hệ thống."); hint.setTextColor(Color.DKGRAY); hint.setPadding(0,24,0,0); root.addView(hint);
        setContentView(root);
        refresh=()->{ android.content.SharedPreferences p=getSharedPreferences("state",MODE_PRIVATE); boolean running=p.getBoolean("running",false); status.setText("Trạng thái: "+(running?"ĐANG GHI":"ĐÃ DỪNG")+"\nMức tín hiệu: "+p.getInt("level",0)+" / 100\nNguồn: "+p.getString("source","Chưa xác định")+"\nFile: "+p.getString("file","Chưa có")+"\nLỗi: "+p.getString("error","Không")); handler.postDelayed(refresh,700); }; handler.post(refresh);
    }
    void add(LinearLayout r,String name,Runnable click){Button b=new Button(this);b.setText(name);r.addView(b);b.setOnClickListener(v->click.run());}
    void start(){if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}send("START");}
    void send(String action){Intent i=new Intent(this,RecorderService.class);i.setAction(action);if(action.equals("START")){try{startForegroundService(i);}catch(Exception e){Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show();}}else startService(i);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==1&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)start();}
    @Override protected void onDestroy(){handler.removeCallbacks(refresh);super.onDestroy();}
}
