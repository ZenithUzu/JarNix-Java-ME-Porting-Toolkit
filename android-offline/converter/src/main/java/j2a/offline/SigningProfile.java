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

/** One exportable signing identity for all new J2A game APKs. Backups are password protected. */
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
        if(!ks.isKeyEntry(ALIAS))throw new GeneralSecurityException("El respaldo no contiene la firma J2A");
        return ks;
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
    public synchronized void exportTo(OutputStream out,String backupPassword)throws Exception{
        if(backupPassword==null||backupPassword.length()<12)throw new IllegalArgumentException("La contraseña necesita al menos 12 caracteres");
        KeyStore local=identity();KeyStore backup=KeyStore.getInstance("PKCS12");backup.load(null,backupPassword.toCharArray());
        backup.setKeyEntry(ALIAS,local.getKey(ALIAS,localPassword().toCharArray()),backupPassword.toCharArray(),local.getCertificateChain(ALIAS));
        backup.store(out,backupPassword.toCharArray());
    }
    public synchronized void importFrom(InputStream in,String backupPassword)throws Exception{
        if(file().exists())throw new IllegalStateException("Ya existe una firma local. No se reemplaza para proteger las actualizaciones.");
        File temporary=new File(context.getCacheDir(),"import-signing.p12");
        try {
            try(OutputStream out=new FileOutputStream(temporary)){byte[] buf=new byte[8192];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>1024*1024)throw new IOException("Respaldo demasiado grande");out.write(buf,0,n);}}
            KeyStore imported=load(temporary,backupPassword);
            KeyStore local=KeyStore.getInstance("PKCS12");String password=localPassword();local.load(null,password.toCharArray());
            local.setKeyEntry(ALIAS,imported.getKey(ALIAS,backupPassword.toCharArray()),password.toCharArray(),imported.getCertificateChain(ALIAS));
            write(local,file(),password);
        }finally{temporary.delete();}
    }
}
