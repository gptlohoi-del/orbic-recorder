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
    private File temp;
    private Thread worker;
    private void state(String k,Object v){SharedPreferences.Editor e=getSharedPreferences("state",MODE_PRIVATE).edit();if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else e.putString(k,String.valueOf(v));e.apply();}
    private void log(String message){SharedPreferences p=getSharedPreferences("state",MODE_PRIVATE);String previous=p.getString("log","");String s=new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+" "+message+"\n"+previous;state("log",s.length()>5000?s.substring(0,5000):s);}
    @Override public void onCreate(){super.onCreate();NotificationChannel c=new NotificationChannel(CHANNEL,"Orbic ghi âm",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}
    private Notification notification(){Intent stop=new Intent(this,RecorderService.class).setAction("STOP");PendingIntent pi=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Orbic Recorder: lọc khoảng lặng").setContentText("Đang theo dõi tiếng nói • Nhấn DỪNG để lưu").setOngoing(true).addAction(new Notification.Action.Builder(null,"DỪNG VÀ LƯU",pi).build()).build();}
    @Override public int onStartCommand(Intent i,int flags,int id){String action=i==null?"":i.getAction();if("STOP".equals(action)){recording=false;return START_NOT_STICKY;}if("START".equals(action)&&!recording){startForeground(101,notification());startRecording();}return START_NOT_STICKY;}
    private void startRecording(){try{
        int rate=16000;int min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0)throw new IOException("Sample rate không hỗ trợ");
        audio=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(Math.max(min*2,4096)).build();
        if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Không khởi tạo được micro");
        File dir=new File(getExternalFilesDir(null),"RecordingsTemp");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Không tạo được thư mục tạm");
        temp=new File(dir,"orbic_voice_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".wav");
        audio.startRecording();recording=true;state("running",true);state("error","Không");state("file","Đang ghi...");state("keptMs",0L);state("segments",0);state("db","—");state("capture","Đang nghe");
        AudioDeviceInfo d=audio.getRoutedDevice();state("source",d==null?"Chưa xác định":d.getProductName()+" (type "+d.getType()+")");log("Bắt đầu lọc khoảng lặng");
        worker=new Thread(this::writeVoice,"VoiceWriter");worker.start();
    }catch(Exception ex){state("error",ex.toString());log("Lỗi khởi động: "+ex);recording=false;state("running",false);if(audio!=null){audio.release();audio=null;}stopForeground(true);stopSelf();}}
    private void writeVoice(){
        final int rate=16000, frameBytes=640, frameMs=20;
        SharedPreferences cfg=getSharedPreferences("config",MODE_PRIVATE);
        final int threshold=cfg.getInt("threshold",-38), minSpeechMs=cfg.getInt("minSpeech",180), preMs=cfg.getInt("pre",300), tailMs=cfg.getInt("tail",500);
        final int startFrames=Math.max(1,(minSpeechMs+frameMs-1)/frameMs), preFrames=Math.max(0,preMs/frameMs), tailFrames=Math.max(1,tailMs/frameMs);
        Deque<byte[]> pre=new ArrayDeque<>();ArrayList<byte[]> candidate=new ArrayList<>();
        int consecutive=0, silentFrames=0, segments=0;boolean active=false;long keptBytes=0;boolean completed=false;
        byte[] frame=new byte[frameBytes];
        try(RandomAccessFile out=new RandomAccessFile(temp,"rw")){
            out.setLength(0);out.write(new byte[44]);
            while(recording){int filled=0;while(filled<frameBytes&&recording){int n=audio.read(frame,filled,frameBytes-filled);if(n>0)filled+=n;else if(n<0)throw new IOException("AudioRecord.read="+n);}
                if(filled<frameBytes)break;
                byte[] block=Arrays.copyOf(frame,frameBytes);long energy=0;for(int j=0;j<frameBytes;j+=2){int sample=(short)((block[j]&255)|(block[j+1]<<8));energy+=(long)sample*sample;}
                double rms=Math.sqrt((double)energy/(frameBytes/2));double db=20*Math.log10(Math.max(1,rms)/32768.0);boolean loud=db>=threshold;
                state("db",String.format(Locale.US,"%.1f",db));state("level",(int)Math.min(100,rms*100/10000));
                if(active){out.write(block);keptBytes+=frameBytes;if(loud)silentFrames=0;else silentFrames++;if(silentFrames>=tailFrames){active=false;consecutive=0;candidate.clear();pre.clear();silentFrames=0;state("capture","Đang chờ tiếng nói");}}
                else if(loud){consecutive++;candidate.add(block);if(consecutive>=startFrames){active=true;segments++;for(byte[] b:pre){out.write(b);keptBytes+=b.length;}pre.clear();for(byte[] b:candidate){out.write(b);keptBytes+=b.length;}candidate.clear();state("capture","Đang giữ tiếng nói");log("Phát hiện đoạn tiếng nói #"+segments);}}
                else {consecutive=0;candidate.clear();pre.addLast(block);while(pre.size()>preFrames)pre.removeFirst();state("capture","Đang bỏ khoảng lặng");}
                state("segments",segments);state("keptMs",keptBytes*1000L/(rate*2));
            }
            writeHeader(out,keptBytes,rate);completed=true;
        }catch(Exception ex){state("error",ex.toString());log("Lỗi ghi: "+ex);}finally{
            recording=false;state("running",false);state("level",0);state("capture","Đã dừng");
            if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();audio=null;}
            if(completed){try{if(keptBytes>0){String path=exportToMusic(temp);state("file",path);log("Đã lưu "+segments+" đoạn, "+(keptBytes/32000)+" giây âm thanh");if(!temp.delete())log("Không xóa được file tạm");}else{state("file","Không có đoạn vượt ngưỡng; không tạo WAV");temp.delete();log("Không phát hiện đoạn tiếng nói");}}catch(Exception ex){state("error","Xuất file: "+ex);state("file","File tạm: "+temp.getAbsolutePath());log("Lỗi xuất file: "+ex);}}
            stopForeground(true);stopSelf();
        }
    }
    private String exportToMusic(File src)throws IOException{ContentValues v=new ContentValues();v.put(MediaStore.Audio.Media.DISPLAY_NAME,src.getName());v.put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav");v.put(MediaStore.Audio.Media.RELATIVE_PATH,Environment.DIRECTORY_MUSIC+"/OrbicRecorder");v.put(MediaStore.Audio.Media.IS_PENDING,1);Uri uri=getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("MediaStore insert thất bại");boolean ok=false;try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Không mở được file đích");byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();ok=true;}finally{if(!ok)getContentResolver().delete(uri,null,null);}ContentValues done=new ContentValues();done.put(MediaStore.Audio.Media.IS_PENDING,0);getContentResolver().update(uri,done,null,null);return "Music/OrbicRecorder/"+src.getName();}
    private void writeHeader(RandomAccessFile o,long size,int rate)throws IOException{o.seek(0);o.writeBytes("RIFF");le32(o,size+36);o.writeBytes("WAVEfmt ");le32(o,16);le16(o,1);le16(o,1);le32(o,rate);le32(o,rate*2);le16(o,2);le16(o,16);o.writeBytes("data");le32(o,size);}
    private void le16(RandomAccessFile o,long v)throws IOException{o.write((int)v&255);o.write((int)(v>>8)&255);}private void le32(RandomAccessFile o,long v)throws IOException{le16(o,v);le16(o,v>>16);}
    @Override public void onDestroy(){recording=false;super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
