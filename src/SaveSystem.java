import java.io.*;
import java.util.Scanner;

/**
 * SaveSystem - Handles all persistence in JSON format.
 * Now also saves Coins for the skin shop.
 */
public class SaveSystem {

    public static final String SAVE_PATH = "./data/c4ca4238a0b923820dcc509a6f75849b.json";

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
        try {
            File f = new File(SAVE_PATH);
            if (!f.exists()) return;
            StringBuilder sb = new StringBuilder();
            Scanner sc = new Scanner(f);
            while (sc.hasNextLine()) sb.append(sc.nextLine().trim());
            sc.close();
            String json = sb.toString();
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
        } catch (Exception e) {
            System.err.println("[SaveSystem] Load failed: " + e.getMessage());
        }
    }

    public void save() {
        try {
            File folder = new File("./data");
            if (!folder.exists()) folder.mkdirs();
            PrintWriter w = new PrintWriter(new FileWriter(SAVE_PATH));
            w.println("{");
            w.println("  \"HighScore\":"      + highScore     + ",");
            w.println("  \"GamesPlayed\":"    + gamesPlayed   + ",");
            w.println("  \"FoodsEaten\":"     + foodsEaten    + ",");
            w.println("  \"Deaths\":"         + deaths        + ",");
            w.println("  \"BestCombo\":"      + bestCombo     + ",");
            w.println("  \"HighestLevel\":"   + highestLevel  + ",");
            w.println("  \"PlayTime\":"       + totalPlayTime + ",");
            w.println("  \"Coins\":"          + coins         + ",");
            w.println("  \"UnlockedSkins\":\"" + escape(unlockedSkins) + "\",");
            w.println("  \"Achievements\":\""  + escape(achievements)  + "\",");
            w.println("  \"CurrentSkin\":\""   + escape(currentSkinId) + "\"");
            w.println("}");
            w.close();
        } catch (Exception e) {
            System.err.println("[SaveSystem] Save failed: " + e.getMessage());
        }
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
        return s == null ? "" : s.replace("\\","\\\\").replace("\"","\\\"");
    }
}
