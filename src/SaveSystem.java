import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Saves game progress in an authenticated encrypted binary file.
 * Older plaintext JSON saves are imported automatically.
 */
public class SaveSystem {

    public static final String SAVE_PATH =
        AppPaths.dataFile("progress.sav").getAbsolutePath();
    private static final String LEGACY_SAVE_PATH =
        AppPaths.dataFile("c4ca4238a0b923820dcc509a6f75849b.json").getAbsolutePath();

    // App-local key: protects casual inspection, but can be extracted by reverse engineering.
    private static final byte[] KEY = new byte[] {
        0x53, 0x6e, 0x65, 0x61, 0x6b, 0x79, 0x53, 0x6e,
        0x61, 0x6b, 0x65, 0x73, 0x2d, 0x32, 0x30, 0x32
    };
    private static final byte[] MAGIC = new byte[] { 'S', 'N', 'K', '1' };
    private static final int NONCE_LENGTH = 12;

    public int    highScore      = 0;
    public int    gamesPlayed    = 0;
    public int    foodsEaten     = 0;
    public int    deaths         = 0;
    public int    bestCombo      = 0;
    public int    highestLevel   = 1;
    public long   totalPlayTime  = 0;
    public int    coins          = 0;
    public String unlockedSkins  = "";
    public String achievements   = "";
    public String currentSkinId  = "bright_green";

    public void load() {
        File file = new File(SAVE_PATH);
        boolean migrateLegacy = false;

        try {
            if (!file.exists()) {
                file = new File(LEGACY_SAVE_PATH);
                if (!file.exists()) return;
                migrateLegacy = true;
            }

            byte[] stored = Files.readAllBytes(file.toPath());
            String json;
            if (isEncrypted(stored)) {
                json = decrypt(stored);
            } else {
                // Compatibility with the previous plain JSON save format.
                json = new String(stored, StandardCharsets.UTF_8).trim();
                migrateLegacy = true;
            }

            highScore     = parseInt(json,  "HighScore",     0);
            gamesPlayed   = parseInt(json,  "GamesPlayed",   0);
            foodsEaten    = parseInt(json,  "FoodsEaten",     0);
            deaths        = parseInt(json,  "Deaths",         0);
            bestCombo     = parseInt(json,  "BestCombo",      0);
            highestLevel  = parseInt(json,  "HighestLevel",   1);
            totalPlayTime = parseLong(json, "PlayTime",       0);
            coins         = parseInt(json,  "Coins",          0);
            unlockedSkins = parseString(json,"UnlockedSkins", "");
            achievements  = parseString(json,"Achievements",  "");
            currentSkinId = parseString(json,"CurrentSkin",   "bright_green");

            if (migrateLegacy) {
                save();
                if (new File(SAVE_PATH).isFile()) {
                    new File(LEGACY_SAVE_PATH).delete();
                }
            }
        } catch (Exception e) {
            System.err.println("[SaveSystem] Load failed: " + e.getMessage());
        }
    }

    public void save() {
        try {
            File folder = AppPaths.dataDirectory();
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException("Could not create save directory");
            }

            StringBuilder json = new StringBuilder();
            json.append("{\n");
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
            json.append("  \"CurrentSkin\":\"").append(escape(currentSkinId)).append("\"\n");
            json.append("}");

            byte[] encrypted = encrypt(json.toString());
            File target = new File(SAVE_PATH);
            File temp = new File(SAVE_PATH + ".tmp");
            Files.write(temp.toPath(), encrypted);
            try {
                Files.move(temp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[SaveSystem] Save failed: " + e.getMessage());
        }
    }

    private byte[] encrypt(String plainText) throws Exception {
        byte[] nonce = new byte[NONCE_LENGTH];
        new SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"),
            new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC);
        out.write(nonce);
        out.write(encrypted);
        return out.toByteArray();
    }

    private String decrypt(byte[] stored) throws Exception {
        if (stored.length <= MAGIC.length + NONCE_LENGTH) {
            throw new IOException("Encrypted save is incomplete");
        }
        byte[] nonce = Arrays.copyOfRange(stored, MAGIC.length, MAGIC.length + NONCE_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(stored, MAGIC.length + NONCE_LENGTH, stored.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"),
            new GCMParameterSpec(128, nonce));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private boolean isEncrypted(byte[] data) {
        if (data.length < MAGIC.length) return false;
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) return false;
        }
        return true;
    }

    private int parseInt(String json, String key, int def) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return def;
            int colon = json.indexOf(':', idx);
            int end = json.indexOf(',', colon);
            if (end < 0) end = json.indexOf('}', colon);
            return Integer.parseInt(json.substring(colon+1,end).trim().replace("\"","").replace("}",""));
        } catch (Exception e) { return def; }
    }

    private long parseLong(String json, String key, long def) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return def;
            int colon = json.indexOf(':', idx);
            int end = json.indexOf(',', colon);
            if (end < 0) end = json.indexOf('}', colon);
            return Long.parseLong(json.substring(colon+1,end).trim().replace("\"","").replace("}",""));
        } catch (Exception e) { return def; }
    }

    private String parseString(String json, String key, String def) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return def;
            int colon = json.indexOf(':', idx);
            int open  = json.indexOf('"', colon+1);
            int close = json.indexOf('"', open+1);
            return json.substring(open+1, close);
        } catch (Exception e) { return def; }
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
