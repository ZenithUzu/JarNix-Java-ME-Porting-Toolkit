package j2a.offline;

import android.app.*;
import android.content.*;
import android.os.IBinder;
import java.io.File;
import java.util.concurrent.*;

/** Keep local compilation alive when the user switches apps or locks the screen. */
public final class BuildService extends Service {
    static final String STATE="offline_build";
    private static volatile boolean running;
    static boolean isRunning(){return running;}
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private StringBuilder lines=new StringBuilder();
    private Notification notification(String text) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"j2a-build").setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("J2Droid · creando APK").setContentText(text).setContentIntent(open).setOngoing(true).build();
    }
    @Override public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("j2a-build","Conversión local de juegos",NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        startForeground(1,notification("Preparando conversión local…"));
        if(running)return START_NOT_STICKY;
        if(intent==null || intent.getStringExtra("jar")==null){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
        running=true;lines=new StringBuilder();getSharedPreferences(STATE,0).edit().putBoolean("busy",true).putString("result","").putString("log","").apply();
        String jar=intent.getStringExtra("jar"),jad=intent.getStringExtra("jad"),name=intent.getStringExtra("name");
        worker.execute(()->{
            try {
                File work=new File(getCacheDir(),"j2a-build-"+System.currentTimeMillis());
                try {
                    File result=new OfflineBuilder(this,this::log).build(new File(jar),jad==null?null:new File(jad),name,work);
                    File output=new File(getFilesDir(),"ready.apk");
                    java.nio.file.Files.move(result.toPath(),output.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    getSharedPreferences(STATE,0).edit().putString("result",output.getAbsolutePath()).apply();
                    recordBuild(name, "Correcto", lines.toString());
                } finally { deleteTree(work); }
            }catch(Throwable error){log("Error: "+error.toString());recordBuild(name,"Falló",error.toString());}
            finally {
                running=false;getSharedPreferences(STATE,0).edit().putBoolean("busy",false).apply();
                stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);
            }
        });
        return START_NOT_STICKY;
    }
    private void recordBuild(String name,String status,String detail){
        try {
            org.json.JSONArray history=new org.json.JSONArray(getSharedPreferences(STATE,0).getString("history","[]"));
            org.json.JSONObject entry=new org.json.JSONObject();
            entry.put("time",System.currentTimeMillis());entry.put("name",name==null?"Juego":name);
            entry.put("status",status);entry.put("detail",detail.length()>1000?detail.substring(detail.length()-1000):detail);
            history.put(entry);
            org.json.JSONArray recent=new org.json.JSONArray();
            for(int i=Math.max(0,history.length()-40);i<history.length();i++)recent.put(history.get(i));
            getSharedPreferences(STATE,0).edit().putString("history",recent.toString()).apply();
        }catch(Exception ignored){}
    }
    private static void deleteTree(File file) {
        if(file==null || !file.exists())return;
        File[] children=file.listFiles();
        if(children!=null)for(File child:children)deleteTree(child);
        file.delete();
    }
    private synchronized void log(String text) {
        lines.append(text).append('\n');if(lines.length()>64000)lines.delete(0,lines.length()-64000);
        getSharedPreferences(STATE,0).edit().putString("log",lines.toString()).apply();
        getSystemService(NotificationManager.class).notify(1,notification(text));
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){worker.shutdown();super.onDestroy();}
}
