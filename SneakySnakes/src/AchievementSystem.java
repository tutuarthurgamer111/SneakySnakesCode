import java.util.ArrayList;
import java.util.List;

/**
 * AchievementSystem - Tracks and unlocks in-game achievements.
 * Each achievement has a name, description, condition, and unlock state.
 */
public class AchievementSystem {

    public static class Achievement {
        public final String id;
        public final String name;
        public final String description;
        public boolean unlocked;
        // Popup animation state
        public float popupTimer;  // counts down from 1.0 to 0 when showing

        public Achievement(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.unlocked = false;
            this.popupTimer = 0f;
        }
    }

    public static final List<Achievement> ALL = new ArrayList<>();

    // Pending popup queue (only one at a time)
    private static final List<Achievement> pendingPopups = new ArrayList<>();
    private static Achievement activePopup = null;
    private static float popupLife = 0f;
    private static final float POPUP_DURATION = 3.5f; // seconds (in update ticks ~60fps: ~210 ticks)

    static {
        ALL.add(new Achievement("first_bite",    "First Bite",        "Eat your first food."));
        ALL.add(new Achievement("hungry",        "Hungry",            "Eat 64 foods in one game."));
        ALL.add(new Achievement("food_hunter",   "Food Hunter",       "Eat 128 foods in one game."));
        ALL.add(new Achievement("combo_master",  "Combo Master",      "Reach a x5 combo."));
        ALL.add(new Achievement("snake_master",  "Snake Master",      "Reach level 5."));
        ALL.add(new Achievement("snake_god",     "Snake God",         "Reach level 8."));
        ALL.add(new Achievement("collector",     "Collector",         "Unlock 16 skins."));
        ALL.add(new Achievement("speed_demon",   "Speed Demon",       "Reach speed level 7."));
        ALL.add(new Achievement("legendary",     "Legendary Hunter",  "Collect a Legendary food."));
        ALL.add(new Achievement("survivor",      "Survivor",          "Survive for 3 minutes."));
        ALL.add(new Achievement("high_scorer",   "High Scorer",       "Reach a score of 50."));
        ALL.add(new Achievement("centurion",     "Centurion",         "Reach a score of 100."));
    }

    /** Try to unlock an achievement by ID. Returns true if newly unlocked. */
    public static boolean unlock(String id) {
        for (Achievement a : ALL) {
            if (a.id.equals(id) && !a.unlocked) {
                a.unlocked = true;
                pendingPopups.add(a);
                return true;
            }
        }
        return false;
    }

    /** Called every game tick to advance popup timers. */
    public static void update() {
        if (activePopup == null && !pendingPopups.isEmpty()) {
            activePopup = pendingPopups.remove(0);
            popupLife = POPUP_DURATION;
        }
        if (activePopup != null) {
            popupLife -= 1f / 60f;
            if (popupLife <= 0) {
                activePopup = null;
            }
        }
    }

    /** Returns the currently-showing popup, or null if none. */
    public static Achievement getActivePopup() {
        return activePopup;
    }

    /** 0→1 alpha for popup entrance / exit easing. */
    public static float getPopupAlpha() {
        if (activePopup == null) return 0f;
        float t = popupLife / POPUP_DURATION;
        // Fade in during last 0.9-1.0 range, fade out during 0-0.1
        if (t > 0.9f) return (1f - t) / 0.1f;
        if (t < 0.15f) return t / 0.15f;
        return 1f;
    }

    /** Serializes unlocked achievement IDs. */
    public static String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Achievement a : ALL) {
            if (a.unlocked) {
                if (sb.length() > 0) sb.append("|");
                sb.append(a.id);
            }
        }
        return sb.toString();
    }

    /** Deserializes unlocked achievement IDs. */
    public static void deserialize(String data) {
        if (data == null || data.isEmpty()) return;
        for (String id : data.split("\\|")) {
            for (Achievement a : ALL) {
                if (a.id.equals(id.trim())) a.unlocked = true;
            }
        }
    }

    /** Check all standard conditions and unlock as needed. */
    public static void checkConditions(GameState s) {
        int score   = s.score;
        int combo   = s.comboLevel;
        int level   = s.level;
        int foods   = s.foodsEaten;
        long playSec = s.gameTimeSeconds;

        if (foods >= 1)  unlock("first_bite");
        if (foods >= 64) unlock("hungry");
        if (foods >= 128) unlock("food_hunter");
        if (combo >= 512)  unlock("combo_master");
        if (level >= 1024)  unlock("snake_master");
        if (level >= 2048)  unlock("snake_god");
        if (level >= 4096)  unlock("speed_demon");
        if (score >= 8192) unlock("high_scorer");
        if (score >= 16384) unlock("centurion");
        if (playSec >= 7200) unlock("survivor");

        // Count unlocked skins
        int unlockedSkins = 0;
        for (SkinSystem.Skin sk : SkinSystem.ALL_SKINS) {
            if (sk.unlocked) unlockedSkins++;
        }
        if (unlockedSkins >= 16) unlock("collector");
    }
}
