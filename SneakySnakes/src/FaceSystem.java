import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * FaceSystem — Rostos personalizados para a cabeça da cobra.
 *
 * Cada rosto substitui os olhos padrão.
 * Se existir um PNG em Resources/content/faces/<id>.png, ele é usado.
 * Caso contrário, o rosto é desenhado programaticamente.
 *
 * Rostos disponíveis:
 *   DEFAULT  — olhos padrão do jogo (sem substituição)
 *   ANGRY    — sobrancelhas furiosas
 *   HAPPY    — olhinhos felizes com sorriso
 *   COOL     — óculos escuros + expressão relaxada
 *   SLEEPY   — olhos meio fechados
 *   DEAD     — olhos em X (assustador/cômico)
 *   LOVE     — olhos de coração
 */
public class FaceSystem {

    private static final String ASSET_PATH = "SneakySnakes/Resources/content/faces/";

    // ── Tipos ────────────────────────────────────────────────────────────────
    public enum Face {
        DEFAULT ("Default", 0,   "Free"),
        ANGRY   ("Angry",   80,  "80 coins"),
        HAPPY   ("Happy",   80,  "80 coins"),
        COOL    ("Cool",    120, "120 coins"),
        SLEEPY  ("Sleepy",  100, "100 coins"),
        DEAD    ("Dead",    100, "100 coins"),
        LOVE    ("Love",    150, "150 coins");

        public final String displayName;
        public final int    coinCost;
        public final String costLabel;

        Face(String name, int cost, String label) {
            this.displayName = name; this.coinCost = cost; this.costLabel = label;
        }
    }

    // ── Estado global ────────────────────────────────────────────────────────
    private static Face      current  = Face.DEFAULT;
    private static boolean[] unlocked = new boolean[Face.values().length];

    private static final BufferedImage[] images = new BufferedImage[Face.values().length];
    private static final boolean[]       tried  = new boolean[Face.values().length];

    static { unlocked[0] = true; }

    public static Face    current()               { return current; }
    public static void    set(Face f)             { current = f; }
    public static boolean isUnlocked(Face f)      { return unlocked[f.ordinal()]; }
    public static void    unlock(Face f)          { unlocked[f.ordinal()] = true; }

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
        for (Face f : Face.values()) if (f.name().equalsIgnoreCase(id)) { current = f; return; }
    }

    // ── Desenho principal ────────────────────────────────────────────────────
    /**
     * Chama este método em vez de drawEyes() no SkinSystem/GamePanel.
     * Se o rosto for DEFAULT, chama o drawEyes padrão passado como lambda.
     *
     * hx, hy = canto superior esquerdo do tile da cabeça (pixels)
     * size   = tamanho do tile
     * dirX, dirY = direção
     */
    public static void drawFace(Graphics2D g, Face face, int hx, int hy, int size, int dirX, int dirY) {
        if (face == Face.DEFAULT) {
            drawDefaultEyes(g, hx, hy, size, dirX, dirY);
            return;
        }

        // Tenta PNG
        BufferedImage img = loadImage(face);
        if (img != null) {
            drawWithImage(g, img, hx, hy, size, dirX, dirY);
            return;
        }

        // Fallback programático
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cx = hx + size / 2, cy = hy + size / 2;

        // Normaliza para direção → direita (rotaciona)
        g2.translate(cx, cy);
        g2.rotate(directionAngle(dirX, dirY));
        g2.translate(-cx, -cy);

        switch (face) {
            case ANGRY:  drawAngry (g2, hx, hy, size); break;
            case HAPPY:  drawHappy (g2, hx, hy, size); break;
            case COOL:   drawCool  (g2, hx, hy, size); break;
            case SLEEPY: drawSleepy(g2, hx, hy, size); break;
            case DEAD:   drawDead  (g2, hx, hy, size); break;
            case LOVE:   drawLove  (g2, hx, hy, size); break;
            default: drawDefaultEyes(g2, hx, hy, size, 1, 0);
        }
        g2.dispose();
    }

    // Conveniência com rosto atual
    public static void drawFace(Graphics2D g, int hx, int hy, int size, int dirX, int dirY) {
        drawFace(g, current, hx, hy, size, dirX, dirY);
    }

    // ── Rostos programáticos ─────────────────────────────────────────────────

    // olhos padrão (brancos com pupila preta)
    private static void drawDefaultEyes(Graphics2D g, int hx, int hy, int size, int dx, int dy) {
        int off = size / 4, es = Math.max(2, size / 5);
        int ex1, ey1, ex2, ey2;
        if      (dx ==  1) { ex1=hx+size-off-2; ey1=hy+off;         ex2=hx+size-off-2; ey2=hy+size-off*2; }
        else if (dx == -1) { ex1=hx+2;           ey1=hy+off;         ex2=hx+2;           ey2=hy+size-off*2; }
        else if (dy == -1) { ex1=hx+off;          ey1=hy+2;           ex2=hx+size-off*2;  ey2=hy+2; }
        else               { ex1=hx+off;          ey1=hy+size-off-2;  ex2=hx+size-off*2;  ey2=hy+size-off-2; }
        g.setColor(Color.WHITE);
        g.fillOval(ex1-es/2,ey1-es/2,es,es); g.fillOval(ex2-es/2,ey2-es/2,es,es);
        g.setColor(Color.BLACK);
        g.fillOval(ex1-es/4,ey1-es/4,es/2,es/2); g.fillOval(ex2-es/4,ey2-es/4,es/2,es/2);
    }

    // ANGRY: olhos apertados com sobrancelha inclinada
    private static void drawAngry(Graphics2D g, int hx, int hy, int size) {
        // olhos: elipses vermelhas-escuras
        int ew = Math.max(3, size / 4), eh = Math.max(2, size / 6);
        int ey = hy + size / 3;
        int ex1 = hx + size - ew - size / 6;
        int ex2 = hx + size - ew - size / 6; // posição frontal (cabeça olha pra direita)

        // olho superior
        g.setColor(Color.WHITE);
        g.fillOval(ex1 - 1, ey - size/6, ew, eh);
        // olho inferior
        g.fillOval(ex1 - 1, ey + size/6, ew, eh);
        g.setColor(new Color(200, 40, 40));
        g.fillOval(ex1, ey - size/6 + 1, ew - 2, eh - 1);
        g.fillOval(ex1, ey + size/6 + 1, ew - 2, eh - 1);

        // sobrancelhas furiosas (linhas diagonais)
        g.setColor(new Color(60, 20, 20));
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(ex1 - 2, ey - size/6 - 2, ex1 + ew + 1, ey - size/6 - size/8);
        g.drawLine(ex1 - 2, ey + size/6 + size/8 + eh, ex1 + ew + 1, ey + size/6 + 2 + eh);
        g.setStroke(new BasicStroke(1f));
    }

    // HAPPY: olhinhos curvados (arcos) e bocão
    private static void drawHappy(Graphics2D g, int hx, int hy, int size) {
        int ey = hy + size / 3;
        int ex = hx + size - size / 4;
        int ew = Math.max(4, size / 4);

        g.setColor(Color.WHITE);
        g.fillOval(ex - ew, ey - size/6, ew, ew);
        g.fillOval(ex - ew, ey + size/6, ew, ew);

        // arco feliz nos olhos
        g.setColor(new Color(50, 50, 50));
        g.setStroke(new BasicStroke(1.5f));
        g.drawArc(ex - ew + 1, ey - size/6, ew - 2, ew - 2, 0, -180);
        g.drawArc(ex - ew + 1, ey + size/6, ew - 2, ew - 2, 0, -180);

        // sorriso pequeno
        g.setColor(new Color(255, 100, 100));
        int sx = hx + size - size / 5;
        int sy = hy + size * 2 / 3;
        g.setStroke(new BasicStroke(1.5f));
        g.drawArc(sx - ew / 2, sy - ew / 4, ew, ew / 2, 0, -180);
        g.setStroke(new BasicStroke(1f));
    }

    // COOL: óculos escuros retangulares
    private static void drawCool(Graphics2D g, int hx, int hy, int size) {
        int ey  = hy + size * 2 / 5;
        int ex  = hx + size - size / 5;
        int lw  = size / 3, lh = size / 5;

        // lentes escuras
        g.setColor(new Color(20, 20, 20, 200));
        g.fillRoundRect(ex - lw, ey - lh, lw, lh, 3, 3);
        g.fillRoundRect(ex - lw, ey + size/8, lw, lh, 3, 3);
        // borda metálica
        g.setColor(new Color(160, 160, 160));
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(ex - lw, ey - lh, lw, lh, 3, 3);
        g.drawRoundRect(ex - lw, ey + size/8, lw, lh, 3, 3);
        // reflexo
        g.setColor(new Color(255, 255, 255, 60));
        g.fillRect(ex - lw + 2, ey - lh + 1, lw / 3, lh / 2);
        g.fillRect(ex - lw + 2, ey + size/8 + 1, lw / 3, lh / 2);
        g.setStroke(new BasicStroke(1f));
    }

    // SLEEPY: olhos meio fechados (semicírculo)
    private static void drawSleepy(Graphics2D g, int hx, int hy, int size) {
        int ey = hy + size / 3;
        int ex = hx + size - size / 4;
        int ew = Math.max(4, size / 4);

        // fundo branco
        g.setColor(Color.WHITE);
        g.fillOval(ex - ew, ey - size/6, ew, ew);
        g.fillOval(ex - ew, ey + size/6, ew, ew);

        // pálpebras pesadas (retângulos escuros cobrindo metade)
        g.setColor(new Color(80, 60, 40));
        g.fillRect(ex - ew, ey - size/6, ew, ew / 2);
        g.fillRect(ex - ew, ey + size/6, ew, ew / 2);

        // pupilas pequenas
        g.setColor(new Color(40, 40, 40));
        g.fillOval(ex - ew/2 - 1, ey - size/6 + ew/2 - 1, 3, 3);
        g.fillOval(ex - ew/2 - 1, ey + size/6 + ew/2 - 1, 3, 3);

        // Zzz pequeno
        g.setFont(new Font("Arial", Font.BOLD, Math.max(5, size / 5)));
        g.setColor(new Color(180, 200, 255, 200));
        g.drawString("z", hx + size - 3, hy + 4);
    }

    // DEAD: olhos em X
    private static void drawDead(Graphics2D g, int hx, int hy, int size) {
        int ey  = hy + size / 3;
        int ex  = hx + size - size / 4;
        int ew  = Math.max(4, size / 4);
        int off = ew / 2;

        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // X superior
        g.setColor(Color.WHITE);
        g.fillOval(ex - ew, ey - size/6, ew, ew);
        g.setColor(new Color(220, 50, 50));
        g.drawLine(ex - ew + 2, ey - size/6 + 2, ex - 2, ey - size/6 + ew - 2);
        g.drawLine(ex - 2, ey - size/6 + 2, ex - ew + 2, ey - size/6 + ew - 2);

        // X inferior
        g.setColor(Color.WHITE);
        g.fillOval(ex - ew, ey + size/6, ew, ew);
        g.setColor(new Color(220, 50, 50));
        g.drawLine(ex - ew + 2, ey + size/6 + 2, ex - 2, ey + size/6 + ew - 2);
        g.drawLine(ex - 2, ey + size/6 + 2, ex - ew + 2, ey + size/6 + ew - 2);

        g.setStroke(new BasicStroke(1f));
    }

    // LOVE: olhos de coração
    private static void drawLove(Graphics2D g, int hx, int hy, int size) {
        int ey = hy + size / 3;
        int ex = hx + size - size / 4;
        int ew = Math.max(4, size / 4);

        drawHeartEye(g, ex - ew / 2, ey - size/6 + ew/2, ew * 0.55f);
        drawHeartEye(g, ex - ew / 2, ey + size/6 + ew/2, ew * 0.55f);
    }

    private static void drawHeartEye(Graphics2D g, float cx, float cy, float s) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(cx, cy);
        GeneralPath heart = new GeneralPath();
        heart.moveTo(0, s * 0.3f);
        heart.curveTo(-s, -s * 0.3f, -s * 1.1f, s * 0.7f, 0, s);
        heart.curveTo(s * 1.1f, s * 0.7f, s, -s * 0.3f, 0, s * 0.3f);
        g2.setColor(new Color(255, 60, 120));
        g2.fill(heart);
        g2.setColor(new Color(255, 160, 200, 150));
        g2.setStroke(new BasicStroke(0.7f));
        g2.draw(heart);
        g2.dispose();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private static void drawWithImage(Graphics2D g, BufferedImage img, int hx, int hy, int size, int dx, int dy) {
        int cx = hx + size / 2, cy = hy + size / 2;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(cx, cy);
        g2.rotate(directionAngle(dx, dy));
        g2.drawImage(img, -size / 2, -size / 2, size, size, null);
        g2.dispose();
    }

    private static double directionAngle(int dx, int dy) {
        if (dx ==  1) return 0;
        if (dx == -1) return Math.PI;
        if (dy == -1) return -Math.PI / 2;
        return Math.PI / 2;
    }

    private static BufferedImage loadImage(Face face) {
        int idx = face.ordinal();
        if (tried[idx]) return images[idx];
        tried[idx] = true;
        try {
            File f = new File(ASSET_PATH + face.name().toLowerCase() + ".png");
            if (f.exists()) images[idx] = ImageIO.read(f);
        } catch (Exception ignored) {}
        return images[idx];
    }
}