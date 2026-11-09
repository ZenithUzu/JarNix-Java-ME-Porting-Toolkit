package j2a.offline;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Android-only UI: choose JAR (+ optional JAD), convert locally, save APK. */
public final class MainActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService files=Executors.newSingleThreadExecutor();
    private Button pickJar,pickJad,clearJad,convert,save;
    private TextView selected,status,log;
    private ProgressBar progress;
    private boolean copying;
    private String backupPassword="";
    private String previousLog="";
    private final Runnable poll=new Runnable(){public void run(){refresh();ui.postDelayed(this,700);}};
    private int accent(){return Build.VERSION.SDK_INT>=31?getColor(android.R.color.system_accent1_500):0xff536dfe;}
    private int surface(){return Build.VERSION.SDK_INT>=31?getColor(android.R.color.system_neutral1_50):0xfff7f8fc;}
    private int ink(){return Build.VERSION.SDK_INT>=31?getColor(android.R.color.system_neutral1_900):0xff1b1b21;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(LinearLayout parent,String label,View.OnClickListener action){Button b=new Button(this);b.setText(label);b.setTextColor(accent());b.setAllCaps(false);b.setOnClickListener(action);parent.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));return b;}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setStatusBarColor(surface());getWindow().setNavigationBarColor(surface());getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        SharedPreferences saved=getSharedPreferences(BuildService.STATE,0);
        if(saved.getBoolean("busy",false) && !BuildService.isRunning())saved.edit().putBoolean("busy",false).putString("log",saved.getString("log","")+"\nLa conversión anterior se interrumpió. Puedes intentarlo de nuevo.\n").apply();
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(24),dp(20),dp(12));root.setBackgroundColor(surface());
        root.addView(text("J2A · SIN CONEXIÓN",12,accent(),true));
        root.addView(text("De Java a Android",28,ink(),true));
        TextView detail=text("JAR → DEX → APK firmada\nTodo en este teléfono. Sin PC ni conexión.",14,ink(),false);detail.setPadding(0,dp(10),0,dp(16));root.addView(detail);
        selected=text("Selecciona un juego .jar",15,accent(),true);root.addView(selected);
        pickJar=button(root,"01  ·  ELEGIR JUEGO JAR",v->pick(1));
        LinearLayout jadRow=new LinearLayout(this);jadRow.setOrientation(LinearLayout.HORIZONTAL);root.addView(jadRow);
        pickJad=new Button(this);pickJad.setText("Añadir JAD (opcional)");pickJad.setOnClickListener(v->pick(2));jadRow.addView(pickJad,new LinearLayout.LayoutParams(0,dp(48),1));
        clearJad=new Button(this);clearJad.setText("Quitar JAD");clearJad.setOnClickListener(v->{getPreferences(0).edit().remove("jad").apply();refresh();});jadRow.addView(clearJad);
        convert=button(root,"02  ·  CREAR APK EN ESTE TELÉFONO",v->startBuild());
        save=button(root,"03  ·  GUARDAR APK",v->saveApk());
        button(root,"Respaldar firma (.p12)",v->askBackup(false));
        button(root,"Restaurar firma (.p12)",v->askBackup(true));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
        status=text("Motor y compilador incluidos · Android 8 o superior",12,ink(),false);root.addView(status);
        ScrollView scroll=new ScrollView(this);log=text("",12,ink(),false);log.setTypeface(Typeface.MONOSPACE);log.setTextIsSelectable(true);log.setPadding(0,dp(12),0,dp(12));scroll.addView(log);root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(160)));
        TextView note=text("La clave de firma pertenece a esta instalación. No borres los datos de la herramienta si quieres actualizar tus juegos con la misma firma.",11,ink(),false);root.addView(note);
        ScrollView page=new ScrollView(this);page.setFillViewport(true);page.addView(root);setContentView(page);
    }
    @Override protected void onResume(){super.onResume();ui.post(poll);}
    @Override protected void onPause(){ui.removeCallbacks(poll);super.onPause();}
    private void pick(int request){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,request);}
    private void refresh() {
        SharedPreferences state=getSharedPreferences(BuildService.STATE,0);boolean busy=copying||state.getBoolean("busy",false);
        String jar=getPreferences(0).getString("jar",""),jad=getPreferences(0).getString("jad",""),result=state.getString("result","");
        selected.setText(getPreferences(0).getString("name","Selecciona un juego .jar")+(jad.isEmpty()?"":"\nJAD añadido"));
        pickJar.setEnabled(!busy);pickJad.setEnabled(!busy);clearJad.setEnabled(!busy&&!jad.isEmpty());convert.setEnabled(!busy&&!jar.isEmpty());save.setEnabled(!busy&&!result.isEmpty()&&new File(result).isFile());progress.setVisibility(busy?View.VISIBLE:View.GONE);
        status.setText(busy?"Trabajando en el teléfono… Puedes volver a esta pantalla al terminar.":!result.isEmpty()?"APK lista. Pulsa Guardar APK para elegir el destino.":"Sin proyectos ni ADB · Solo APK");
        String lines=state.getString("log","");if(!lines.equals(previousLog)){log.setText(lines);previousLog=lines;}
    }
    private void startBuild() {
        SharedPreferences prefs=getPreferences(0);String jar=prefs.getString("jar",null),jad=prefs.getString("jad",null);
        if(jar==null)return;
        Intent intent=new Intent(this,BuildService.class).putExtra("jar",jar).putExtra("name",prefs.getString("name","Juego.jar"));if(jad!=null)intent.putExtra("jad",jad);
        SharedPreferences state=getSharedPreferences(BuildService.STATE,0);
        state.edit().putBoolean("busy",true).putString("result","").putString("log","").apply();refresh();
        try { startForegroundService(intent); }
        catch(RuntimeException error){state.edit().putBoolean("busy",false).putString("log","No se pudo iniciar la conversión: "+error).apply();refresh();}
    }
    private void askBackup(boolean restore){
        if(BuildService.isRunning()){Toast.makeText(this,"Espera a que termine la conversión",Toast.LENGTH_LONG).show();return;}
        EditText field=new EditText(this);field.setSingleLine(true);field.setHint("Contraseña de respaldo (mínimo 12 caracteres)");
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new android.app.AlertDialog.Builder(this).setTitle(restore?"Restaurar firma":"Respaldar firma")
            .setMessage(restore?"Solo se permite restaurar antes de crear la primera firma. Necesitas la contraseña original.":"Guarda el archivo y la contraseña en un lugar seguro. Sin ambos, no podrás recuperar la firma.")
            .setView(field).setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->{
                String password=field.getText().toString();
                if(password.length()<12){Toast.makeText(this,"Usa al menos 12 caracteres",Toast.LENGTH_LONG).show();return;}
                backupPassword=password;
                Intent intent=new Intent(restore?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_CREATE_DOCUMENT)
                    .setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE);
                if(!restore)intent.putExtra(Intent.EXTRA_TITLE,"j2a-firma.p12");
                startActivityForResult(intent,restore?5:4);
            }).show();
    }
    private void saveApk(){String result=getSharedPreferences(BuildService.STATE,0).getString("result","");if(result.isEmpty())return;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/vnd.android.package-archive").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,new File(result).getName());startActivityForResult(intent,3);}
    @Override protected void onActivityResult(int request,int code,Intent intent) {
        super.onActivityResult(request,code,intent);if(code!=RESULT_OK || intent==null || intent.getData()==null)return;
        Uri uri=intent.getData();copying=true;refresh();
        files.execute(()->{
            try {
                if(request==4 || request==5){
                    SigningProfile profile=new SigningProfile(this);
                    if(request==4){try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("No se pudo guardar");profile.exportTo(out,backupPassword);}}
                    else {try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("No se pudo leer");profile.importFrom(in,backupPassword);}}
                    ui.post(()->Toast.makeText(this,request==4?"Firma respaldada":"Firma restaurada",Toast.LENGTH_LONG).show());
                }else if(request==3) {
                    String result=getSharedPreferences(BuildService.STATE,0).getString("result","");
                    try(InputStream in=new FileInputStream(result);OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("No se pudo abrir el destino");OfflineBuilder.copy(in,out);}
                    new File(result).delete();getSharedPreferences(BuildService.STATE,0).edit().remove("result").apply();
                    ui.post(()->Toast.makeText(this,"APK guardada. Archivo temporal eliminado.",Toast.LENGTH_LONG).show());
                }else {
                    String name="Juego.jar";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
                    File target=new File(getFilesDir(),request==1?"input.jar":"input.jad"),temporary=new File(getFilesDir(),"input.tmp");
                    try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temporary)){if(in==null)throw new IOException("No se pudo leer el archivo");out.write(Archives.read(in,request==1?64*1024*1024:1024*1024));}
                    java.nio.file.Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    SharedPreferences.Editor edit=getPreferences(0).edit().putString(request==1?"jar":"jad",target.getAbsolutePath());
                    if(request==1){edit.putString("name",name).remove("jad");getSharedPreferences(BuildService.STATE,0).edit().putString("result","").putString("log","").apply();}edit.apply();
                }
            }catch(Exception error){ui.post(()->Toast.makeText(this,error.toString(),Toast.LENGTH_LONG).show());}
            finally{backupPassword="";copying=false;ui.post(this::refresh);}
        });
    }
    @Override protected void onDestroy(){ui.removeCallbacks(poll);files.shutdown();super.onDestroy();}
}
