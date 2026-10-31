import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * TrailSystem — 5 tipos criativos de rastro para SneakySnakes.
 *
 * Cada rastro é uma partícula gerada quando a cauda da cobra "some".
 * Tipos:
 *   NONE     — sem rastro
 *   SPARKLE  — faíscas douradas que giram e somem
 *   SMOKE    — fumaça cinza que expande e desaparece
 *   HEARTS   — coraçõezinhos rosas que flutuam para cima
 *   STARS    — estrelas coloridas que piscam e giram
 *   NEON     — anel neon que expande rapidamente
 */
public class TrailSystem {

    // ── Tipos disponíveis ────────────────────────────────────────────────────
    public enum TrailType {
        NONE    ("None",    0,    "Free"),
        SPARKLE ("Sparkle", 80,   "80 coins"),
        SMOKE   ("Smoke",   80,   "80 coins"),
        HEARTS  ("Hearts",  120,  "120 coins"),
        STARS   ("Stars",   120,  "120 coins"),
        NEON    ("Neon",    200,  "200 coins");

        public final String displayName;
        public final int    coinCost;
        public final String costLabel;

        TrailType(String displayName, int coinCost, String costLabel) {
            this.displayName = displayName;
            this.coinCost    = coinCost;
            this.costLabel   = costLabel;
        }
    }

    // ── Estado global ────────────────────────────────────────────────────────
    private static TrailType   currentType     = TrailType.NONE;
    private static boolean[]   unlocked        = new boolean[TrailType.values().length];
    private static final List<TrailParticle> particles = new ArrayList<>();

    static { unlocked[0] = true; } // NONE sempre desbloqueado

    public static TrailType current()                  { return currentType; }
    public static void      setType(TrailType t)       { currentType = t; }
    public static boolean   isUnlocked(TrailType t)    { return unlocked[t.ordinal()]; }
    public static void      unlock(TrailType t)        { unlocked[t.ordinal()] = true; }

    public static String serializeUnlocked() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < unlocked.length; i++) { if (i > 0) sb.append(","); sb.append(unlocked[i] ? "1" : "0"); }
        return sb.toString();
    }

    public static void deserializeUnlocked(String s) {
        if (s == null || s.isEmpty()) return;
        String[] parts = s.split(",");
        for (int i = 0; i < parts.length && i < unlocked.length; i++)
            unlocked[i] = parts[i].trim().equals("1");
        unlocked[0] = true;
    }

    public static void setById(String id) {
        for (TrailType t : TrailType.values()) if (t.name().equalsIgnoreCase(id)) { currentType = t; return; }
    }

    // ── Spawn de partícula na cauda ──────────────────────────────────────────
    /**
     * Chamado quando o último segmento da cobra "some" (a cada movimento).
     * wx, wy = coordenadas de mundo (pixels) do segmento removido.
     * skinGlow = cor base da skin atual.
     */
    public static void spawnTail(float wx, float wy, Color skinGlow) {
        switch (currentType) {
            case SPARKLE: spawnSparkle(wx, wy, skinGlow); break;
            case SMOKE:   spawnSmoke  (wx, wy);           break;
            case HEARTS:  spawnHeart  (wx, wy);           break;
            case STARS:   spawnStar   (wx, wy, skinGlow); break;
            case NEON:    spawnNeon   (wx, wy, skinGlow); break;
            default: break;
        }
    }

    // ── Atualização e desenho ────────────────────────────────────────────────
    public static void update() {
        Iterator<TrailParticle> it = particles.iterator();
        while (it.hasNext()) { if (!it.next().update()) it.remove(); }
    }

    public static void draw(Graphics2D g) {
        for (TrailParticle p : particles) p.draw(g);
    }

    public static void clear() { particles.clear(); }

    // ── Implementações dos 5 tipos ───────────────────────────────────────────

    // SPARKLE: 4-6 faíscas douradas que giram ao redor do ponto e somem
    private static void spawnSparkle(float wx, float wy, Color base) {
        int count = 4 + (int)(Math.random() * 3);
        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2;
            float speed  = 0.4f + (float)Math.random() * 0.8f;
            float vx = (float)Math.cos(angle) * speed;
            float vy = (float)Math.sin(angle) * speed;
            Color c = blend(base, new Color(255, 230, 80), (float)Math.random());
            particles.add(new SparkleParticle(wx, wy, vx, vy, c, 20 + (int)(Math.random() * 15)));
        }
    }

    // SMOKE: 1-2 círculos cinzas que expandem e ficam transparentes
    private static void spawnSmoke(float wx, float wy) {
        int count = 1 + (int)(Math.random() * 2);
        for (int i = 0; i < count; i++) {
            float ox = (float)(Math.random() * 6 - 3);
            float oy = (float)(Math.random() * 6 - 3);
            particles.add(new SmokeParticle(wx + ox, wy + oy, 25 + (int)(Math.random() * 20)));
        }
    }

    // HEARTS: coraçãozinho rosa que flutua para cima e some
    private static void spawnHeart(float wx, float wy) {
        float ox = (float)(Math.random() * 8 - 4);
        particles.add(new HeartParticle(wx + ox, wy, 35 + (int)(Math.random() * 20)));
    }

    // STARS: estrela colorida que gira e pisca
    private static void spawnStar(float wx, float wy, Color base) {
        Color[] palette = {
            new Color(255, 100, 100), new Color(100, 200, 255),
            new Color(255, 220, 60),  new Color(160, 100, 255),
            new Color(100, 255, 160)
        };
        Color c = palette[(int)(Math.random() * palette.length)];
        particles.add(new StarParticle(wx, wy, c, 30 + (int)(Math.random() * 20)));
    }

    // NEON: anel que expande rapidamente e desaparece
    private static void spawnNeon(float wx, float wy, Color base) {
        particles.add(new NeonParticle(wx, wy, base, 18 + (int)(Math.random() * 10)));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private static Color blend(Color a, Color b, float t) {
        return new Color(
            (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t)
        );
    }

    // =========================================================================
    // PARTÍCULAS BASE
    // =========================================================================
    abstract static class TrailParticle {
        float x, y, life, maxLife;
        TrailParticle(float x, float y, int life) {
            this.x = x; this.y = y; this.life = this.maxLife = life;
        }
        float alpha() { return life / maxLife; }
        /** Retorna false quando deve ser removida */
        boolean update() { life--; return life > 0; }
        abstract void draw(Graphics2D g);
    }

    // ── SPARKLE ──
    static class SparkleParticle extends TrailParticle {
        float vx, vy, angle, size;
        Color color;
        SparkleParticle(float x, float y, float vx, float vy, Color c, int life) {
            super(x, y, life);
            this.vx = vx; this.vy = vy; this.color = c;
            this.size = 2f + (float)Math.random() * 3f;
            this.angle = (float)(Math.random() * Math.PI * 2);
        }
        @Override boolean update() {
            x += vx; y += vy;
            vy += 0.04f; // leve gravidade
            vx *= 0.96f;
            angle += 0.2f;
            size  *= 0.97f;
            return super.update();
        }
        @Override void draw(Graphics2D g) {
            float a = alpha();
            int alpha = (int)(a * 220);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            // desenha losango girado
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(x, y);
            g2.rotate(angle);
            float s = size;
            int[] xs = {0, (int)s, 0, -(int)s};
            int[] ys = {-(int)s, 0, (int)s, 0};
            g2.fillPolygon(xs, ys, 4);
            // brilho central
            g2.setColor(new Color(255, 255, 255, (int)(a * 150)));
            g2.fillOval(-(int)(s*0.3f), -(int)(s*0.3f), (int)(s*0.6f), (int)(s*0.6f));
            g2.dispose();
        }
    }

    // ── SMOKE ──
    static class SmokeParticle extends TrailParticle {
        float radius, vy;
        SmokeParticle(float x, float y, int life) {
            super(x, y, life);
            this.radius = 3f + (float)Math.random() * 3f;
            this.vy = -(float)Math.random() * 0.3f;
        }
        @Override boolean update() {
            y += vy; radius += 0.25f;
            return super.update();
        }
        @Override void draw(Graphics2D g) {
            float a = alpha() * 0.55f;
            int grey = 160 + (int)(Math.random() * 40);
            g.setColor(new Color(grey, grey, grey, (int)(a * 255)));
            g.fillOval((int)(x - radius), (int)(y - radius), (int)(radius * 2), (int)(radius * 2));
        }
    }

    // ── HEART ──
    static class HeartParticle extends TrailParticle {
        float size, vy, wobble;
        HeartParticle(float x, float y, int life) {
            super(x, y, life);
            this.size = 4f + (float)Math.random() * 4f;
            this.vy = -0.5f - (float)Math.random() * 0.4f;
            this.wobble = (float)(Math.random() * Math.PI * 2);
        }
        @Override boolean update() {
            y += vy; wobble += 0.15f;
            x += (float)Math.sin(wobble) * 0.4f;
            size *= 0.985f;
            return super.update();
        }
        @Override void draw(Graphics2D g) {
            float a = alpha();
            int alpha = (int)(a * 230);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            float s = size;
            // coração via bezier
            GeneralPath heart = new GeneralPath();
            heart.moveTo(0, s * 0.3f);
            heart.curveTo(-s, -s * 0.3f, -s * 1.2f, s * 0.8f, 0, s * 1.1f);
            heart.curveTo(s * 1.2f, s * 0.8f, s, -s * 0.3f, 0, s * 0.3f);
            // rosa vibrante com brilho
            g2.setColor(new Color(255, 80, 160, alpha));
            g2.fill(heart);
            g2.setColor(new Color(255, 180, 220, (int)(a * 120)));
            g2.setStroke(new BasicStroke(0.8f));
            g2.draw(heart);
            g2.dispose();
        }
    }

    // ── STAR ──
    static class StarParticle extends TrailParticle {
        float angle, size, rotSpeed;
        Color color;
        boolean pulsing;
        StarParticle(float x, float y, Color c, int life) {
            super(x, y, life);
            this.color = c;
            this.size  = 5f + (float)Math.random() * 5f;
            this.angle = (float)(Math.random() * Math.PI * 2);
            this.rotSpeed = (float)(Math.random() * 0.15 - 0.075);
            this.pulsing  = Math.random() > 0.5;
        }
        @Override boolean update() {
            angle += rotSpeed;
            if (pulsing) size += (float)Math.sin(life * 0.4) * 0.2f;
            return super.update();
        }
        @Override void draw(Graphics2D g) {
            float a = alpha();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.rotate(angle);
            // desenha estrela de 5 pontas
            GeneralPath star = makeStar(size, size * 0.4f, 5);
            // glow externo
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(a * 60)));
            Graphics2D g3 = (Graphics2D) g2.create();
            g3.scale(1.4f, 1.4f);
            g3.fill(star);
            g3.dispose();
            // estrela principal
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(a * 240)));
            g2.fill(star);
            // brilho central branco
            g2.setColor(new Color(255, 255, 255, (int)(a * 180)));
            g2.fillOval((int)(-size * 0.25f), (int)(-size * 0.25f), (int)(size * 0.5f), (int)(size * 0.5f));
            g2.dispose();
        }
        private GeneralPath makeStar(float outer, float inner, int points) {
            GeneralPath path = new GeneralPath();
            for (int i = 0; i < points * 2; i++) {
                double angle = Math.PI / points * i - Math.PI / 2;
                float r = (i % 2 == 0) ? outer : inner;
                float px = (float)(Math.cos(angle) * r);
                float py = (float)(Math.sin(angle) * r);
                if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            path.closePath();
            return path;
        }
    }

    // ── NEON ──
    static class NeonParticle extends TrailParticle {
        float radius, thickness;
        Color color;
        NeonParticle(float x, float y, Color c, int life) {
            super(x, y, life);
            this.color     = c;
            this.radius    = 4f;
            this.thickness = 3f;
        }
        @Override boolean update() {
            radius    += 1.4f;
            thickness *= 0.88f;
            return super.update();
        }
        @Override void draw(Graphics2D g) {
            float a = alpha();
            // anel externo (glow)
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(a * 80)));
            g.setStroke(new BasicStroke(thickness + 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval((int)(x - radius), (int)(y - radius), (int)(radius * 2), (int)(radius * 2));
            // anel principal nítido
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(a * 220)));
            g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval((int)(x - radius), (int)(y - radius), (int)(radius * 2), (int)(radius * 2));
            // core branco
            g.setColor(new Color(255, 255, 255, (int)(a * 140)));
            g.setStroke(new BasicStroke(Math.max(0.5f, thickness * 0.4f)));
            g.drawOval((int)(x - radius), (int)(y - radius), (int)(radius * 2), (int)(radius * 2));
            g.setStroke(new BasicStroke(1f)); // reset
        }
    }
}