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
        Uri root=tree(a);
        Uri saves=child(a,root,"saves",true);
        return child(a,saves,a.getPackageName(),true);
    }
    public static void backup(Activity a)throws Exception {
        File rms=new File(a.getFilesDir(),"rms");Uri destination=gameDir(a);
        File[] files=rms.listFiles((dir,name)->name.endsWith(".rms"));
        if(files==null)return;
        for(File file:files){
            Uri target=child(a,destination,file.getName(),false);
            try(InputStream in=new FileInputStream(file);OutputStream out=a.getContentResolver().openOutputStream(target,"wt")){
                if(out==null)throw new IOException("No se pudo escribir "+file.getName());
                copy(in,out);
            }
        }
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
