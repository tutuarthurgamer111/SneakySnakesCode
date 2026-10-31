import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * SkinSystem - All snake skins including solid-color and texture-based skins.
 *
 * Texture skins load .png files from:
 *   SneakySnakes/Resources/content/textures/<filename>.png
 *
 * Texture is tiled/scaled to fit each snake segment during rendering.
 * Each skin has a coinCost (0 = free/score-unlocked, >0 = shop purchase).
 */
public class SkinSystem {
    public static boolean isUnlocked(Skin skin) {
        return skin.unlocked;
    }

    public static final String TEXTURE_PATH = "content/textures/";
            public static void unlock(Skin skin) {
            skin.unlocked = true;
        }
                public static Skin[] all() {
            return ALL_SKINS.toArray(new Skin[0]);
        }

    public static class Skin {
        public final String  id;
        public final String  displayName;
        public final Color   primary;
        public final Color   glow;
        public final Color   secondary;
        public final int     unlockScore;
        public final int     coinCost;
        public final String  textureFile;
        public BufferedImage texture;
        public boolean       unlocked;
        public Skin(String id, String name, Color primary, Color glow, Color secondary,
                    int unlockScore, int coinCost) {
            this.id = id; this.displayName = name;
            this.primary = primary; this.glow = glow; this.secondary = secondary;
            this.unlockScore = unlockScore; this.coinCost = coinCost;
            this.textureFile = null; this.texture = null;
            this.unlocked = (unlockScore == 0 && coinCost == 0);
        }
        public String costLabel() {
            if (unlocked) return "Owned";
            if (coinCost > 0) return coinCost + " coins";
            if (unlockScore > 0) return "Score " + unlockScore;
            return "Locked";
        }
        public Skin(String id, String name, Color fallbackColor, Color glow,
                    int coinCost, String textureFile) {
            this.id = id; this.displayName = name;
            this.primary = fallbackColor; this.glow = glow; this.secondary = fallbackColor;
            this.unlockScore = -1; this.coinCost = coinCost;
            this.textureFile = textureFile; this.texture = null;
            this.unlocked = false;
        }
        public void loadTexture() {
            if (textureFile == null || texture != null) return;
            try {
                File f = AppPaths.resourceFile(TEXTURE_PATH + textureFile);
                if (f.exists()) texture = ImageIO.read(f);
            } catch (Exception e) {
                System.err.println("Could not load texture: " + textureFile);
            }
        }

        public boolean isTextureSkin() { return textureFile != null; }

        public String unlockInfo() {
            if (unlocked) return "✓ Owned";
            if (coinCost > 0) return coinCost + " coins";
            if (unlockScore > 0) return "Score " + unlockScore;
            return "Locked";
        }
    }

    public static final List<Skin> ALL_SKINS = new ArrayList<>();
    private static int currentSkinIndex = 0;

    static {
        // ── Default solid skins (free) ───────────────────────────────────
        ALL_SKINS.add(new Skin("cocoa",        "Cocoa",          new Color(86,36,36),   new Color(160,80,60),  new Color(120,55,45),  50, 0));
        ALL_SKINS.add(new Skin("bright_blue",  "Blue",    new Color(13,105,172), new Color(80,180,255), new Color(0,140,220),   100, 0));
        ALL_SKINS.add(new Skin("lilac",        "Lilac",          new Color(167,94,155), new Color(220,150,210),new Color(200,110,185), 150, 0));
        ALL_SKINS.add(new Skin("hurricane",    "Grey", new Color(99,95,98),   new Color(170,170,175),new Color(130,128,132), 200, 0));
        ALL_SKINS.add(new Skin("violet",       "Violet",  new Color(107,50,124), new Color(175,90,200), new Color(140,65,160),  250, 0));
        ALL_SKINS.add(new Skin("bright_red",   "Red",     new Color(196,40,28),  new Color(255,100,80), new Color(230,60,45),   300, 0));
        ALL_SKINS.add(new Skin("bright_green", "Green",   new Color(75,151,75),  new Color(130,220,100),new Color(95,180,85),   0, 0));
        ALL_SKINS.add(new Skin("yeller",       "Yellow",     new Color(255,220,0),  new Color(255,245,100),new Color(240,200,20),  350, 0));
        ALL_SKINS.add(new Skin("really_blue",  "Bright Blue",    new Color(0,32,96),    new Color(30,80,200),  new Color(10,50,140),   400, 0));
        ALL_SKINS.add(new Skin("really_red",   "Bright Red",     new Color(255,0,0),    new Color(255,130,120),new Color(220,20,20),   450, 0));

        // ── Score-unlock skins ───────────────────────────────────────────
        ALL_SKINS.add(new Skin("gold",         "Gold",           new Color(200,150,0),  new Color(255,215,0),  new Color(240,185,30),  450, 0));
        ALL_SKINS.add(new Skin("neon_green",   "Classic",     new Color(0,234,0),    new Color(150,255,150),new Color(50,255,50),   450, 0));
        ALL_SKINS.add(new Skin("neon_pink",    "Neon Pink",      new Color(255,0,150),  new Color(255,150,220),new Color(255,50,180),  500, 0));
        ALL_SKINS.add(new Skin("neon_blue",    "Neon Blue",      new Color(0,150,255),  new Color(100,210,255),new Color(30,180,255),  500, 0));
        ALL_SKINS.add(new Skin("rainbow",      "Rainbow",        new Color(255,80,80),  new Color(255,255,255),new Color(80,200,255),  700, 0));

        // ── Shop skins (coin purchase) ───────────────────────────────────
        ALL_SKINS.add(new Skin("obsidian",     "Obsidian",       new Color(20,20,35),   new Color(100,60,200), new Color(40,30,60),    -1, 150));
        ALL_SKINS.add(new Skin("lava",         "Lava",           new Color(200,60,0),   new Color(255,160,0),  new Color(255,80,0),    -1, 200));
        ALL_SKINS.add(new Skin("ice",          "Ice Crystal",    new Color(180,230,255), new Color(220,245,255),new Color(200,240,255),-1, 200));
        ALL_SKINS.add(new Skin("midnight",     "Midnight",           new Color(10,10,60),   new Color(80,80,255),  120, "moon.png"));
        ALL_SKINS.add(new Skin("toxic",     "Toxic",           new Color(50,200,50),   new Color(180,255,50),  300, "toxic.png"));
        ALL_SKINS.add(new Skin("rich",     "Rich",           new Color(255,237,89),   new Color(255,237,89),  300, "money.png"));

        // ── Texture skins (loaded from PNG files) ────────────────────────
        // These are auto-discovered from the textures folder + hardcoded entries.
        // Players can add any .png to the textures folder and it will appear here.
        ALL_SKINS.add(new Skin("tex_snake",    "Snake Scale",    new Color(80,140,60),  new Color(140,220,80), 350, "snake_scale.png"));
        ALL_SKINS.add(new Skin("tex_pixel",    "Pixel Camo",     new Color(100,120,80), new Color(160,180,120),400, "pixel_camo.png"));
        ALL_SKINS.add(new Skin("tex_neon_grid","Neon Grid",      new Color(0,200,200),  new Color(100,255,255),400, "neon_grid.png"));
        ALL_SKINS.add(new Skin("tex_galaxy",   "Galaxy",         new Color(30,0,80),    new Color(150,80,255), 500, "galaxy.png"));
        ALL_SKINS.add(new Skin("tex_fire",     "Fire",           new Color(200,80,0),   new Color(255,200,0),  500, "fire.png"));
        ALL_SKINS.add(new Skin("tex_tomato",     "Tomatoes",           new Color(237,70,32),   new Color(237,70,32),  1000, "tomato.png"));

        //discoverExtraTextures();
    }

    private static void discoverExtraTextures() {
        File folder = AppPaths.resourceFile(TEXTURE_PATH);
        if (!folder.exists() || !folder.isDirectory()) return;
        File[] files = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (files == null) return;
        for (File f : files) {
            String fname = f.getName();
            String id    = "custom_" + fname.replace(".png","").replaceAll("[^a-zA-Z0-9_]","_");
            boolean exists = false;
            for (Skin s : ALL_SKINS) {
                if (fname.equals(s.textureFile) || s.id.equals(id)) { exists = true; break; }
            }
            if (!exists) {
                String displayName = fname.replace(".png","").replace("_"," ");
                displayName = Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
                ALL_SKINS.add(new Skin(id, displayName, new Color(100,100,200), new Color(180,180,255), 300, fname));
            }
        }
    }
    public static void preloadTextures() {
        for (Skin s : ALL_SKINS) s.loadTexture();
    }

    public static Skin current() { return ALL_SKINS.get(currentSkinIndex); }

    public static void cycleNext() {
        int start = currentSkinIndex;
        do { currentSkinIndex = (currentSkinIndex + 1) % ALL_SKINS.size(); }
        while (!ALL_SKINS.get(currentSkinIndex).unlocked && currentSkinIndex != start);
    }

    public static void cyclePrev() {
        int start = currentSkinIndex;
        do { currentSkinIndex = (currentSkinIndex - 1 + ALL_SKINS.size()) % ALL_SKINS.size(); }
        while (!ALL_SKINS.get(currentSkinIndex).unlocked && currentSkinIndex != start);
    }

    public static Skin findById(String id) {
        if (id==null) return null;
        for (Skin s : ALL_SKINS) if (s.id.equals(id)) return s;
        return null;
    }

    public static void setById(String id) {
        for (int i = 0; i < ALL_SKINS.size(); i++) {
            if (ALL_SKINS.get(i).id.equals(id)) { currentSkinIndex = i; return; }
        }
    }

    public static Color getRainbowColor(int segment, long tick) {
        float hue = ((tick * 2 + segment * 15) % 360) / 360f;
        return Color.getHSBColor(hue, 0.9f, 1.0f);
    }

    public static void checkUnlocks(int highScore) {
        for (Skin s : ALL_SKINS) {
            if (!s.unlocked && s.unlockScore > 0 && highScore >= s.unlockScore)
                s.unlocked = true;
        }
    }

    public static boolean buySkin(String id, SaveSystem save) {
        for (Skin s : ALL_SKINS) {
            if (s.id.equals(id) && !s.unlocked && s.coinCost > 0) {
                if (save.coins >= s.coinCost) {
                    save.coins -= s.coinCost;
                    s.unlocked = true;
                    return true;
                }
            }
        }
        return false;
    }

    public static String serializeUnlocked() {
        StringBuilder sb = new StringBuilder();
        for (Skin s : ALL_SKINS) {
            if (s.unlocked) { if (sb.length()>0) sb.append("|"); sb.append(s.id); }
        }
        return sb.toString();
    }

    public static void deserializeUnlocked(String data) {
        if (data == null || data.isEmpty()) return;
        for (String id : data.split("\\|"))
            for (Skin s : ALL_SKINS) if (s.id.equals(id.trim())) s.unlocked = true;
    }

    public static void drawSegment(Graphics2D g, Skin skin, int x, int y, int size,
                                    boolean isHead, long tick, int segmentIndex) {
        Color base = skin.primary;
        if (skin.id.equals("rainbow")) base = getRainbowColor(segmentIndex, tick);

        if (isHead) {
            for (int r = 6; r > 0; r -= 2) {
                Color gc = skin.glow;
                g.setColor(new Color(gc.getRed(),gc.getGreen(),gc.getBlue(), 25 + r*5));
                g.fillRoundRect(x-r, y-r, size+r*2, size+r*2, 14, 14);
            }
        }

        if (skin.isTextureSkin() && skin.texture != null) {
            Shape clip = new java.awt.geom.RoundRectangle2D.Float(x+1, y+1, size-2, size-2, 10, 10);
            g.setClip(clip);
            g.drawImage(skin.texture, x+1, y+1, size-2, size-2, null);
            g.setClip(null);
            if (!isHead) {
                g.setColor(new Color(0,0,0,40));
                g.fillRoundRect(x+1, y+1, size-2, size-2, 10, 10);
            }
        } else {
            g.setColor(isHead ? base.brighter() : base);
            g.fillRoundRect(x+1, y+1, size-2, size-2, 10, 10);
            g.setColor(new Color(255,255,255, isHead ? 80 : 35));
            g.fillRoundRect(x+3, y+3, size-8, (size-8)/2, 6, 6);
        }
        g.setColor(new Color(0,0,0,60));
        g.drawRoundRect(x+1, y+1, size-2, size-2, 10, 10);
    }
}