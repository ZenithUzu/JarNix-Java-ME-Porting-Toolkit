package j2a.offline;

import j2a.core.ResolutionProfiles;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Inspect a JAR without executing game code. Keep original J2ME namespaces for the bundled runtime. */
public final class GameJar {
    public final Map<String,byte[]> entries;
    public final Map<String,String> attributes;
    public final String name, vendor, midlet, appId, version;
    public final int width,height,versionCode;
    public final String confidence;
    public final byte[] icon;
    private static final String[] PLATFORM={"java/","javax/microedition/","javax/bluetooth/","javax/wireless/","com/nokia/","com/samsung/","com/siemens/","com/motorola/","com/sonyericsson/","com/mascotcapsule/","com/vodafone/","com/j_phone/","com/jblend/","com/kddi/","com/mot/","com/mexa/","com/sprintpcs/","com/sun/midp/","org/microemu/","ru/woesss/","j2a/"};
    public static boolean platform(String entry){for(String prefix:PLATFORM)if(entry.startsWith(prefix))return true;return false;}
    static Map<String,String> attributes(byte[] bytes) {
        Map<String,String> values=new LinkedHashMap<>();String last=null;
        for(String line:new String(bytes,StandardCharsets.UTF_8).split("\\r?\\n")) {
            if(line.startsWith(" ") && last!=null){values.put(last,values.get(last)+line.substring(1));continue;}
            int colon=line.indexOf(':');if(colon>0){last=line.substring(0,colon).trim();values.put(last,line.substring(colon+1).trim());}
        }
        return values;
    }
    private String attr(String key,String fallback){for(Map.Entry<String,String> e:attributes.entrySet())if(e.getKey().equalsIgnoreCase(key))return e.getValue();return fallback;}
    public GameJar(File jar,File jad,String filename) throws Exception {
        entries=Archives.load(jar);byte[] manifest=null;
        for(String path:entries.keySet())if(path.equalsIgnoreCase("META-INF/MANIFEST.MF"))manifest=entries.get(path);
        if(manifest==null)throw new IOException("El JAR no contiene MANIFEST.MF");
        attributes=attributes(manifest);
        if(jad!=null){Map<String,String> extra;try(InputStream in=new FileInputStream(jad)){extra=attributes(Archives.read(in,1024*1024));}
            for(Map.Entry<String,String> e:extra.entrySet()){attributes.keySet().removeIf(k->k.equalsIgnoreCase(e.getKey()));attributes.put(e.getKey(),e.getValue());}}
        String[] first=attr("MIDlet-1","").split(",",-1);
        if(first.length<3 || first[2].trim().isEmpty())throw new IOException("Falta MIDlet-1. Añade el JAD si contiene los datos del juego.");
        midlet=first[2].trim();name=attr("MIDlet-Name",first[0].trim().isEmpty()?filename:first[0].trim());version=attr("MIDlet-Version","1.0");vendor=attr("MIDlet-Vendor","");
        if(!entries.containsKey(midlet.replace('.','/')+".class"))throw new IOException("La clase MIDlet declarada no existe: "+midlet);
        String identity=attr("MIDlet-Vendor","")+"\n"+name+"\n"+midlet;
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));StringBuilder id=new StringBuilder("com.j2a.game.g");
        for(int i=0;i<10;i++)id.append(String.format(Locale.ROOT,"%02x",digest[i]&255));
        String declared=attr("MIDlet-Name","").trim();
        String official=officialPackage(vendor,declared.isEmpty()?first[0].trim():declared,entries.keySet());
        appId=official!=null?official:id.toString();
        int v=0;Matcher digits=Pattern.compile("\\d+").matcher(version);int parts=0;
        while(parts++<3){int number=digits.find()?Math.min(99,Integer.parseInt(digits.group().substring(0,Math.min(2,digits.group().length())))):0;v=v*100+number;}versionCode=Math.max(1,v);
        String iconPath=attr("MIDlet-Icon",first[1].trim()).replaceFirst("^/","");byte[] image=entries.get(iconPath);
        icon=image!=null && image.length>=24 && image[0]==(byte)0x89 && image[1]=='P' && image[2]=='N' && image[3]=='G'?image:null;
        Set<String> known=new HashSet<>();for(int[] r:ResolutionProfiles.CLASSIC)known.add(r[0]+"x"+r[1]);
        Map<String,Map<String,Integer>> scores=new LinkedHashMap<>();
        String[] keys={"J2A-Screen-Size","Nokia-MIDlet-Original-Display-Size","MIDlet-Screen-Size","Nokia-MIDlet-Target-Display-Size"};int[] weights={1200,1100,1000,900};
        Pattern size=Pattern.compile("(?<!\\d)(\\d{2,4})\\s*[,xX*_-]\\s*(\\d{2,4})(?!\\d)");
        for(int i=0;i<keys.length;i++){Matcher m=size.matcher(attr(keys[i],""));if(m.find())add(scores,m.group(1)+"x"+m.group(2),"manifest",weights[i]);}
        Matcher named=size.matcher(filename);while(named.find())add(scores,named.group(1)+"x"+named.group(2),"filename",80);
        Map<String,Integer> pictures=new HashMap<>();
        for(Map.Entry<String,byte[]> e:entries.entrySet()) {
            String path=e.getKey(),lower=path.toLowerCase(Locale.ROOT);byte[] data=e.getValue();
            if(path.endsWith(".class") && !platform(path)) {
                ClassPatcher parsed=new ClassPatcher(data);
                for(Map.Entry<String,Integer> evidence:parsed.drawingSizes(known).entrySet()) {
                    String[] pair=evidence.getKey().split(":");int old=scores.getOrDefault(pair[0],Collections.emptyMap()).getOrDefault(pair[1],0);
                    add(scores,pair[0],pair[1],Math.min(pair[1].equals("draw")?125:55,old+evidence.getValue()));
                }
            } else if(!path.equals(iconPath) && !lower.matches(".*(?:icon|atlas|sprite|font|tileset).*")) {
                int[] wh=imageSize(data);
                if(wh!=null){String pair=wh[0]+"x"+wh[1];if(known.contains(pair) && wh[0]!=wh[1])pictures.put(pair,pictures.getOrDefault(pair,0)+1);}
            }
        }
        for(Map.Entry<String,Integer> e:pictures.entrySet())add(scores,e.getKey(),"images",Math.min(50,16+9*e.getValue()));
        String best="240x320";int top=0,second=0;
        for(Map.Entry<String,Map<String,Integer>> candidate:scores.entrySet()) {
            int total=0;for(int points:candidate.getValue().values())total+=points;
            if(total>top){second=top;top=total;best=candidate.getKey();}else second=Math.max(second,total);
        }
        String[] wh=best.split("x");width=Integer.parseInt(wh[0]);height=Integer.parseInt(wh[1]);
        confidence=top>=900 || top>=110 && top-second>=30?"alta":top>=60 && top-second>=15?"media":"baja";
    }
    /** SDK placeholders that are not a real developer name. Keep in sync with generate.GENERIC_VENDORS. */
    private static final Set<String> GENERIC_VENDORS=new HashSet<>(Arrays.asList("","unknown","vendor","midlet suite vendor",
            "midlet-vendor","none","n/a","na","null","-","author","company","my company","mycompany","sun microsystems, inc."));
    static String clean(String text){return (text==null?"":text).replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]"," ").replaceAll("\\s+"," ").trim().replaceAll("^\\.+|\\.+$","").trim();}
    /** Official developer from MIDlet-Vendor, or "" for SDK placeholders. */
    public static String officialVendor(String vendor){String v=clean(vendor);return GENERIC_VENDORS.contains(v.toLowerCase(Locale.ROOT))?"":v;}
    /** "Asphalt 4 (Gameloft) v1.0.2" — same rule as generate.apk_file_name on PC. */
    public static String apkFileName(String name,String vendor,String version){
        String game=clean(name);if(game.isEmpty())game="juego";String dev=officialVendor(vendor);
        if(!dev.isEmpty() && game.toLowerCase(Locale.ROOT).contains(dev.toLowerCase(Locale.ROOT)))dev="";
        String ver=(version==null?"":version).replaceAll("[^0-9A-Za-z.]","").replaceAll("^\\.+|\\.+$","");
        String tail=(dev.isEmpty()?"":" ("+dev+")")+(ver.isEmpty()?"":" v"+ver);if(tail.length()>60)tail=tail.substring(0,60);
        String head=game.substring(0,Math.max(1,Math.min(game.length(),120-tail.length()))).trim();return (head+tail).trim();
    }
    public String apkFileName(){return apkFileName(name,vendor,version);}
    private static final Set<String> LEGAL_WORDS=new HashSet<>(Arrays.asList("s","a","sa","inc","ltd","llc","gmbh","corp",
            "corporation","co","company","limited","oy","ab","sl","srl","spa","plc","kk","bv","ag","nv","sas","sarl","pty","lda"));
    private static final Set<String> JAVA_KEYWORDS=new HashSet<>(Arrays.asList(("abstract assert boolean break byte case catch char class const continue default do double "
            +"else enum extends final finally float for goto if implements import instanceof int interface long native new "
            +"package private protected public return short static strictfp super switch synchronized this throw throws "
            +"transient try void volatile while true false null var record yield").split(" ")));
    static List<String> words(String text){
        String ascii=java.text.Normalizer.normalize(text==null?"":text,java.text.Normalizer.Form.NFKD).replaceAll("[^\\x00-\\x7F]","").toLowerCase(Locale.ROOT);
        List<String> out=new ArrayList<>();Matcher m=Pattern.compile("[a-z0-9]+").matcher(ascii);while(m.find())out.add(m.group());return out;
    }
    static String segment(List<String> words){
        String seg=String.join("",words);if(seg.length()>30)seg=seg.substring(0,30);if(seg.isEmpty())return "";
        if(Character.isDigit(seg.charAt(0)))seg="g"+seg;return JAVA_KEYWORDS.contains(seg)?seg+"_":seg;
    }
    /** com.&lt;developer&gt;.&lt;game&gt; ("Gameloft S.A." + "Asphalt 4" -> com.gameloft.asphalt4), same rule as
     *  generate.official_package on PC; null when there is no real vendor/name or the game's classes clash. */
    public static String officialPackage(String vendor,String gameName,Collection<String> entries){
        List<String> dev=words(officialVendor(vendor));while(!dev.isEmpty() && LEGAL_WORDS.contains(dev.get(dev.size()-1)))dev.remove(dev.size()-1);
        String d=segment(dev),g=segment(words(gameName));if(d.isEmpty()||g.isEmpty())return null;
        String pkg="com."+d+"."+g,base=pkg.replace('.','/')+"/";
        for(String c:entries)if(c.equals(base+"MainActivity.class")||c.equals(base+"BuildConfig.class")||c.equals(base+"R.class")
                ||c.startsWith(base+"R$")||c.startsWith(base+"engine/"))return null;
        return pkg;
    }
    static void add(Map<String,Map<String,Integer>> scores,String pair,String group,int value) {
        String[] parts=pair.split("x");try {int w=Integer.parseInt(parts[0]),h=Integer.parseInt(parts[1]);if(w<64||h<64||w>2048||h>2048)return;
            scores.computeIfAbsent(w+"x"+h,k->new HashMap<>()).merge(group,value,Math::max);
        }catch(NumberFormatException ignored){}
    }
    static int[] imageSize(byte[] data) {
        if(data.length>=24 && data[0]==(byte)0x89 && data[1]=='P' && data[2]=='N' && data[3]=='G')return new int[]{ClassPatcher.intAt(data,16),ClassPatcher.intAt(data,20)};
        if(data.length>=10 && data[0]=='G' && data[1]=='I' && data[2]=='F')return new int[]{(data[6]&255)|(data[7]&255)<<8,(data[8]&255)|(data[9]&255)<<8};
        return null;
    }
    public Map<String,String> config() {
        Map<String,String> c=new LinkedHashMap<>();c.put("midlet.class",midlet);c.put("screen.width",""+width);c.put("screen.height",""+height);
        c.put("screen.profiles",width+"x"+height);c.put("scale.mode","fit");c.put("shader","nearest");c.put("keypad","gamepad");c.put("keypad.ask","true");
        c.put("fps.mode","vsync");c.put("fps.limit","0");c.put("auto.fit","true");c.put("center","true");c.put("rotate","-1");c.put("orientation","auto");return c;
    }
}
