import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * AccessorySystem — Acessórios desenhados sobre a cabeça da cobra.
 *
 * Cada acessório tem:
 *  - Um PNG em Resources/content/accessories/ (opcional — usa fallback desenhado)
 *  - Um preço em moedas
 *  - Um deslocamento (offset) para ficar bem posicionado na cabeça
 *
 * Acessórios disponíveis:
 *   NONE, HAT_TOP, GLASSES, CROWN, BANDANA, HALO
 */
public class AccessorySystem {

    private static final String ASSET_PATH = "SneakySnakes/Resources/content/accessories/";

    // ── Tipos ────────────────────────────────────────────────────────────────
    public enum Accessory {
        NONE     ("None",     0,   "Free"),
        HAT_TOP  ("Top Hat",  100, "100 coins"),
        GLASSES  ("Glasses",  80,  "80 coins"),
        CROWN    ("Crown",    200, "200 coins"),
        BANDANA  ("Bandana",  80,  "80 coins"),
        HALO     ("Halo",     150, "150 coins");

        public final String displayName;
        public final int    coinCost;
        public final String costLabel;

        Accessory(String name, int cost, String label) {
            this.displayName = name; this.coinCost = cost; this.costLabel = label;
        }
    }

    // ── Estado global ────────────────────────────────────────────────────────
    private static Accessory   current  = Accessory.NONE;
    private static boolean[]   unlocked = new boolean[Accessory.values().length];

    // Cache de imagens PNG (carregados sob demanda)
    private static final BufferedImage[] images = new BufferedImage[Accessory.values().length];
    private static final boolean[]       tried  = new boolean[Accessory.values().length];

    static { unlocked[0] = true; }

    public static Accessory current()                   { return current; }
    public static void      set(Accessory a)            { current = a; }
    public static boolean   isUnlocked(Accessory a)     { return unlocked[a.ordinal()]; }
    public static void      unlock(Accessory a)         { unlocked[a.ordinal()] = true; }

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
        for (Accessory a : Accessory.values()) if (a.name().equalsIgnoreCase(id)) { current = a; return; }
    }

    // ── Desenho ──────────────────────────────────────────────────────────────
    /**
     * Desenha o acessório atual sobre a cabeça da cobra.
     * hx, hy = posição pixel (canto superior esquerdo do tile da cabeça)
     * size   = tamanho do tile
     * dirX, dirY = direção atual (para orientar o acessório)
     */
    public static void draw(Graphics2D g, Accessory acc, int hx, int hy, int size, int dirX, int dirY) {
        if (acc == Accessory.NONE) return;

        // Tenta carregar PNG
        BufferedImage img = loadImage(acc);
        if (img != null) {
            drawWithImage(g, img, hx, hy, size, dirX, dirY);
            return;
        }

        // Fallback: desenha programaticamente
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Centro da cabeça
        int cx = hx + size / 2;
        int cy = hy + size / 2;

        // Rotaciona de acordo com a direção
        double rot = directionAngle(dirX, dirY);
        g2.translate(cx, cy);
        g2.rotate(rot);
        g2.translate(-cx, -cy);

        switch (acc) {
            case HAT_TOP:  drawHatTop (g2, cx, hy, size); break;
            case GLASSES:  drawGlasses(g2, cx, cy, size); break;
            case CROWN:    drawCrown  (g2, cx, hy, size); break;
            case BANDANA:  drawBandana(g2, cx, cy, size); break;
            case HALO:     drawHalo   (g2, cx, hy, size); break;
            default: break;
        }
        g2.dispose();
    }

    // Conveniência: desenha o acessório atualmente equipado
    public static void draw(Graphics2D g, int hx, int hy, int size, int dirX, int dirY) {
        draw(g, current, hx, hy, size, dirX, dirY);
    }

    // ── Desenhos programáticos ───────────────────────────────────────────────

    private static void drawHatTop(Graphics2D g, int cx, int topY, int size) {
        int w  = (int)(size * 0.7f);
        int h  = (int)(size * 0.6f);
        int bh = (int)(size * 0.15f);
        int x  = cx - w / 2;
        int y  = topY - h + 2;

        // aba
        g.setColor(new Color(20, 20, 20));
        g.fillRoundRect(cx - (int)(w * 0.7f), y + h - bh, (int)(w * 1.4f), bh + 2, 4, 4);
        // corpo
        g.setColor(new Color(30, 30, 30));
        g.fillRect(x, y, w, h);
        // faixa
        g.setColor(new Color(200, 160, 40));
        g.fillRect(x, y + h - bh * 2, w, (int)(bh * 0.8f));
        // borda brilhante
        g.setColor(new Color(80, 80, 80));
        g.drawRect(x, y, w, h);
    }

    private static void drawGlasses(Graphics2D g, int cx, int cy, int size) {
        int r  = Math.max(3, size / 5);
        int ey = cy - size / 8;
        int gap = (int)(r * 0.4f);

        // lente esquerda
        g.setColor(new Color(100, 200, 255, 120));
        g.fillOval(cx - r * 2 - gap, ey - r, r * 2, r * 2);
        // lente direita
        g.fillOval(cx + gap, ey - r, r * 2, r * 2);
        // bordas
        g.setColor(new Color(40, 40, 40));
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(cx - r * 2 - gap, ey - r, r * 2, r * 2);
        g.drawOval(cx + gap, ey - r, r * 2, r * 2);
        // ponte central
        g.drawLine(cx - gap, ey, cx + gap, ey);
        // hastes
        g.drawLine(cx - r * 2 - gap, ey, cx - r * 2 - gap - r, ey + r / 2);
        g.drawLine(cx + gap + r * 2, ey, cx + gap + r * 2 + r, ey + r / 2);
        g.setStroke(new BasicStroke(1f));
    }

    private static void drawCrown(Graphics2D g, int cx, int topY, int size) {
        int w  = (int)(size * 0.8f);
        int h  = (int)(size * 0.5f);
        int x  = cx - w / 2;
        int y  = topY - h + 2;

        // corpo dourado
        int[] xs = { x, x + w/4, x + w/2, x + 3*w/4, x + w, x + w, x };
        int[] ys = { y + h/2, y, y + h/3, y, y + h/2, y + h, y + h };
        g.setColor(new Color(255, 200, 30));
        g.fillPolygon(xs, ys, 7);
        // borda escura
        g.setColor(new Color(180, 130, 0));
        g.setStroke(new BasicStroke(1.2f));
        g.drawPolygon(xs, ys, 7);
        // joias
        g.setColor(new Color(220, 50, 80));
        g.fillOval(x + w/2 - 2, y + h/3 - 2, 5, 5);
        g.setColor(new Color(80, 160, 255));
        g.fillOval(x + w/4 - 1, y + h/2, 4, 4);
        g.fillOval(x + 3*w/4 - 1, y + h/2, 4, 4);
        g.setStroke(new BasicStroke(1f));
    }

    private static void drawBandana(Graphics2D g, int cx, int cy, int size) {
        int r = size / 2 - 1;
        // triângulo frontal da bandana
        int[] xs = { cx - r, cx + r, cx };
        int[] ys = { cy - r / 3, cy - r / 3, cy + r / 2 };
        g.setColor(new Color(200, 40, 40));
        g.fillPolygon(xs, ys, 3);
        // nó atrás
        g.setColor(new Color(160, 20, 20));
        g.fillOval(cx + r - 3, cy - 4, 8, 6);
        // pontinho branco
        g.setColor(new Color(255, 255, 255, 100));
        g.fillOval(cx - 3, cy - size / 8, 5, 3);
        // borda
        g.setColor(new Color(120, 10, 10, 150));
        g.setStroke(new BasicStroke(1f));
        g.drawPolygon(xs, ys, 3);
        g.setStroke(new BasicStroke(1f));
    }

    private static void drawHalo(Graphics2D g, int cx, int topY, int size) {
        int rx = (int)(size * 0.45f);
        int ry = (int)(size * 0.13f);
        int y  = topY - (int)(size * 0.15f);

        // brilho externo
        g.setColor(new Color(255, 240, 80, 40));
        g.setStroke(new BasicStroke(6f));
        g.drawOval(cx - rx - 3, y - ry - 3, (rx + 3) * 2, (ry + 3) * 2);
        // anel principal
        g.setColor(new Color(255, 230, 60, 220));
        g.setStroke(new BasicStroke(3.5f));
        g.drawOval(cx - rx, y - ry, rx * 2, ry * 2);
        // brilho branco central
        g.setColor(new Color(255, 255, 255, 160));
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(cx - rx, y - ry, rx * 2, ry * 2);
        g.setStroke(new BasicStroke(1f));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private static void drawWithImage(Graphics2D g, BufferedImage img, int hx, int hy, int size, int dirX, int dirY) {
        int iw = (int)(size * 1.1f), ih = (int)(size * 1.1f);
        int cx = hx + size / 2, cy = hy + size / 2;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(cx, cy);
        g2.rotate(directionAngle(dirX, dirY));
        g2.drawImage(img, -iw / 2, -ih / 2 - size / 3, iw, ih, null);
        g2.dispose();
    }

    private static double directionAngle(int dx, int dy) {
        if (dx ==  1) return 0;
        if (dx == -1) return Math.PI;
        if (dy == -1) return -Math.PI / 2;
        return Math.PI / 2;
    }

    private static BufferedImage loadImage(Accessory acc) {
        int idx = acc.ordinal();
        if (tried[idx]) return images[idx];
        tried[idx] = true;
        try {
            File f = new File(ASSET_PATH + acc.name().toLowerCase() + ".png");
            if (f.exists()) images[idx] = ImageIO.read(f);
        } catch (Exception ignored) {}
        return images[idx];
    }
}