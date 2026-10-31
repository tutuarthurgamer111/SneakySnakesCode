import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * ShopSystem - Renders and handles the Skin Shop screen.
 * Players spend coins earned in-game to unlock skins.
 * Navigated from the main menu.
 */

public class ShopSystem {
    private static final int COLS       = 4;    // skins per row
    private static final int CARD_W     = 160;
    private static final int CARD_H     = 130;
    private static final int CARD_PAD_X = 20;
    private static final int CARD_PAD_Y = 16;
    private static final int START_X    = 40;
    private static final int START_Y    = 100;

    private static final String ICON_PATH = "SneakySnakes/Resources/content/icons/";

    private int scrollOffset = 0;
    private int hoveredIndex = -1;

    private final BufferedImage ownedIcon  = loadIcon("ok.png");
    private final BufferedImage lockedIcon = loadIcon("lock.png");
    private final BufferedImage coinIcon   = loadIcon("coin.png");
    private final BufferedImage shopIcon   = loadIcon("skinshop.png");

    private static BufferedImage loadIcon(String filename) {
        try {
            File f = new File(ICON_PATH + filename);
            if (f.exists()) return ImageIO.read(f);
        } catch (Exception e) {
            System.err.println("[ShopSystem] Could not load icon: " + filename);
        }
        return null; // null is safe; all drawImage calls accept null (draws nothing)
    }

    /** Draw the full shop UI. Returns pixel height of content for scroll bounds. */
    public void draw(Graphics2D g, int screenW, int screenH, SaveSystem save, long tick) {
        List<SkinSystem.Skin> skins = SkinSystem.ALL_SKINS;

        // Background
        g.setColor(new Color(8, 10, 22));
        g.fillRect(0, 0, screenW, screenH);

        // Header panel
        g.setColor(new Color(15, 18, 40, 230));
        g.fillRect(0, 0, screenW, 80);
        g.setColor(new Color(80, 140, 255, 80));
        g.drawLine(0, 80, screenW, 80);

        g.setFont(new Font("Arial", Font.BOLD, 30));
        g.setColor(Color.WHITE);
        g.drawImage(shopIcon,30,18,32,32,null);
        g.drawString("SKIN SHOP",70,48);

        // Coins display
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.setColor(new Color(255, 215, 0));
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.setColor(new Color(255,215,0));

        String coinStr = save.coins + " coins";
        FontMetrics fm = g.getFontMetrics();

        g.drawImage(
            coinIcon,
            screenW - fm.stringWidth(coinStr) - 55,
            28,
            18,
            18,
            null
        );

        g.drawString(
            coinStr,
            screenW - fm.stringWidth(coinStr) - 30,
            48
        );
        g.drawString(coinStr, screenW - fm.stringWidth(coinStr) - 30, 48);

        // Back hint
        g.setFont(new Font("Arial", Font.PLAIN, 13));
        g.setColor(new Color(140, 180, 220, 160));
        g.drawString("ESC = Back to Menu", 30, 72);

        // Clip content area
        g.setClip(0, 82, screenW, screenH - 82);
        g.translate(0, -scrollOffset);

        // Draw skin cards
        for (int i = 0; i < skins.size(); i++) {
            SkinSystem.Skin skin = skins.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int cx = START_X + col * (CARD_W + CARD_PAD_X);
            int cy = START_Y + row * (CARD_H + CARD_PAD_Y);
            drawCard(g, skin, cx, cy, i == hoveredIndex, tick, save);
        }

        g.translate(0, scrollOffset);
        g.setClip(null);

        // Scroll indicator
        int totalRows = (int)Math.ceil(skins.size() / (double)COLS);
        int contentH  = START_Y + totalRows * (CARD_H + CARD_PAD_Y) + 20;
        int maxScroll = Math.max(0, contentH - screenH);
        if (maxScroll > 0) {
            float scrollPct = (float)scrollOffset / maxScroll;
            int barH = Math.max(30, (int)((float)(screenH - 82) / contentH * (screenH - 82)));
            int barY = 82 + (int)(scrollPct * (screenH - 82 - barH));
            g.setColor(new Color(80, 140, 255, 100));
            g.fillRoundRect(screenW - 8, barY, 5, barH, 4, 4);
        }
    }

    private void drawCard(Graphics2D g, SkinSystem.Skin skin, int x, int y,
                           boolean hovered, long tick, SaveSystem save) {
        boolean owned    = skin.unlocked;
        boolean canAfford = skin.coinCost > 0 && save.coins >= skin.coinCost;
        boolean isCurrent = SkinSystem.current() == skin;

        // Card background
        Color bg = isCurrent ? new Color(30, 60, 120, 220)
                 : hovered   ? new Color(25, 35, 70,  220)
                             : new Color(14, 18, 38,  210);
        g.setColor(bg);
        g.fillRoundRect(x, y, CARD_W, CARD_H, 14, 14);

        // Card border
        Color border = isCurrent ? new Color(100, 180, 255) :
                       owned     ? new Color(60, 200, 80, 180) :
                       hovered   ? new Color(80, 120, 255, 180) :
                                   new Color(50, 60, 100, 120);
        g.setColor(border);
        g.drawRoundRect(x, y, CARD_W, CARD_H, 14, 14);

        // Snake preview (mini 4-segment snake)
        int previewY = y + 20;
        int previewX = x + CARD_W/2 - 2*14;
        for (int seg = 3; seg >= 0; seg--) {
            SkinSystem.drawSegment(g, skin, previewX + seg * 14, previewY, 13,
                                   seg == 0, tick, seg);
        }
        // Eyes on head
        drawMiniEyes(g, previewX, previewY, 13);

        // Name
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        String name = skin.displayName;
        if (fm.stringWidth(name) > CARD_W - 10) {
            // Truncate
            while (fm.stringWidth(name + "…") > CARD_W - 10 && name.length() > 1)
                name = name.substring(0, name.length() - 1);
            name += "…";
        }
        g.drawString(name, x + (CARD_W - fm.stringWidth(name)) / 2, y + 55);

        // Status label
        if (isCurrent) {
            drawStatusBadge(g, x + CARD_W/2, y + 72, "EQUIPPED", new Color(100,200,255));
        } else if (owned) {
            drawStatusBadge(g, x + CARD_W/2 + 10, y + 72, "OWNED", new Color(80,220,100));
            g.drawImage(ownedIcon,x + CARD_W/2 - 40,y + 58,16,16,null);
        } else if (skin.unlockScore > 0) {
            drawStatusBadge(g, x + CARD_W/2, y + 72, "Score " + skin.unlockScore, new Color(200,180,80));
        } else if (skin.coinCost > 0) {
            g.drawImage(lockedIcon,x + CARD_W/2 - 42,y + 58,16,16,null);

            Color priceColor = canAfford ?
                new Color(255,215,0) :
                new Color(180,100,100);

            drawStatusBadge(
                g,
                x + CARD_W/2,
                y + 72,
                String.valueOf(skin.coinCost),
                priceColor
            );
        }

        // Texture badge
        if (skin.isTextureSkin()) {
            g.setFont(new Font("Arial", Font.BOLD, 9));
            g.setColor(new Color(180, 140, 255, 180));
            g.drawString("TEXTURE", x + 6, y + CARD_H - 8);
        }

        // Buy button for purchasable unowned skins
        if (!owned && skin.coinCost > 0) {
            Color btnC = canAfford ? new Color(50,160,90) : new Color(80,40,40);
            g.setColor(btnC);
            g.fillRoundRect(x + 20, y + CARD_H - 32, CARD_W - 40, 22, 8, 8);
            g.setFont(new Font("Arial", Font.BOLD, 11));
            g.setColor(canAfford ? Color.WHITE : new Color(160,100,100));
            String btn = canAfford ? "BUY" : "Need more coins";
            fm = g.getFontMetrics();
            g.drawString(btn, x + (CARD_W - fm.stringWidth(btn))/2, y + CARD_H - 15);
        }
    }

    private void drawStatusBadge(Graphics2D g, int cx, int y, String text, Color color) {
        g.setFont(new Font("Arial", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
        g.fillRoundRect(cx - tw/2 - 6, y - 12, tw + 12, 16, 6, 6);
        g.setColor(color);
        g.drawString(text, cx - tw/2, y);
    }

    private void drawMiniEyes(Graphics2D g, int hx, int hy, int size) {
        // Eyes facing right
        g.setColor(Color.WHITE);
        g.fillOval(hx + size - 5, hy + 2, 3, 3);
        g.fillOval(hx + size - 5, hy + 7, 3, 3);
        g.setColor(Color.BLACK);
        g.fillOval(hx + size - 4, hy + 3, 2, 2);
        g.fillOval(hx + size - 4, hy + 8, 2, 2);
    }

    /** Handle mouse click in shop. Returns true if a skin was purchased. */
    public boolean handleClick(int mx, int my, SaveSystem save) {
        List<SkinSystem.Skin> skins = SkinSystem.ALL_SKINS;
        for (int i = 0; i < skins.size(); i++) {
            SkinSystem.Skin skin = skins.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int cx = START_X + col * (CARD_W + CARD_PAD_X);
            int cy = START_Y + row * (CARD_H + CARD_PAD_Y) - scrollOffset;

            if (mx >= cx && mx <= cx + CARD_W && my >= cy && my <= cy + CARD_H) {
                if (skin.unlocked) {
                    // Equip
                    SkinSystem.setById(skin.id);
                    SoundSystem.playClick();
                    return false;
                } else if (skin.coinCost > 0 && save.coins >= skin.coinCost) {
                    // Buy
                    SkinSystem.buySkin(skin.id, save);
                    SkinSystem.setById(skin.id);
                    SoundSystem.playClick();
                    return true;
                }
            }
        }
        return false;
    }

    /** Update hover state. */
    public void handleMouseMove(int mx, int my) {
        List<SkinSystem.Skin> skins = SkinSystem.ALL_SKINS;
        hoveredIndex = -1;
        for (int i = 0; i < skins.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int cx = START_X + col * (CARD_W + CARD_PAD_X);
            int cy = START_Y + row * (CARD_H + CARD_PAD_Y) - scrollOffset;
            if (mx >= cx && mx <= cx + CARD_W && my >= cy && my <= cy + CARD_H) {
                hoveredIndex = i; break;
            }
        }
    }

    /** Scroll the shop. delta is mouse wheel rotation. */
    public void scroll(int delta, int screenH) {
        List<SkinSystem.Skin> skins = SkinSystem.ALL_SKINS;
        int totalRows = (int)Math.ceil(skins.size() / (double)COLS);
        int contentH  = START_Y + totalRows * (CARD_H + CARD_PAD_Y) + 20;
        int maxScroll = Math.max(0, contentH - screenH);
        scrollOffset  = Math.max(0, Math.min(maxScroll, scrollOffset + delta * 30));
    }
}