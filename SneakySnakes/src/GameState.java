/**
 * GameState - Centralizes all mutable game state for clarity and easy serialization.
 */
public class GameState {

    // ── Snake position (tile coordinates)
    public int[] bodyX = new int[2048];
    public int[] bodyY = new int[2048];
    public int length  = 1;

    // ── Direction
    public int dirX = 1, dirY = 0;
    public int nextDirX = 1, nextDirY = 0; // buffered input

    // ── Food
    public int foodX, foodY;
    public FoodRarity foodRarity = FoodRarity.NORMAL;
    public float foodPulse = 0f;
    public boolean foodPulseGrowing = true;

    // ── Scoring
    public int score  = 0;
    public int xp     = 0;
    public int level  = 1;

    // ── Combo
    public int  comboLevel   = 1;
    public long lastFoodTime = 0;  // System.currentTimeMillis() of last eat
    public static final long COMBO_WINDOW_MS = 3500; // ms to maintain combo

    // ── Session stats
    public int  foodsEaten    = 0;
    public long gameStartTime = System.currentTimeMillis();
    public long gameTimeSeconds = 0;

    // ── Win/lose
    public int  pendingGrowth = 0; // segments still to add
    public boolean growthAnimating = false;

    // ── Flags
    public boolean alive  = true;
    public boolean paused = false;

    // ── Screen shake
    public float shakeX = 0, shakeY = 0;
    public float shakeMag = 0;

    /** XP table: xpForLevel[i] = XP needed to reach level i+2 from level i+1 */
    public static final int[] XP_TABLE = {10, 25, 50, 100, 200, 400, 800, Integer.MAX_VALUE};

    /** Returns XP threshold for given level (1-indexed). */
    public static int xpForLevel(int level) {
        int total = 0;
        for (int i = 0; i < Math.min(level - 1, XP_TABLE.length); i++) {
            total += XP_TABLE[i];
        }
        return total;
    }

    public static int xpForNextLevel(int level) {
        int idx = level - 1;
        if (idx >= XP_TABLE.length) return Integer.MAX_VALUE;
        return XP_TABLE[idx];
    }

    /** XP gained per food, multiplied by combo and rarity. */
    public int xpGain(FoodRarity rarity) {
        int base = rarity.growthBonus;
        return (int)(base * comboMultiplier());
    }

    /** Score gained per food. */
    public int scoreGain(FoodRarity rarity) {
        int base = rarity.scoreValue;
        return (int)(base * comboMultiplier());
    }

    /** Current combo multiplier (1x, 2x … 10x). */
    public float comboMultiplier() {
        return Math.min(comboLevel, 10);
    }

    /** Timer tick for food pulse animation. */
    public void updateFoodPulse() {
        if (foodPulseGrowing) {
            foodPulse += 0.18f;
            if (foodPulse >= 5f) foodPulseGrowing = false;
        } else {
            foodPulse -= 0.18f;
            if (foodPulse <= 0f) foodPulseGrowing = true;
        }
    }

    /** Update screen shake decay. */
    public void updateShake() {
        if (shakeMag > 0.1f) {
            double angle = Math.random() * Math.PI * 2;
            shakeX = (float)(Math.cos(angle) * shakeMag);
            shakeY = (float)(Math.sin(angle) * shakeMag);
            shakeMag *= 0.82f;
        } else {
            shakeX = 0; shakeY = 0; shakeMag = 0;
        }
    }

    public void triggerShake(float magnitude) {
        shakeMag = Math.max(shakeMag, magnitude);
    }

    /** Food rarity enumeration with all gameplay values. */
    public enum FoodRarity {
        NORMAL   (70, 1, 1,  new java.awt.Color(255,150,200), new java.awt.Color(255,200,230),  8),
        RARE     (20, 2, 3,  new java.awt.Color( 80,150,255), new java.awt.Color(150,200,255), 14),
        EPIC     ( 8, 4, 8,  new java.awt.Color(180, 80,255), new java.awt.Color(220,150,255), 20),
        LEGENDARY( 2, 8,20,  new java.awt.Color(255,215,  0), new java.awt.Color(255,255,150), 30);

        public final int weight;        // relative spawn weight
        public final int growthBonus;   // segments added
        public final int scoreValue;    // base score
        public final java.awt.Color color;
        public final java.awt.Color glowColor;
        public final int particleCount;

        FoodRarity(int w, int g, int s, java.awt.Color c, java.awt.Color gc, int pc) {
            weight = w; growthBonus = g; scoreValue = s;
            color = c; glowColor = gc; particleCount = pc;
        }
    }
}
