package j2a.core;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;

/** User-authorized SAF backup of the private Java ME RMS stores. */
public final class SaveSaf {
    private static final int REQUEST = 7412;
    private SaveSaf() {}
    private static final java.util.concurrent.ExecutorService queue =
        java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final java.util.concurrent.ConcurrentHashMap<String,Long> pending =
        new java.util.concurrent.ConcurrentHashMap<String,Long>();
    /** Called only after RMS has been safely committed to private storage. */
    public static void saved(File file) {
        Activity a=Host.getActivity();
        if(a==null || !file.isFile())return;
        if(a.getPreferences(0).getString("j2a-saves-tree",null)==null)return;
        pending.put(file.getName(),file.lastModified());
        queue.execute(() -> {
            Long expected=pending.get(file.getName());
            if(expected==null)return;
            try {
                backupOne(a,file);
                pending.remove(file.getName(),expected);
            }catch(Exception e){
                android.util.Log.e("j2a-saves","SAF backup failed for "+file.getName(),e);
                Host.runOnUi(() -> error(a,e));
            }
        });
    }
    private static void backupOne(Activity a,File file)throws Exception {
        Uri destination=gameDir(a);
        Uri target=child(a,destination,file.getName(),false);
        // Copy to a new staging document before replacing the visible backup.
        Uri staging=DocumentsContract.createDocument(a.getContentResolver(),destination,
            "application/octet-stream",file.getName()+".pending");
        if(staging==null)throw new IOException("No se pudo crear copia temporal");
        boolean completed=false;
        try {
            try(InputStream in=new FileInputStream(file);
                OutputStream out=a.getContentResolver().openOutputStream(staging,"wt")){
                if(out==null)throw new IOException("No se pudo escribir el respaldo");
                copy(in,out);
            }
            // SAF providers may not support atomic replacement; retain old backup until copy succeeds.
            try(InputStream in=a.getContentResolver().openInputStream(staging);
                OutputStream out=a.getContentResolver().openOutputStream(target,"wt")){
                if(in==null||out==null)throw new IOException("No se pudo publicar respaldo");
                copy(in,out);
            }
            completed=true;
        }finally{
            try{DocumentsContract.deleteDocument(a.getContentResolver(),staging);}catch(Exception ignored){}
        }
        if(!completed)throw new IOException("Respaldo incompleto");
    }
    public static void choose(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(intent, REQUEST);
    }
    public static boolean handle(Activity activity,int request,int result,Intent data) {
        if(request!=REQUEST)return false;
        if(result==Activity.RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri=data.getData();
            try {
                activity.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                activity.getPreferences(0).edit().putString("j2a-saves-tree",uri.toString()).apply();
                android.widget.Toast.makeText(activity,"Carpeta Java autorizada",android.widget.Toast.LENGTH_LONG).show();
            } catch(Exception e){error(activity,e);}
        }
        return true;
    }
    private static Uri tree(Activity a)throws Exception {
        String value=a.getPreferences(0).getString("j2a-saves-tree",null);
        if(value==null)throw new IOException("Primero selecciona la carpeta Java");
        Uri uri=Uri.parse(value);
        boolean permitted=false;
        for(android.content.UriPermission p:a.getContentResolver().getPersistedUriPermissions())
            if(p.getUri().equals(uri)&&p.isReadPermission()&&p.isWritePermission())permitted=true;
        if(!permitted)throw new IOException("Permiso de carpeta caducado; vuelve a seleccionarla");
        return uri;
    }
    private static Uri child(Activity a,Uri parent,String name,boolean directory)throws Exception {
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(parent,DocumentsContract.getDocumentId(parent));
        try(android.database.Cursor cursor=a.getContentResolver().query(children,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(cursor!=null)while(cursor.moveToNext())if(name.equals(cursor.getString(1)))
                return DocumentsContract.buildDocumentUriUsingTree(parent,cursor.getString(0));
        }
        return DocumentsContract.createDocument(a.getContentResolver(),parent,
                directory?DocumentsContract.Document.MIME_TYPE_DIR:"application/octet-stream",name);
    }
    private static void copy(InputStream in,OutputStream out)throws IOException {
        byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
    }
    private static Uri gameDir(Activity a)throws Exception {
        Uri selected=tree(a);
        Uri root=DocumentsContract.buildDocumentUriUsingTree(selected,DocumentsContract.getTreeDocumentId(selected));
        Uri saves=child(a,root,"saves",true);
        return child(a,saves,a.getPackageName(),true);
    }
    public static void backup(Activity a)throws Exception {
        File rms=new File(a.getFilesDir(),"rms");Uri destination=gameDir(a);
        File[] files=rms.listFiles((dir,name)->name.endsWith(".rms"));
        if(files==null)return;
        for(File file:files)backupOne(a,file);
    }
    public static void restore(Activity a)throws Exception {
        Uri source=gameDir(a);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(source,DocumentsContract.getDocumentId(source));
        File rms=new File(a.getFilesDir(),"rms");if(!rms.isDirectory()&&!rms.mkdirs())throw new IOException("No se pudo crear RMS");
        try(android.database.Cursor cursor=a.getContentResolver().query(children,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(cursor==null)return;
            while(cursor.moveToNext()){
                String name=cursor.getString(1);
                if(!name.endsWith(".rms")||name.contains("/")||name.contains("\\")||name.contains(".."))continue;
                Uri doc=DocumentsContract.buildDocumentUriUsingTree(source,cursor.getString(0));
                File target=new File(rms,name);
                if(target.exists())continue; // never silently replace newer local saves
                File temp=new File(rms,name+".import");
                try(InputStream in=a.getContentResolver().openInputStream(doc);OutputStream out=new FileOutputStream(temp)){
                    if(in==null)throw new IOException("No se pudo leer "+name);
                    copy(in,out);
                }
                if(!temp.renameTo(target))throw new IOException("No se pudo restaurar "+name);
            }
        }
    }
    public static void error(Activity a,Exception e){
        android.widget.Toast.makeText(a,"Partidas: "+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();
    }
}
