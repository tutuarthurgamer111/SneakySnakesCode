import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SaveSystem {
    public static final String SAVE_PATH = AppPaths.dataFile("progress.sav").getAbsolutePath();
    private static final String LEGACY_NAME = "c4ca4238a0b923820dcc509a6f75849b.json";
    private static final byte[] MAGIC = new byte[] {'S','N','K','2'};
    private static final int NONCE_LENGTH = 12;
    private static final String[] KEY_POOL = {
        "510c4619e02e553e7bb98f3a0183a8b5",
        "e6336d1ff989d237ba2529d0fcfbedbf",
        "a8c5ee39b55a53b81a88a9eef918a8b4",
        "6dc588d3472f513c0c1870b843e1465f",
        "0e78ea8a761dc0de0eca9c7df5e7493f",
        "84d44cbfa536e9de79afaed802e9f4a2",
        "b3c8f91c318ec249d13543ea504fd68e",
        "f9563be1fb6a9a74acad82a683d0d79a",
        "bd58ba6be8a46341fd4255c748a7297a",
        "1c8fc87e558f2d7eb43618ae935f84df",
        "1b4ef29d66bb32731e5f13297b73ebb4",
        "c6a87e76e5bd8265ebd01b3dfe4e23a6",
        "7d6529dbd39a9b749e7f3ace5dfe0dfd",
        "147d987d493f1344c1af1b0f7b13a768",
        "f02ab2776ae429e514c73f29976eb1b8",
        "6a6bb3949f3e8e989358942eba7f8cc0",
        "37128f53b9e359cf8980c4e2b28541ec",
        "4da15ab0b81a50abb3e67c2cf01b81bd",
        "85a054cb681719b7ef1638c729d754c0",
        "aaa99987aabf9c2b7e60c676b3689101",
        "8854d5054c7bf39f730959fb5ef4a9e0",
        "b2d14c84f371a5a43f6d8e86591c32d8",
        "37acf21b94171b6c982ebaf1a1671469",
        "3ea8a61c670d5609744e0d0f081948f8",
        "01cd571bcee2330c98fd1eed5f34ccdd",
        "134efeca5e6cc8a12869e8bdbab60242",
        "2531989dd264420c1e980cdeff7ba8bf",
        "c7edbca97f6c3635ccf7b6e07f5ed555",
        "1b70d24f261f68b3aa24cbd758987d78",
        "b1dd8a831130b265e8fe2abb9882d18f",
        "94d94a160ee14fbbc5d1ba30a06fac1b",
        "e8703b4d0c2474e1d5baa21dbed11ec1",
        "3c2778e5b44d9e78f7d12a9982ce18d8",
        "7b723e72ab3065ac57337bae3092562d",
        "30aeabc65f153c8de818f92f10913491",
        "f662fd9093c586784258685da52e1174",
        "8714fc740bd4771923d5a5c27ad860f4",
        "ae1da9777d5bd92ec9bd583135d264ec",
        "50b4d12b98ab580386c37b16dd983706",
        "b46bcdfa774989108b1eee858f02d9a2",
        "52e884990d0b31240edf12d37c2596b1",
        "1089e8c89f8f3e0071af46c7b78aa5fc",
        "859fd8a6afefdb83c76da84c3ee63ebe",
        "df01c6e61c73d408b8ae09514906a7f3",
        "22e9a8efe97c21b5c41c551099703baf",
        "5eb7010d6c49368619a3aa8af2a94293",
        "8592b22ea93463658e42e8e9b8ab8986",
        "fafe842b6505d3d63090f149f98104b5",
        "fbeecffe6032c0363eb799ac7dcd92cd",
        "3d1ef5e797eee2f63db0e2ee1c4b7118",
        "3f614daccc4c1e06be13c1c0035ff875",
        "7675edfdb28f2b18aa6c1d2e10f0f08a",
        "d2d748ba43c2be1a943f775a20554c30",
        "b3b213f9c86428ffe2062602fe08d941",
        "4131f1f19ef220b6867535443b69006a",
        "77edf6d8efe4da23e0b08e13df2043ea",
        "3cf060c42df7eeb0d41152a036e09dc2",
        "8e4122aee8824324bc34f9b743430ef6",
        "712628b8321f26a00fa2f56570c1c1a2",
        "56411ed8c6dea6b55309f991eb461e0c",
        "3876c3afe069266e503403bcd83e983d",
        "6c1e898118f015d65311c69313b1fd32",
        "aee2cc191b536289974d5808b5c483ee",
        "9293777273d460cc067e7a6aef588093",
        "161979f61dd309c5e19961b0784feb87",
        "22d66a181d36a792f154ed70f3a1baad",
        "5a7b6237f18ebc09e7fe6ece6ed0c07d",
        "ad34395269d3e7fdb0a5e00b98344d79",
        "22f7d334cab6d0fc05824da8cada1dd3",
        "a5671503bf2d8ae0c04036d815f1e735",
        "0bfd247e4124cb27d048a7e75230921d",
        "4e1156a4fc797c06acc36abaf31d0470",
        "21f69a99a61dbaab385293ef2fc799f8",
        "43e60704e3de1667034e3b2e4158be5b",
        "84ed5752eee70a71fa8c99055c2db7b3",
        "b1aff91e9f0fd558d13816caafc6d87a",
        "edfde1de69072e9d164dd6173d636a50",
        "55010ac4ff6fade85087b8a155eaa668",
        "25d3799b16e7a18bb800ace01e03bfee",
        "56372931d13b479980cf54dd20ccff17",
        "5ae650808e35079bf6086ff885155263",
        "c47d9c2f88f0bdb37794d0105f427dbb",
        "0e57ade18695f294202ab017b3250b63",
        "5b652aa73807025fd1dd4159473de9e2",
        "ba3b48a2b6c347951800992d6f82ab5f",
        "67c3934ba0f5ca5e2e7ed6a161fb1274",
        "7507e66eb08633eba9967a56bd4dae38",
        "19dfe5dd49e2da8f44d549165308024d",
        "3b70f64d9291d6b1a0b3f0d20b922f46",
        "8123c71bc1dba49b1c5b59df9b70cf8f",
        "d2acb72fcd67d46bcabf5c5e07a51444",
        "00951c92c3e6af31d61002111c1b6e38",
        "4a568c16e1543e7cb5ea2b11e18619f5",
        "75adbd8981836c676df0b6601cadf43e",
        "e766c04bd298407c88620bd7ea09643b",
        "174288fc8df851927e801c97df4a81de",
        "6151caa658a1f224ba4d6b96f0cba129",
        "1936a89e4884df0cbfc3e17ebe645188",
        "befc3fe2190c0f2285e869969387529b",
        "4355c05190ac223e8996e12be3c6d5b2"
    };
    private static final int[] ORDER_MASKED = {0xA7, 0x5C, 0xD2, 0x31, 0x89, 0xE4, 0x6B, 0xF0, 0x42, 0xBD};
    private static final int[] ORDER_MASK = {0x80, 0x58, 0x80, 0x22, 0xA6, 0xEF, 0x08, 0xEC, 0x16, 0xBA};
    private static int[] keyOrder() {
        int[] order = new int[ORDER_MASKED.length];
        for (int i = 0; i < order.length; i++) order[i] = ORDER_MASKED[i] ^ ORDER_MASK[i];
        return order;
    }

    public int highScore=0, gamesPlayed=0, foodsEaten=0, deaths=0, bestCombo=0;
    public int highestLevel=1, coins=0;
    public long totalPlayTime=0;
    public String unlockedSkins="", achievements="", currentSkinId="bright_green";

    public void load() {
        File source = new File(SAVE_PATH);
        boolean migrate = false;
        try {
            if (!source.isFile()) {
                File appDataLegacy = AppPaths.dataFile(LEGACY_NAME);
                File portableLegacy = new File(new File("data"), LEGACY_NAME);
                if (appDataLegacy.isFile()) source=appDataLegacy;
                else if (portableLegacy.isFile()) source=portableLegacy;
                else return;
                migrate=true;
            }
            byte[] bytes=Files.readAllBytes(source.toPath());
            String json;
            if (isEncrypted(bytes)) json=decrypt(bytes);
            else { json=new String(bytes,StandardCharsets.UTF_8).trim(); migrate=true; }
            highScore=parseInt(json,"HighScore",0);
            gamesPlayed=parseInt(json,"GamesPlayed",0);
            foodsEaten=parseInt(json,"FoodsEaten",0);
            deaths=parseInt(json,"Deaths",0);
            bestCombo=parseInt(json,"BestCombo",0);
            highestLevel=parseInt(json,"HighestLevel",1);
            totalPlayTime=parseLong(json,"PlayTime",0);
            coins=parseInt(json,"Coins",0);
            unlockedSkins=parseString(json,"UnlockedSkins","");
            achievements=parseString(json,"Achievements","");
            currentSkinId=parseString(json,"CurrentSkin","bright_green");
            if(migrate) {
                save();
                if(new File(SAVE_PATH).isFile() && new File(SAVE_PATH).length()>0) {
                    // Keep the original as a backup; it is removed only after a later successful save.
                }
            }
        } catch(Exception e) {
            System.err.println("[SaveSystem] Load failed: "+e.getMessage());
        }
    }

    public void save() {
        try {
            File folder=AppPaths.dataDirectory();
            if(!folder.exists() && !folder.mkdirs()) throw new IOException("Could not create save directory");
            AppPaths.hideDataDirectory();
            StringBuilder json=new StringBuilder("{\n");
            json.append("  \"HighScore\":").append(highScore).append(",\n");
            json.append("  \"GamesPlayed\":").append(gamesPlayed).append(",\n");
            json.append("  \"FoodsEaten\":").append(foodsEaten).append(",\n");
            json.append("  \"Deaths\":").append(deaths).append(",\n");
            json.append("  \"BestCombo\":").append(bestCombo).append(",\n");
            json.append("  \"HighestLevel\":").append(highestLevel).append(",\n");
            json.append("  \"PlayTime\":").append(totalPlayTime).append(",\n");
            json.append("  \"Coins\":").append(coins).append(",\n");
            json.append("  \"UnlockedSkins\":\"").append(escape(unlockedSkins)).append("\",\n");
            json.append("  \"Achievements\":\"").append(escape(achievements)).append("\",\n");
            json.append("  \"CurrentSkin\":\"").append(escape(currentSkinId)).append("\"\n}");
            byte[] encrypted=encrypt(json.toString());
            File target=new File(SAVE_PATH), temp=new File(SAVE_PATH+".tmp");
            Files.write(temp.toPath(),encrypted);
            try {
                Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch(java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            AppPaths.hideDataDirectory();
        } catch(Exception e) { System.err.println("[SaveSystem] Save failed: "+e.getMessage()); }
    }

    private byte[] encrypt(String plain) throws Exception {
        byte[] data=plain.getBytes(StandardCharsets.UTF_8);
        SecureRandom random=new SecureRandom();
        for(int idx:keyOrder()) {
            byte[] nonce=new byte[NONCE_LENGTH]; random.nextBytes(nonce);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(hex(KEY_POOL[idx]),"AES"),new GCMParameterSpec(128,nonce));
            byte[] body=c.doFinal(data);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            out.write(nonce); out.write(body); data=out.toByteArray();
        }
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        result.write(MAGIC); result.write(data);
        return result.toByteArray();
    }

    private String decrypt(byte[] stored) throws Exception {
        if(!isEncrypted(stored)) throw new IOException("Unknown save format");
        byte[] data=Arrays.copyOfRange(stored,MAGIC.length,stored.length);
        for(int i=keyOrder().length-1;i>=0;i--) {
            if(data.length<=NONCE_LENGTH) throw new IOException("Incomplete save layer");
            byte[] nonce=Arrays.copyOfRange(data,0,NONCE_LENGTH);
            byte[] body=Arrays.copyOfRange(data,NONCE_LENGTH,data.length);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(hex(KEY_POOL[keyOrder()[i]]),"AES"),new GCMParameterSpec(128,nonce));
            data=c.doFinal(body);
        }
        return new String(data,StandardCharsets.UTF_8);
    }

    private byte[] hex(String value) {
        byte[] out=new byte[value.length()/2];
        for(int i=0;i<out.length;i++) out[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);
        return out;
    }
    private boolean isEncrypted(byte[] data) {
        return data.length>=MAGIC.length && data[0]=='S' && data[1]=='N' && data[2]=='K' && data[3]=='2';
    }
    private int parseInt(String j,String k,int d) {
        try { int i=j.indexOf("\""+k+"\""); if(i<0)return d; int c=j.indexOf(':',i),e=j.indexOf(',',c); if(e<0)e=j.indexOf('}',c); return Integer.parseInt(j.substring(c+1,e).trim()); } catch(Exception e){return d;}
    }
    private long parseLong(String j,String k,long d) {
        try { int i=j.indexOf("\""+k+"\""); if(i<0)return d; int c=j.indexOf(':',i),e=j.indexOf(',',c); if(e<0)e=j.indexOf('}',c); return Long.parseLong(j.substring(c+1,e).trim()); } catch(Exception e){return d;}
    }
    private String parseString(String j,String k,String d) {
        try { int i=j.indexOf("\""+k+"\""); if(i<0)return d; int c=j.indexOf(':',i),o=j.indexOf('"',c+1),e=j.indexOf('"',o+1); return j.substring(o+1,e); } catch(Exception e){return d;}
    }
    private String escape(String s) { return s==null?"":s.replace("\\","\\\\").replace("\"","\\\""); }
}
