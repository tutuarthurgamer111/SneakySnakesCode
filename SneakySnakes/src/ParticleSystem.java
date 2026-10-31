import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * ParticleSystem - Handles all particle effects in the game.
 * Supports food collection bursts, combo flares, level-up explosions,
 * achievement popups, confetti (victory), and game-over shatter effects.
 */
public class ParticleSystem {

    private static final Random RNG = new Random();

    /** Single particle data. */
    public static class Particle {
        public float x, y;
        public float vx, vy;
        public float life;        // 0.0 = dead, 1.0 = full
        public float maxLife;
        public Color color;
        public float size;
        public float gravity;
        public boolean confetti;
        public float rotation;
        public float rotSpeed;

        public Particle(float x, float y, float vx, float vy,
                        float life, Color color, float size, float gravity) {
            this.x = x; this.y = y;
            this.vx = vx; this.vy = vy;
            this.life = this.maxLife = life;
            this.color = color;
            this.size = size;
            this.gravity = gravity;
            this.confetti = false;
        }

        public boolean update() {
            x += vx;
            y += vy;
            vy += gravity;
            vx *= 0.97f;
            life -= 0.03f;
            return life > 0;
        }

        public void draw(Graphics2D g) {
            float alpha = Math.max(0, Math.min(1, life / maxLife));
            Color c = new Color(
                color.getRed(), color.getGreen(), color.getBlue(),
                (int)(alpha * 220)
            );
            g.setColor(c);
            if (confetti) {
                rotation += rotSpeed;
                g.rotate(rotation, x, y);
                g.fillRect((int)(x - size/2), (int)(y - size/2), (int)size, (int)(size*0.5f));
                g.rotate(-rotation, x, y);
            } else {
                int s = Math.max(1, (int)(size * alpha));
                g.fillOval((int)(x - s/2f), (int)(y - s/2f), s, s);
            }
        }
    }

    private final ArrayList<Particle> particles = new ArrayList<>();

    /** Update all particles, removing dead ones. */
    public void update() {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            if (!it.next().update()) it.remove();
        }
    }

    /** Render all particles. */
    public void draw(Graphics2D g) {
        for (Particle p : particles) {
            p.draw(g);
        }
    }

    /** Number of active particles. */
    public int count() { return particles.size(); }

    // ─── Spawn helpers ─────────────────────────────────────────────────────

    /** Burst when collecting food. count and color vary by rarity. */
    public void spawnFoodCollect(float x, float y, Color color, int count) {
        for (int i = 0; i < count; i++) {
            float angle = (float)(RNG.nextDouble() * Math.PI * 2);
            float speed = 1.5f + RNG.nextFloat() * 3f;
            float life  = 0.5f + RNG.nextFloat() * 0.8f;
            Particle p = new Particle(
                x, y,
                (float)Math.cos(angle) * speed,
                (float)Math.sin(angle) * speed,
                life, color, 4 + RNG.nextFloat() * 5f, 0.05f
            );
            particles.add(p);
        }
    }

    /** Spark trail when the snake moves (lightweight). */
    public void spawnTrail(float x, float y, Color color) {
        if (RNG.nextFloat() > 0.25f) return; // Only 25% chance each call
        float life = 0.25f + RNG.nextFloat() * 0.3f;
        Particle p = new Particle(
            x + RNG.nextFloat() * 8 - 4,
            y + RNG.nextFloat() * 8 - 4,
            (RNG.nextFloat() - 0.5f) * 0.8f,
            (RNG.nextFloat() - 0.5f) * 0.8f,
            life, color, 3f + RNG.nextFloat() * 3f, 0.02f
        );
        particles.add(p);
    }

    /** Big explosion for combo hits. */
    public void spawnCombo(float x, float y, int comboLevel) {
        Color[] comboColors = {
            new Color(255,220,50),
            new Color(255,140,0),
            new Color(255,60,60),
            new Color(200,50,255),
            new Color(50,255,255)
        };
        Color c = comboColors[Math.min(comboLevel - 1, comboColors.length - 1)];
        int count = 15 + comboLevel * 8;
        for (int i = 0; i < count; i++) {
            float angle = (float)(RNG.nextDouble() * Math.PI * 2);
            float speed = 2f + RNG.nextFloat() * 5f;
            Particle p = new Particle(
                x, y,
                (float)Math.cos(angle) * speed,
                (float)Math.sin(angle) * speed,
                0.8f + RNG.nextFloat() * 0.6f, c,
                5 + RNG.nextFloat() * 7f, 0.03f
            );
            particles.add(p);
        }
    }

    /** Level-up ring explosion. */
    public void spawnLevelUp(float cx, float cy) {
        Color[] colors = {
            new Color(255,215,0), new Color(255,255,100), new Color(200,255,100),
            new Color(100,255,200), new Color(100,200,255)
        };
        for (int i = 0; i < 80; i++) {
            float angle = (float)(i / 80.0 * Math.PI * 2);
            float speed = 3f + RNG.nextFloat() * 4f;
            Color c = colors[i % colors.length];
            Particle p = new Particle(
                cx, cy,
                (float)Math.cos(angle) * speed,
                (float)Math.sin(angle) * speed,
                1.0f + RNG.nextFloat() * 0.5f, c,
                6 + RNG.nextFloat() * 6f, 0.02f
            );
            particles.add(p);
        }
    }

    /** Achievement unlock burst. */
    public void spawnAchievement(float x, float y) {
        for (int i = 0; i < 40; i++) {
            float angle = (float)(RNG.nextDouble() * Math.PI * 2);
            float speed = 1f + RNG.nextFloat() * 4f;
            Color c = new Color(255, 215, 0);
            Particle p = new Particle(
                x, y,
                (float)Math.cos(angle) * speed,
                (float)Math.sin(angle) * speed,
                0.6f + RNG.nextFloat() * 0.8f, c,
                4 + RNG.nextFloat() * 5f, 0.04f
            );
            particles.add(p);
        }
    }

    /** Game-over shatter effect. */
    public void spawnGameOver(int screenW, int screenH) {
        Color[] reds = {Color.RED, new Color(200,0,0), new Color(255,80,80)};
        for (int i = 0; i < 80; i++) {
            float x = RNG.nextFloat() * screenW;
            float y = RNG.nextFloat() * screenH;
            float angle = (float)(RNG.nextDouble() * Math.PI * 2);
            float speed = 2f + RNG.nextFloat() * 4f;
            Particle p = new Particle(
                x, y,
                (float)Math.cos(angle) * speed,
                (float)Math.sin(angle) * speed,
                0.8f + RNG.nextFloat(), reds[i%3],
                5 + RNG.nextFloat() * 8f, 0.05f
            );
            particles.add(p);
        }
    }

    /** Victory confetti shower. */
    public void spawnConfetti(int screenW) {
        Color[] confettiColors = {
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
            Color.MAGENTA, new Color(255,165,0), Color.WHITE
        };
        for (int i = 0; i < 120; i++) {
            float x = RNG.nextFloat() * screenW;
            float y = -10 - RNG.nextFloat() * 200;
            Color c = confettiColors[RNG.nextInt(confettiColors.length)];
            Particle p = new Particle(
                x, y,
                (RNG.nextFloat() - 0.5f) * 2f,
                2f + RNG.nextFloat() * 4f,
                2.5f + RNG.nextFloat(), c,
                8 + RNG.nextFloat() * 8f, 0.01f
            );
            p.confetti = true;
            p.rotSpeed = (RNG.nextFloat() - 0.5f) * 0.3f;
            particles.add(p);
        }
    }

    /** Decorative background particles for the main menu. */
    public void spawnMenuDecor(int screenW, int screenH) {
        if (particles.size() > 80) return;
        for (int i = 0; i < 5; i++) {
            float x = RNG.nextFloat() * screenW;
            float y = RNG.nextFloat() * screenH;
            float r = 0.5f + RNG.nextFloat() * 0.5f;
            Color c = new Color(
                150 + RNG.nextInt(105),
                200 + RNG.nextInt(55),
                255,
                80 + RNG.nextInt(80)
            );
            Particle p = new Particle(
                x, y,
                (RNG.nextFloat() - 0.5f) * 0.6f,
                -0.4f - RNG.nextFloat() * 0.6f,
                r * 2, c,
                3 + RNG.nextFloat() * 6f, 0f
            );
            p.maxLife = p.life = r * 2;
            particles.add(p);
        }
    }

    public void clear() {
        particles.clear();
    }
}
