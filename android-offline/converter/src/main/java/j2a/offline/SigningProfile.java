package j2a.offline;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * One signing identity for every game APK. The exported keys file (.p12) uses a fixed built-in
 * password, so exporting/importing never asks the user for one. JarNix (PC) reads the same file.
 */
public final class SigningProfile {
    private static final String FILE = "signing/identity.p12";
    private static final String ALIAS = "j2a-games";
    private final Context context;
    public SigningProfile(Context context){this.context=context.getApplicationContext();}
    private File file(){return new File(context.getFilesDir(),FILE);}
    private String localPassword(){
        SharedPreferences p=context.getSharedPreferences("signing",0);
        String value=p.getString("local-password",null);
        if(value!=null)return value;
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        value=Base64.getEncoder().encodeToString(bytes);
        if(!p.edit().putString("local-password",value).commit())throw new IllegalStateException("No se pudo almacenar la clave local");
        return value;
    }
    private KeyStore load(File source,String password)throws Exception{
        KeyStore ks=KeyStore.getInstance("PKCS12");
        try(InputStream in=new FileInputStream(source)){ks.load(in,password.toCharArray());}
        if(keyAlias(ks)==null)throw new GeneralSecurityException("El archivo no contiene llaves de firma");
        return ks;
    }
    /** "j2a-games", or the first key of a .p12 made elsewhere (e.g. an Android debug keystore). */
    private static String keyAlias(KeyStore ks)throws KeyStoreException{
        if(ks.isKeyEntry(ALIAS))return ALIAS;
        for(Enumeration<String> e=ks.aliases();e.hasMoreElements();){String a=e.nextElement();if(ks.isKeyEntry(a))return a;}
        return null;
    }
    private void write(KeyStore ks,File target,String password)throws Exception{
        File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("No se pudo crear la carpeta de firmas");
        File tmp=new File(parent,target.getName()+".tmp");
        try(OutputStream out=new FileOutputStream(tmp)){ks.store(out,password.toCharArray());}
        try{java.nio.file.Files.move(tmp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
        catch(java.nio.file.AtomicMoveNotSupportedException e){java.nio.file.Files.move(tmp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
    }
    public synchronized KeyStore identity()throws Exception{
        File target=file();String password=localPassword();
        if(target.exists())return load(target,password);
        KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA");gen.initialize(3072);
        KeyPair pair=gen.generateKeyPair();
        Date from=new Date(System.currentTimeMillis()-86400000L),to=new Date(System.currentTimeMillis()+30L*365*86400000L);
        X500Name name=new X500Name("CN=J2A Game Signing");
        JcaX509v3CertificateBuilder certBuilder=new JcaX509v3CertificateBuilder(name,new BigInteger(128,new SecureRandom()),from,to,name,pair.getPublic());
        X509Certificate cert=new JcaX509CertificateConverter().getCertificate(certBuilder.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        cert.verify(pair.getPublic());
        KeyStore ks=KeyStore.getInstance("PKCS12");ks.load(null,password.toCharArray());
        ks.setKeyEntry(ALIAS,pair.getPrivate(),password.toCharArray(),new java.security.cert.Certificate[]{cert});
        write(ks,target,password);return ks;
    }
    public synchronized PrivateKey key()throws Exception{return (PrivateKey)identity().getKey(ALIAS,localPassword().toCharArray());}
    public synchronized X509Certificate certificate()throws Exception{return (X509Certificate)identity().getCertificate(ALIAS);}
    /** Fixed password of exported key files; shared with JarNix (tool/j2a_tool/signing.py). */
    public static final String FILE_PASSWORD = "j2droid";
    /** Thrown when an old backup was protected with a personal password. */
    public static final class NeedsPassword extends GeneralSecurityException { NeedsPassword(){super("Este archivo de llaves tiene contraseña");} }
    public synchronized boolean exists(){return file().exists();}
    public synchronized void exportTo(OutputStream out)throws Exception{
        KeyStore local=identity();KeyStore backup=KeyStore.getInstance("PKCS12");backup.load(null,FILE_PASSWORD.toCharArray());
        backup.setKeyEntry(ALIAS,local.getKey(ALIAS,localPassword().toCharArray()),FILE_PASSWORD.toCharArray(),local.getCertificateChain(ALIAS));
        backup.store(out,FILE_PASSWORD.toCharArray());
    }
    /** Replaces the current keys. password is only needed for old backups made with a personal password. */
    public synchronized void importFrom(InputStream in,String password)throws Exception{
        File temporary=new File(context.getCacheDir(),"import-signing.p12");
        try {
            try(OutputStream out=new FileOutputStream(temporary)){byte[] buf=new byte[8192];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>1024*1024)throw new IOException("Archivo de llaves demasiado grande");out.write(buf,0,n);}}
            String secret=password==null||password.isEmpty()?FILE_PASSWORD:password;
            KeyStore imported;
            try{imported=load(temporary,secret);}
            catch(IOException wrongPassword){if(password==null)throw new NeedsPassword();throw new GeneralSecurityException("Contraseña incorrecta");}
            KeyStore local=KeyStore.getInstance("PKCS12");String localPassword=localPassword();local.load(null,localPassword.toCharArray());
            String alias=keyAlias(imported);
            local.setKeyEntry(ALIAS,imported.getKey(alias,secret.toCharArray()),localPassword.toCharArray(),imported.getCertificateChain(alias));
            write(local,file(),localPassword);
        }finally{temporary.delete();}
    }
}
