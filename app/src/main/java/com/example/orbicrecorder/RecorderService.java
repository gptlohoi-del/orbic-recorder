package com.example.orbicrecorder;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecorderService extends Service {
    private static final String CHANNEL="recording";
    private volatile boolean recording=false;
    private AudioRecord audio;
    private Thread worker;
    private File file;
    private volatile int silenceCount=0;
    private String modeName="MIC";
    private long started;
    @Override public void onCreate(){super.onCreate();NotificationChannel c=new NotificationChannel(CHANNEL,"Orbic ghi âm",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}
    private Notification notification(){
        Intent stop=new Intent(this,RecorderService.class).setAction("STOP");
        PendingIntent pi=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setContentTitle("Orbic Recorder đang ghi: "+modeName).setContentText("Nhấn DỪNG để lưu WAV")
          .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true)
          .addAction(new Notification.Action.Builder(null,"DỪNG GHI",pi).build()).build();
    }
    private void state(String k,Object v){android.content.SharedPreferences.Editor e=getSharedPreferences("state",MODE_PRIVATE).edit();if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else e.putString(k,String.valueOf(v));e.apply();}
    private synchronized void log(String s){android.content.SharedPreferences p=getSharedPreferences("state",MODE_PRIVATE);String old=p.getString("log","");String line=new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+" "+s+"\n";String combined=line+old;String[] a=combined.split("\n");StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(50,a.length);i++)b.append(a[i]).append('\n');state("log",b.toString());}
    @Override public int onStartCommand(Intent intent,int flags,int id){String action=intent==null?"":intent.getAction();
        if("STOP".equals(action)){log("Người dùng yêu cầu dừng");recording=false;return START_NOT_STICKY;}
        if("START".equals(action)&&!recording){int m=intent.getIntExtra("mode",0);m=Math.max(0,Math.min(2,m));modeName=new String[]{"MIC","VOICE_RECOGNITION","UNPROCESSED"}[m];
            startForeground(101,notification());startRecording(m);
        }return START_NOT_STICKY;
    }
    private void startRecording(int m){try{
        int rate=16000;int min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(min<=0)throw new IOException("Unsupported sample rate");
        int src=new int[]{MediaRecorder.AudioSource.MIC,MediaRecorder.AudioSource.VOICE_RECOGNITION,MediaRecorder.AudioSource.UNPROCESSED}[m];
        audio=new AudioRecord.Builder().setAudioSource(src).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(Math.max(min*2,4096)).build();
        if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("AudioRecord initialization failed");
        File dir=new File(getExternalFilesDir(null),"RecordingsTemp");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create temp directory");
        file=new File(dir,"orbic_"+modeName.toLowerCase(Locale.US)+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".wav");
        state("file","Đang ghi tạm: "+file.getName());state("error","Không");state("capture","Bắt đầu");state("level",0);state("db","—");
        audio.startRecording();recording=true;started=System.currentTimeMillis();silenceCount=0;state("running",true);
        AudioDeviceInfo device=audio.getRoutedDevice();state("source",device==null?"Không xác định":device.getProductName()+" (type "+device.getType()+")");
        log("Bắt đầu "+modeName+", sampleRate="+rate+", route="+getSharedPreferences("state",MODE_PRIVATE).getString("source","?"));
        worker=new Thread(()->writeAudio(rate),"WavWriter");worker.start();
    }catch(Exception ex){state("error",ex.toString());log("LỖI khởi động: "+ex);recording=false;state("running",false);if(audio!=null){audio.release();audio=null;}stopForeground(true);stopSelf();}}
    private void writeAudio(int rate){long bytes=0;byte[] buf=new byte[4096];long lastLog=0;boolean wasSilent=false;boolean completed=false;
        try(RandomAccessFile out=new RandomAccessFile(file,"rw")){
            out.setLength(0);out.write(new byte[44]);
            while(recording){int n=audio.read(buf,0,buf.length);if(n>0){out.write(buf,0,n);bytes+=n;
                long energy=0;for(int i=0;i+1<n;i+=2){int sample=(short)((buf[i]&255)|(buf[i+1]<<8));energy+=(long)sample*sample;}
                double rms=Math.sqrt((double)energy/Math.max(1,n/2));double db=20*Math.log10(Math.max(1,rms)/32768.0);
                int level=(int)Math.min(100,Math.max(0,rms*100/10000));state("level",level);state("db",String.format(Locale.US,"%.1f",db));
                if(db< -55)silenceCount++;else silenceCount=0;
                boolean silent=silenceCount>=12; // ~1.5 seconds at 16 kHz
                if(silent!=wasSilent){wasSilent=silent;log(silent?"CẢNH BÁO: mức âm thanh rất thấp / im lặng":"Tín hiệu âm thanh trở lại");}
                state("capture",silent?"CẢNH BÁO IM LẶNG":"Đang nhận mẫu âm thanh");
                long now=System.currentTimeMillis();if(now-lastLog>10000){lastLog=now;AudioDeviceInfo d=audio.getRoutedDevice();log("Đang ghi "+modeName+", dBFS="+String.format(Locale.US,"%.1f",db)+", route="+(d==null?"?":d.getType()));}
            }else if(n<0){state("error","AudioRecord.read: "+n);log("LỖI AudioRecord.read="+n);break;}}
            writeHeader(out,bytes,rate);completed=true;
        }catch(Exception ex){state("error",ex.toString());log("LỖI ghi: "+ex);}finally{
            recording=false;state("running",false);state("level",0);state("capture","Đã dừng");
            if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();audio=null;}
            if(completed){try{String name=exportToMusic(file);state("file",name);log("Đã lưu: "+name);if(!file.delete())log("Không xóa được file tạm");}catch(Exception ex){state("error","Lỗi xuất file: "+ex);state("file","File tạm: "+file.getAbsolutePath());log("LỖI xuất file: "+ex);}}
            stopForeground(true);stopSelf();
        }
    }
    private String exportToMusic(File src)throws IOException{
        ContentValues v=new ContentValues();v.put(MediaStore.Audio.Media.DISPLAY_NAME,src.getName());v.put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav");v.put(MediaStore.Audio.Media.RELATIVE_PATH,Environment.DIRECTORY_MUSIC+"/OrbicRecorder");v.put(MediaStore.Audio.Media.IS_PENDING,1);
        Uri uri=getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("MediaStore insert returned null");
        boolean ok=false;try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(uri)){
            if(out==null)throw new IOException("MediaStore output stream null");byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);out.flush();ok=true;
        }finally{if(!ok)getContentResolver().delete(uri,null,null);}
        ContentValues done=new ContentValues();done.put(MediaStore.Audio.Media.IS_PENDING,0);getContentResolver().update(uri,done,null,null);
        return "Music/OrbicRecorder/"+src.getName();
    }
    private void writeHeader(RandomAccessFile o,long size,int rate)throws IOException{o.seek(0);o.writeBytes("RIFF");le32(o,size+36);o.writeBytes("WAVEfmt ");le32(o,16);le16(o,1);le16(o,1);le32(o,rate);le32(o,rate*2);le16(o,2);le16(o,16);o.writeBytes("data");le32(o,size);}
    private void le16(RandomAccessFile o,long v)throws IOException{o.write((int)v&255);o.write((int)(v>>8)&255);}
    private void le32(RandomAccessFile o,long v)throws IOException{le16(o,v);le16(o,v>>16);}
    @Override public void onDestroy(){recording=false;super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
