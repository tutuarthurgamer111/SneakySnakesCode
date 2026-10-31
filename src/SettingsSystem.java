import java.io.*;
import java.util.Scanner;

/**
 * SettingsSystem - Handles settings persistence separately from SaveSystem.
 * Stores: playerName, theme, volume
 * File: ./data/settings.json
 */
public class SettingsSystem {

    public static final String SETTINGS_PATH = AppPaths.dataFile("settings.json").getAbsolutePath();

    // Themes
    public enum Theme { DAY, NIGHT, CRIMSON, FOREST }

    public String playerName = generateDefaultName();
    public Theme  theme      = Theme.DAY;
    public float  volume     = 1.0f; // 0.0 to 1.0

    public void load() {
        try {
            File f = new File(SETTINGS_PATH);
            if (!f.exists()) { save(); return; } // first run: save defaults
            StringBuilder sb = new StringBuilder();
            Scanner sc = new Scanner(f);
            while (sc.hasNextLine()) sb.append(sc.nextLine().trim());
            sc.close();
            String json = sb.toString();
            playerName = parseString(json, "PlayerName", playerName);
            volume     = parseFloat(json,  "Volume",     1.0f);
            String t   = parseString(json, "Theme",      "DAY");
            try { theme = Theme.valueOf(t); } catch (Exception e) { theme = Theme.DAY; }
        } catch (Exception e) {
            System.err.println("[SettingsSystem] Load failed: " + e.getMessage());
        }
    }

    public void save() {
        try {
            File folder = AppPaths.dataDirectory();
            if (!folder.exists()) folder.mkdirs();
            PrintWriter w = new PrintWriter(new FileWriter(SETTINGS_PATH));
            w.println("{");
            w.println("  \"PlayerName\":\"" + escape(playerName) + "\",");
            w.println("  \"Theme\":\""      + theme.name()       + "\",");
            w.println("  \"Volume\":"       + volume             );
            w.println("}");
            w.close();
        } catch (Exception e) {
            System.err.println("[SettingsSystem] Save failed: " + e.getMessage());
        }
    }

    private static String generateDefaultName() {
        int digits = 10000 + (int)(Math.random() * 90000); // 5 digits
        return "Player_" + digits;
    }

    private String parseString(String json, String key, String def) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return def;
            int colon = json.indexOf(':', idx);
            int open  = json.indexOf('"', colon + 1);
            int close = json.indexOf('"', open + 1);
            return json.substring(open + 1, close);
        } catch (Exception e) { return def; }
    }

    private float parseFloat(String json, String key, float def) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return def;
            int colon = json.indexOf(':', idx);
            int end   = json.indexOf('\n', colon);
            if (end < 0) end = json.indexOf('}', colon);
            return Float.parseFloat(json.substring(colon + 1, end).trim().replace(",", ""));
        } catch (Exception e) { return def; }
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}