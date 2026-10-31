import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GamePanel - Core game class (v3).
 * New in v3:
 *  - Menu redesign: Online Mode, Offline Mode, Skin Shop, Mode Options, skin selector
 *  - Mode Options popup: Party Mode / Infinite Mode (UI only)
 *  - Online Mode: connects to WebSocket server via OnlineClient
 *  - Offline Mode: original single-player game
 */
public class GamePanel extends JPanel implements ActionListener, KeyListener,
        MouseListener, MouseMotionListener, MouseWheelListener {

    // -- Constants
    public static final int WIDTH  = 960;
    public static final int HEIGHT = 600;
    public static final int TILE   = 20;
    public static final int COLS   = WIDTH  / TILE;
    public static final int ROWS   = HEIGHT / TILE;
    public static final int WIN_LENGTH = 60;

    // -- State machine
    private enum Screen { MENU, PLAYING, PAUSED, GAME_OVER, VICTORY, SHOP, ONLINE, GAME_MODES, SETTINGS, EDIT_PROFILE }
    private Screen screen = Screen.MENU;

    // -- Sub-systems
    private final SaveSystem     save      = new SaveSystem();
    private final SettingsSystem settings  = new SettingsSystem();
    private final ParticleSystem particles = new ParticleSystem();
    private final GameState      gs        = new GameState();
    private final ShopSystem     shop      = new ShopSystem();
    private final Random         rng       = new Random();
    private String selectedGameMode = "NORMAL";

    private static final String ICON_PATH = "SneakySnakes/Resources/content/icons/";
    private final java.awt.image.BufferedImage skinShopIcon = loadIcon("skinshop.png");
    private final java.awt.image.BufferedImage coinIcon     = loadIcon("coin.png");
    private final java.awt.image.BufferedImage moonIcon     = loadIcon("moon.png");
    private final java.awt.image.BufferedImage sunIcon      = loadIcon("sun.png");
    private final java.awt.image.BufferedImage levelUpIcon  = loadIcon("levelup.png");
    private final java.awt.image.BufferedImage badgeIcon    = loadIcon("badge.png");
    private final java.awt.image.BufferedImage okIcon       = loadIcon("ok.png");
    private final java.awt.image.BufferedImage enoughIcon   = loadIcon("enough.png");
    private final java.awt.image.BufferedImage gearIcon     = loadIcon("gear.png");
    private final java.awt.image.BufferedImage pencilIcon   = loadIcon("pencil.png");

    private static java.awt.image.BufferedImage loadIcon(String filename) {
        try {
            java.io.File f = new java.io.File(ICON_PATH + filename);
            if (f.exists()) return javax.imageio.ImageIO.read(f);
        } catch (Exception e) {
            System.err.println("[GamePanel] Could not load icon: " + filename);
        }
        return null;
    }

    private void loadCustomCursor() {
        try {
            java.io.File f = new java.io.File("SneakySnakes/Resources/content/CursorDefault.png");
            if (f.exists()) {
                java.awt.image.BufferedImage cursorImg = javax.imageio.ImageIO.read(f);
                Toolkit tk = Toolkit.getDefaultToolkit();
                Cursor customCursor = tk.createCustomCursor(cursorImg, new Point(0, 0), "GameCursor");
                setCursor(customCursor);
            }
        } catch (Exception e) {
            System.err.println("[GamePanel] Could not load custom cursor.");
        }
    }

    // -- Online client
    private OnlineGamePanel onlinePanel = null;

    // -- Multi-food list
    private final List<int[]> foods = new ArrayList<>();

    // -- Timing
    private Timer gameTimer;
    private long  tickCount   = 0;
    private long  lastTime    = System.currentTimeMillis();
    private int   fpsCounter  = 0;
    private int   fps         = 60;
    private long  fpsTimer    = 0;
    private int   snakeTickAccum = 0;
    private int   snakeTickRate  = 8;

    // -- Menu animation
    private float menuTitleBob  = 0f;
    private float menuTitleGlow = 0f;
    private boolean menuGlowDir = true;

    // -- Day/Night
    private boolean nightMode    = false;
    private float   dayNightBlend = 0f;

    // -- Banners
    private float levelUpTimer       = 0f;
    private int   displayedLevel     = 1;
    private float comboDisplayTimer  = 0f;
    private int   lastDisplayedCombo = 1;

    // -- Overlays
    private float overlayAlpha  = 0f;
    private int   confettiTimer = 0;

    // -- Double-buffer
    private BufferedImage buffer;
    private Graphics2D    bufferG;

    // -- Hover states (menu)
    private boolean onlineHover      = false;
    private boolean offlineHover     = false;
    private boolean shopHover        = false;
    private boolean modeOptionsHover = false;

    // -- Hover states (game over / victory)
    private boolean restartHover = false;
    private boolean menuHover    = false;

    // -- Mode Options popup hover
    private boolean normalModeHover   = false;
    private boolean partyModeHover    = false;
    private boolean infiniteModeHover = false;
    private boolean closeOptionsHover = false;

    // -- Skin arrow hover
    private boolean skinLeftHover  = false;
    private boolean skinRightHover = false;

    // -- Bottom menu buttons hover
    private boolean settingsHover  = false;
    private boolean editHover      = false;

    // -- Settings overlay state
    private int     settingsThemeIdx = 0;  // index into Theme enum
    private float   settingsVolume   = 1.0f;
    private boolean settingsSaveHover = false;
    private boolean settingsThemeHover = false;
    private boolean settingsVolDownHover = false;
    private boolean settingsVolUpHover   = false;

    // -- Edit Profile overlay state
    private String  editNameBuffer  = "";
    private boolean editNameFocus   = false;
    private boolean editSkinHover   = false;
    private boolean editSaveHover   = false;

    // -- Theme colors (current)
    private Color themeGridColor   = new Color(255,255,255,18);
    private Color themeBgColor     = new Color(30,35,50);
    private Color themeBgNight     = new Color(8,8,18);

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);

        buffer  = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        bufferG = buffer.createGraphics();
        bufferG.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        bufferG.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        bufferG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        save.load();
        settings.load();
        SkinSystem.deserializeUnlocked(save.unlockedSkins);
        AchievementSystem.deserialize(save.achievements);
        SkinSystem.setById(save.currentSkinId);
        SkinSystem.checkUnlocks(save.highScore);
        SkinSystem.preloadTextures();
        applyTheme(settings.theme);
        SoundSystem.setVolume(settings.volume);
        settingsVolume   = settings.volume;
        settingsThemeIdx = settings.theme.ordinal();
        editNameBuffer   = settings.playerName;
        SoundSystem.startMenuMusic();
        loadCustomCursor();
        gameTimer = new Timer(16, this);
        gameTimer.start();
    }

    // ==========================================================
    // GAME LOOP
    // ==========================================================

    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.currentTimeMillis();
        long dt  = now - lastTime;
        lastTime = now;
        tickCount++;
        fpsCounter++;
        fpsTimer += dt;
        if (fpsTimer >= 1000) { fps = fpsCounter; fpsCounter = 0; fpsTimer = 0; }
        update(dt);
        drawToBuffer();
        repaint();
    }

    private void update(long dt) {
        AchievementSystem.update();
        particles.update();
        float targetBlend = nightMode ? 1f : 0f;
        dayNightBlend += (targetBlend - dayNightBlend) * 0.05f;
        if (levelUpTimer   > 0) levelUpTimer   -= dt / 1000f;
        if (comboDisplayTimer > 0) comboDisplayTimer -= dt / 1000f;

        switch (screen) {
            case MENU:         updateMenu();    break;
            case PLAYING:      updatePlaying(); break;
            case GAME_OVER:    updateGameOver(); break;
            case VICTORY:      updateVictory(); break;
            case GAME_MODES: updateMenu(); break; // keep particles moving
            default: break;
        }
    }

    private void updateMenu() {
        menuTitleBob += 0.04f;
        if (menuGlowDir) { menuTitleGlow += 0.02f; if (menuTitleGlow > 1f) menuGlowDir = false; }
        else             { menuTitleGlow -= 0.02f; if (menuTitleGlow < 0f) menuGlowDir = true; }
        particles.spawnMenuDecor(WIDTH, HEIGHT);
    }

    private void updatePlaying() {
        gs.updateFoodPulse();
        gs.updateShake();
        gs.gameTimeSeconds = (System.currentTimeMillis() - gs.gameStartTime) / 1000;
        long now = System.currentTimeMillis();
        if (gs.comboLevel > 1 && (now - gs.lastFoodTime) > GameState.COMBO_WINDOW_MS)
            gs.comboLevel = 1;
        snakeTickAccum++;
        if (snakeTickAccum >= snakeTickRate) { snakeTickAccum = 0; moveSnake(); }
        AchievementSystem.checkConditions(gs);
    }

    private void updateGameOver()  { overlayAlpha = Math.min(1f, overlayAlpha + 0.025f); gs.updateShake(); }
    private void updateVictory()   {
        overlayAlpha = Math.min(1f, overlayAlpha + 0.015f);
        confettiTimer++;
        if (confettiTimer % 30 == 0) particles.spawnConfetti(WIDTH);
    }

    // ==========================================================
    // SNAKE LOGIC (Offline)
    // ==========================================================

    private int targetFoodCount() {
        return Math.min(gs.level, 5);
    }

    private void moveSnake() {
        if (!gs.alive) return;
        gs.dirX = gs.nextDirX; gs.dirY = gs.nextDirY;
        int newHX = gs.bodyX[0] + gs.dirX;
        int newHY = gs.bodyY[0] + gs.dirY;

        if ("INFINITE".equals(selectedGameMode)) { if(newHX<0)newHX=COLS-1;else if(newHX>=COLS)newHX=0;if(newHY<0)newHY=ROWS-1;else if(newHY>=ROWS)newHY=0; } else if (newHX < 0 || newHX >= COLS || newHY < 0 || newHY >= ROWS) { triggerGameOver(); return; }

        int checkLen = (gs.pendingGrowth > 0) ? gs.length : gs.length - 1;
        for (int i = 0; i < checkLen; i++) {
            if (gs.bodyX[i] == newHX && gs.bodyY[i] == newHY) { triggerGameOver(); return; }
        }

        if (gs.pendingGrowth > 0) {
            System.arraycopy(gs.bodyX, 0, gs.bodyX, 1, gs.length);
            System.arraycopy(gs.bodyY, 0, gs.bodyY, 1, gs.length);
            gs.length++;
            gs.pendingGrowth--;
        } else {
            System.arraycopy(gs.bodyX, 0, gs.bodyX, 1, gs.length - 1);
            System.arraycopy(gs.bodyY, 0, gs.bodyY, 1, gs.length - 1);
        }
        gs.bodyX[0] = newHX;
        gs.bodyY[0] = newHY;

        SkinSystem.Skin skin = SkinSystem.current();
        particles.spawnTrail(newHX * TILE + TILE/2f, newHY * TILE + TILE/2f, skin.glow);

        for (int fi = foods.size() - 1; fi >= 0; fi--) {
            int[] food = foods.get(fi);
            if (food[0] == newHX && food[1] == newHY) {
                GameState.FoodRarity rarity = GameState.FoodRarity.values()[food[2]];
                foods.remove(fi);
                eatFood(rarity, newHX, newHY);
                break;
            }
        }

        while (foods.size() < targetFoodCount()) spawnOneFood();

        if (gs.length >= WIN_LENGTH && !"INFINITE".equals(selectedGameMode)) triggerVictory();
    }

    private void eatFood(GameState.FoodRarity rarity, int fx, int fy) {
        SoundSystem.play("eat");
        gs.foodsEaten++;
        save.foodsEaten++;

        long now = System.currentTimeMillis();
        if ((now - gs.lastFoodTime) <= GameState.COMBO_WINDOW_MS)
            gs.comboLevel = Math.min(gs.comboLevel + 1, 10);
        else
            gs.comboLevel = 1;
        gs.lastFoodTime = now;

        if (gs.comboLevel > 1) {
            SoundSystem.play("combo");
            lastDisplayedCombo = gs.comboLevel;
            comboDisplayTimer  = 1.8f;
            particles.spawnCombo(fx * TILE + TILE/2f, fy * TILE + TILE/2f, gs.comboLevel);
        }

        gs.score        += gs.scoreGain(rarity);
        gs.xp           += gs.xpGain(rarity);
        gs.pendingGrowth += rarity.growthBonus;

        int coinsGained = (int)((rarity.ordinal() + 1) * gs.comboMultiplier());
        save.coins += coinsGained;

        particles.spawnFoodCollect(fx*TILE+TILE/2f, fy*TILE+TILE/2f, rarity.glowColor, rarity.particleCount);

        if (rarity == GameState.FoodRarity.LEGENDARY) {
            AchievementSystem.unlock("legendary");
            particles.spawnAchievement(fx*TILE+TILE/2f, fy*TILE+TILE/2f);
        }

        checkLevelUp();

        if (gs.score > save.highScore) { save.highScore = gs.score; SkinSystem.checkUnlocks(save.highScore); }
        if (gs.comboLevel > save.bestCombo) save.bestCombo = gs.comboLevel;

        snakeTickRate = Math.max(3, 10 - gs.level);
    }

    private void checkLevelUp() {
        while (gs.level < 8) {
            int need = GameState.xpForNextLevel(gs.level);
            if (gs.xp < need) break;
            gs.xp -= need;
            gs.level++;
            displayedLevel = gs.level;
            levelUpTimer   = 2.5f;
            SoundSystem.play("levelup");
            particles.spawnLevelUp(WIDTH/2f, HEIGHT/2f);
            gs.triggerShake(6f);
            if (gs.level > save.highestLevel) save.highestLevel = gs.level;
            while (foods.size() < targetFoodCount()) spawnOneFood();
        }
    }

    private void spawnOneFood() {
        GameState.FoodRarity rarity = pickRarity();
        int fx, fy;
        int tries = 0;
        outer:
        do {
            fx = rng.nextInt(COLS);
            fy = rng.nextInt(ROWS);
            tries++;
            if (tries > 500) break;
            for (int i = 0; i < gs.length; i++)
                if (gs.bodyX[i] == fx && gs.bodyY[i] == fy) continue outer;
            for (int[] f : foods)
                if (f[0] == fx && f[1] == fy) continue outer;
            break;
        } while (true);
        foods.add(new int[]{fx, fy, rarity.ordinal()});
    }

    private GameState.FoodRarity pickRarity() {
        int roll = rng.nextInt(100);
        if (roll < 2)  return GameState.FoodRarity.LEGENDARY;
        if (roll < 10) return GameState.FoodRarity.EPIC;
        if (roll < 30) return GameState.FoodRarity.RARE;
        return GameState.FoodRarity.NORMAL;
    }

    private void triggerGameOver() {
        gs.alive = false;
        screen   = Screen.GAME_OVER;
        overlayAlpha = 0f;
        save.gamesPlayed++;
        save.deaths++;
        save.totalPlayTime += gs.gameTimeSeconds;
        SoundSystem.stopMusic();
        SoundSystem.playHit();
        particles.spawnGameOver(WIDTH, HEIGHT);
        gs.triggerShake(12f);
        commitSave();
    }

    private void triggerVictory() {
        gs.alive = false;
        screen   = Screen.VICTORY;
        overlayAlpha = 0f;
        save.gamesPlayed++;
        save.totalPlayTime += gs.gameTimeSeconds;
        SoundSystem.stopMusic();
        SoundSystem.play("win");
        commitSave();
    }

    private void startNewGame() {
        gs.length = 4;
        for (int i = 0; i < gs.length; i++) { gs.bodyX[i] = COLS/2 - i; gs.bodyY[i] = ROWS/2; }
        gs.dirX=1; gs.dirY=0; gs.nextDirX=1; gs.nextDirY=0;
        gs.score=0; gs.xp=0; gs.level=1;
        gs.comboLevel=1; gs.lastFoodTime=0;
        gs.foodsEaten=0; gs.pendingGrowth=0;
        gs.alive=true; gs.paused=false; gs.shakeMag=0;
        gs.gameStartTime = System.currentTimeMillis();
        gs.gameTimeSeconds = 0;
        overlayAlpha=0f; levelUpTimer=0; comboDisplayTimer=0; displayedLevel=1;
        snakeTickRate=8; snakeTickAccum=0;
        particles.clear();
        foods.clear();
        for (int i = 0; i < targetFoodCount(); i++) spawnOneFood();
        screen = Screen.PLAYING;
        SoundSystem.startRandomTrack();
    }

    private void goToMenu() {
        screen = Screen.MENU;
        SoundSystem.stopMusic();
        SoundSystem.startMenuMusic();
        commitSave();
    }

    private void commitSave() {
        save.unlockedSkins = SkinSystem.serializeUnlocked();
        save.achievements  = AchievementSystem.serialize();
        save.currentSkinId = SkinSystem.current().id;
        save.save();
    }

    // ==========================================================
    // RENDERING
    // ==========================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        drawAspectFit(g, buffer);
    }

    private void drawAspectFit(Graphics graphics, BufferedImage image) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        int drawW = Math.max(1, getWidth());
        int drawH = Math.max(1, getHeight());
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(image, 0, 0, drawW, drawH, null);
        g2.dispose();
    }

    private void drawToBuffer() {
        Graphics2D g = bufferG;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate((int)gs.shakeX, (int)gs.shakeY);

        drawBackground(g);

        switch (screen) {
            case MENU:         drawMenu(g);    break;
            case GAME_MODES: drawMenu(g); drawModeOptionsPopup(g); break;
            case SETTINGS:     drawMenu(g); drawSettingsOverlay(g);  break;
            case EDIT_PROFILE: drawMenu(g); drawEditProfileOverlay(g); break;
            case SHOP:         drawShopScreen(g); break;
            case PLAYING:      drawPlaying(g); break;
            case PAUSED:       drawPlaying(g); drawPauseOverlay(g);   break;
            case GAME_OVER:    drawPlaying(g); drawGameOverOverlay(g); break;
            case VICTORY:      drawPlaying(g); drawVictoryOverlay(g);  break;
        }

        particles.draw(g);
        drawAchievementPopup(g);

        g.translate(-(int)gs.shakeX, -(int)gs.shakeY);
    }

    private void applyTheme(SettingsSystem.Theme t) {
        switch (t) {
            case DAY:     themeBgColor=new Color(30,35,50);  themeBgNight=new Color(8,8,18);   themeGridColor=new Color(255,255,255,18);  break;
            case NIGHT:   themeBgColor=new Color(10,10,22);  themeBgNight=new Color(4,4,12);   themeGridColor=new Color(0,255,150,30);    nightMode=true; break;
            case CRIMSON: themeBgColor=new Color(40,10,15);  themeBgNight=new Color(18,4,8);   themeGridColor=new Color(220,50,80,25);    break;
            case FOREST:  themeBgColor=new Color(10,35,15);  themeBgNight=new Color(4,14,6);   themeGridColor=new Color(50,220,80,22);    break;
        }
        settings.theme = t;
        settingsThemeIdx = t.ordinal();
        if (t == SettingsSystem.Theme.NIGHT) nightMode = true;
        else nightMode = false;
    }

    private void drawBackground(Graphics2D g) {
        Color bg = blendColor(themeBgColor, themeBgNight, dayNightBlend);
        g.setColor(bg);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        drawGrid(g);
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(themeGridColor);
        for (int x=0; x<=WIDTH;  x+=TILE) g.drawLine(x,0,x,HEIGHT);
        for (int y=0; y<=HEIGHT; y+=TILE) g.drawLine(0,y,WIDTH,y);
    }

    private void drawShopScreen(Graphics2D g) {
        shop.draw(g, WIDTH, HEIGHT, save, tickCount);
    }

    // ── NEW MENU LAYOUT ──────────────────────────────────────────────────
    // Layout (x=285, centered, w=230):
    //   y=200 : Online Mode  (blue)
    //   y=265 : Offline Mode (green)
    //   y=325 : Skin Shop    (gold)
    //   y=380 : Mode Options (dark purple)
    //   y=438 : ◄ Skin Name ► (selector row)

    private void drawMenu(Graphics2D g) {
        float bobY = (float)Math.sin(menuTitleBob) * 8f;
        String title = "SneakySnakes";
        Font titleFont = new Font("Arial", Font.BOLD, 62);
        g.setFont(titleFont);
        FontMetrics fm = g.getFontMetrics();
        int titleX = (WIDTH - fm.stringWidth(title)) / 2;
        int titleY = 145 + (int)bobY;

        for (int r=20; r>0; r-=4) {
            float a = menuTitleGlow * (0.03f + r*0.003f);
            g.setColor(new Color(100,220,255,(int)(a*255)));
            g.drawString(title, titleX - r/2, titleY + r/2);
        }
        g.setFont(titleFont);
        g.setColor(Color.WHITE);
        g.drawString(title, titleX, titleY);

        g.setFont(new Font("Arial", Font.PLAIN, 16));
        g.setColor(new Color(180,220,255,180));
        String sub = "Sneak' em!";
        g.drawString(sub, (WIDTH - g.getFontMetrics().stringWidth(sub))/2, titleY+30);

        // -- Buttons
        drawRoundButton(g, 285, 200, 230, 50, "ONLINE MODE",
            onlineHover ? new Color(80,200,255) : new Color(50,160,220), onlineHover);

        drawRoundButton(g, 285, 263, 230, 50, "OFFLINE MODE",
            offlineHover ? new Color(60,210,110) : new Color(35,160,80), offlineHover);
        g.drawImage(skinShopIcon, 300, 334, 24, 24, null);
        drawRoundButton(g, 285, 326, 230, 44, "SKIN SHOP",
            shopHover ? new Color(200,150,50) : new Color(160,110,30), shopHover);

        drawRoundButton(g, 285, 382, 230, 40, "CONTA", modeOptionsHover ? new Color(80,190,150) : new Color(35,125,95), modeOptionsHover);

        // -- Skin selector row
        g.setFont(new Font("Arial", Font.BOLD, 18));
        g.setColor(new Color(180,220,255));
        String skinStr = "\u25C4  " + SkinSystem.current().displayName + "  \u25BA";
        FontMetrics sfm = g.getFontMetrics();
        int skinStrX = (WIDTH - sfm.stringWidth(skinStr))/2;
        g.drawString(skinStr, skinStrX, 444);
        if(AccountSession.token().isEmpty()){g.setFont(new Font("Arial",Font.PLAIN,12));g.setColor(new Color(170,195,220,190));String hint="Para criar uma conta, clique em Conta.";g.drawString(hint,(WIDTH-g.getFontMetrics().stringWidth(hint))/2,475);}

        drawMenuStats(g);
        drawSkinPreview(g, 640, 195, SkinSystem.current());

        g.setFont(new Font("Arial", Font.PLAIN, 13));
        g.setColor(new Color(140,180,220,160));
        String hint = "WASD/Arrows=Move  N=Day/Night  ESC=Pause";
        g.drawString(hint, (WIDTH - g.getFontMetrics().stringWidth(hint))/2, HEIGHT-18);

        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.setColor(new Color(255,215,0,180));
        g.drawImage(coinIcon,WIDTH-125,HEIGHT-25,16,16,null);
        g.drawString(save.coins+" coins",WIDTH-105,HEIGHT-10);
        g.setColor(new Color(120,160,200,130));
        g.drawString("FPS: " + fps, WIDTH - 70, HEIGHT - 22);

        // Bottom-left: player name + Settings + Edit buttons
        int blY = HEIGHT - 90;
        g.setFont(new Font("Arial", Font.BOLD, 13));
        g.setColor(new Color(180,220,255,210));
        String pname = settings.playerName;
        g.drawString(pname, 12, blY);
        drawIconButton(g, 12,  blY+8, 64, 64, gearIcon,
            settingsHover ? new Color(100,100,100) : new Color(60,60,60), settingsHover);
        drawIconButton(g, 84,  blY+8, 64, 64, pencilIcon,
            editHover ? new Color(180,60,60) : new Color(120,30,30), editHover);
    }

    private void drawModeOptionsPopup(Graphics2D g) {
        g.setColor(new Color(0,0,0,165));g.fillRect(0,0,WIDTH,HEIGHT);
        int pw=360,ph=280,px=moPopupX(),py=moPopupY();
        for(int r=8;r>0;r-=2){g.setColor(new Color(70,120,220,(int)(20f*(r/8f))));g.fillRoundRect(px-r,py-r,pw+r*2,ph+r*2,20,20);}
        g.setColor(new Color(10,16,32,238));g.fillRoundRect(px,py,pw,ph,18,18);g.setColor(new Color(80,150,230,150));g.drawRoundRect(px,py,pw,ph,18,18);
        g.setFont(new Font("Arial",Font.BOLD,23));g.setColor(Color.WHITE);String title="GAME MODES";FontMetrics fm=g.getFontMetrics();
        g.drawString(title,px+(pw-fm.stringWidth(title))/2,py+38);g.setColor(new Color(100,150,220,80));g.drawLine(px+22,py+50,px+pw-22,py+50);
        int bx=moBtnX(),bw=260,bh=44;
        drawRoundButton(g,bx,py+62,bw,bh,"Normal mode",normalModeHover?new Color(80,190,130):new Color(35,135,90),normalModeHover);
        drawRoundButton(g,bx,py+116,bw,bh,"Party mode",partyModeHover?new Color(80,170,255):new Color(45,115,200),partyModeHover);
        drawRoundButton(g,bx,py+170,bw,bh,"Infinite Mode",infiniteModeHover?new Color(80,170,255):new Color(45,115,200),infiniteModeHover);
        g.setFont(new Font("Arial",Font.PLAIN,12));g.setColor(new Color(160,180,210,180));String hint="Press ESC to close";fm=g.getFontMetrics();g.drawString(hint,px+(pw-fm.stringWidth(hint))/2,py+ph-12);
    }

    // ========================================================
    // SETTINGS OVERLAY
    // ========================================================
    private static final String[] THEME_NAMES  = {"Day", "Night", "Crimson", "Forest"};
    private static final Color[]  THEME_COLORS = {
        new Color(80,160,255), new Color(60,60,180),
        new Color(180,30,50),  new Color(30,150,60)
    };

    private void drawSettingsOverlay(Graphics2D g) {
        // dim background
        g.setColor(new Color(0,0,0,170));
        g.fillRect(0,0,WIDTH,HEIGHT);

        int pw=440, ph=340, px=(WIDTH-pw)/2, py=(HEIGHT-ph)/2;
        // glow
        for (int r=10;r>0;r-=2) {
            g.setColor(new Color(80,120,255,(int)(15f*(r/10f))));
            g.fillRoundRect(px-r,py-r,pw+r*2,ph+r*2,22,22);
        }
        g.setColor(new Color(12,14,32,230)); g.fillRoundRect(px,py,pw,ph,18,18);
        g.setColor(new Color(80,120,255,120)); g.drawRoundRect(px,py,pw,ph,18,18);

        // Title
        g.setFont(new Font("Arial",Font.BOLD,28));
        g.setColor(new Color(170,170,180));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("Settings", px+(pw-fm.stringWidth("Settings"))/2, py+40);

        // Divider
        g.setColor(new Color(100,120,220,60)); g.drawLine(px+20,py+52,px+pw-20,py+52);

        // --- Theme row ---
        g.setFont(new Font("Arial",Font.BOLD,15)); g.setColor(new Color(180,200,255));
        g.drawString("Theme:", px+28, py+85);
        SettingsSystem.Theme[] themes = SettingsSystem.Theme.values();
        int tBtnW = 80, tBtnH = 32, tStartX = px+110, tY = py+68;
        for (int i=0;i<themes.length;i++) {
            boolean sel = (settingsThemeIdx == i);
            boolean hov = settingsThemeHover && (i == settingsThemeIdx);
            Color base = sel ? THEME_COLORS[i].brighter() : THEME_COLORS[i].darker();
            g.setColor(sel ? new Color(255,255,255,30) : new Color(0,0,0,60));
            g.fillRoundRect(tStartX+i*(tBtnW+6)+2,tY+2,tBtnW,tBtnH,10,10);
            g.setColor(base); g.fillRoundRect(tStartX+i*(tBtnW+6),tY,tBtnW,tBtnH,10,10);
            if (sel) { g.setColor(new Color(255,255,255,80)); g.drawRoundRect(tStartX+i*(tBtnW+6),tY,tBtnW,tBtnH,10,10); }
            g.setFont(new Font("Arial",Font.BOLD,13)); g.setColor(Color.WHITE);
            fm=g.getFontMetrics();
            g.drawString(THEME_NAMES[i], tStartX+i*(tBtnW+6)+(tBtnW-fm.stringWidth(THEME_NAMES[i]))/2, tY+21);
        }

        // --- Volume row ---
        g.setFont(new Font("Arial",Font.BOLD,15)); g.setColor(new Color(180,200,255));
        g.drawString("Volume:", px+28, py+145);
        int vx = px+110, vy = py+128;
        // minus button
        g.setColor(settingsVolDownHover?new Color(80,100,200):new Color(50,70,160));
        g.fillRoundRect(vx,vy,32,32,8,8);
        g.setFont(new Font("Arial",Font.BOLD,20)); g.setColor(Color.WHITE);
        g.drawString("-",vx+10,vy+22);
        // bar
        int barW = 180, barH = 14;
        int barX = vx+40, barY = vy+9;
        g.setColor(new Color(20,20,50,180)); g.fillRoundRect(barX,barY,barW,barH,7,7);
        g.setColor(new Color(80,180,255));   g.fillRoundRect(barX,barY,(int)(barW*settingsVolume),barH,7,7);
        g.setColor(new Color(120,180,255,100)); g.drawRoundRect(barX,barY,barW,barH,7,7);
        g.setFont(new Font("Arial",Font.BOLD,12)); g.setColor(Color.WHITE);
        g.drawString((int)(settingsVolume*100)+"%", barX+barW+8, barY+11);
        // plus button
        g.setColor(settingsVolUpHover?new Color(80,100,200):new Color(50,70,160));
        g.fillRoundRect(barX+barW+36,vy,32,32,8,8);
        g.setFont(new Font("Arial",Font.BOLD,18)); g.setColor(Color.WHITE);
        g.drawString("+",barX+barW+44,vy+22);

        // hint
        g.setFont(new Font("Arial",Font.PLAIN,12)); g.setColor(new Color(140,160,200,160));
        String cl="Press ESC to cancel"; fm=g.getFontMetrics();
        g.drawString(cl, px+(pw-fm.stringWidth(cl))/2, py+ph-16);

        // Save button
        int sbW=130,sbH=40,sbX=px+(pw-sbW)/2,sbY=py+ph-65;
        drawRoundButton(g,sbX,sbY,sbW,sbH,"Save",
            settingsSaveHover?new Color(60,200,120):new Color(35,150,80), settingsSaveHover);
    }

    // ========================================================
    // EDIT PROFILE OVERLAY
    // ========================================================
    private void drawEditProfileOverlay(Graphics2D g) {
        g.setColor(new Color(0,0,0,170)); g.fillRect(0,0,WIDTH,HEIGHT);

        int pw=420, ph=280, px=(WIDTH-pw)/2, py=(HEIGHT-ph)/2;
        for (int r=10;r>0;r-=2) {
            g.setColor(new Color(40,160,100,(int)(15f*(r/10f))));
            g.fillRoundRect(px-r,py-r,pw+r*2,ph+r*2,22,22);
        }
        g.setColor(new Color(10,16,22,230)); g.fillRoundRect(px,py,pw,ph,18,18);
        g.setColor(new Color(40,160,100,120)); g.drawRoundRect(px,py,pw,ph,18,18);

        // Title
        g.setFont(new Font("Arial",Font.BOLD,26)); g.setColor(new Color(170,170,180));
        FontMetrics fm=g.getFontMetrics();
        g.drawString("Edit", px+(pw-fm.stringWidth("Edit"))/2, py+38);
        g.setColor(new Color(80,160,100,60)); g.drawLine(px+20,py+50,px+pw-20,py+50);

        // Player Name label + field
        g.setFont(new Font("Arial",Font.BOLD,14)); g.setColor(new Color(160,210,255));
        g.drawString("Player Name:", px+28, py+82);
        int fx=px+28, fy=py+90, fw=pw-56, fh=36;
        g.setColor(editNameFocus ? new Color(30,50,90,200) : new Color(18,22,40,200));
        g.fillRoundRect(fx,fy,fw,fh,8,8);
        g.setColor(editNameFocus ? new Color(80,160,255) : new Color(60,90,160,160));
        g.drawRoundRect(fx,fy,fw,fh,8,8);
        g.setFont(new Font("Arial",Font.PLAIN,15)); g.setColor(Color.WHITE);
        String display = editNameBuffer + (editNameFocus && tickCount%30<15 ? "|" : "");
        g.drawString(display, fx+8, fy+24);

        // Change Skin button
        drawRoundButton(g, px+28, py+148, pw-56, 38, "Change Skin (opens Shop)",
            editSkinHover?new Color(160,100,200):new Color(110,60,160), editSkinHover);

        // Save button
        int sbW=130,sbH=40,sbX=px+(pw-sbW)/2,sbY=py+ph-56;
        drawRoundButton(g,sbX,sbY,sbW,sbH,"Save",
            editSaveHover?new Color(60,200,120):new Color(35,150,80), editSaveHover);

        // hint
        g.setFont(new Font("Arial",Font.PLAIN,12)); g.setColor(new Color(140,160,200,160));
        String cl="ESC to cancel"; fm=g.getFontMetrics();
        g.drawString(cl, px+(pw-fm.stringWidth(cl))/2, py+ph-12);
    }

        private void drawMenuStats(Graphics2D g) {
        int px=55, py=200, pw=200, ph=260;
        drawPanel(g, px, py, pw, ph, 0.55f);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.setColor(new Color(180,220,255));
        g.drawString("STATISTICS", px+40, py+24);
        g.setFont(new Font("Arial", Font.PLAIN, 13));
        g.setColor(Color.WHITE);
        String[] labels = {
            "High Score:  " + save.highScore,
            "Games:       " + save.gamesPlayed,
            "Foods Eaten: " + save.foodsEaten,
            "Best Combo:  x" + save.bestCombo,
            "Max Level:   " + save.highestLevel,
            "Deaths:      " + save.deaths,
            "Play Time:   " + formatTime(save.totalPlayTime),
            "Coins:       " + save.coins,
        };
        for (int i=0; i<labels.length; i++) g.drawString(labels[i], px+12, py+50+i*26);
    }

    private void drawSkinPreview(Graphics2D g, int cx, int cy, SkinSystem.Skin skin) {
        drawPanel(g, cx-60, cy-10, 110, 120, 0.45f);
        for (int i=4; i>=0; i--) {
            int sx = cx - 30 + i * (TILE/2);
            SkinSystem.drawSegment(g, skin, sx, cy+25, TILE-2, i==0, tickCount, i);
        }
        drawMiniEyes(g, cx-30, cy+25, TILE-2);
        g.setFont(new Font("Arial", Font.BOLD, 11));
        g.setColor(skin.coinCost > 0 ? new Color(255,215,0) : new Color(100,255,150));
        String info = skin.unlockInfo();
        g.drawString(info, cx - g.getFontMetrics().stringWidth(info)/2, cy+95);
    }

    private void drawMiniEyes(Graphics2D g, int hx, int hy, int size) {
        g.setColor(Color.WHITE);
        g.fillOval(hx+size-6, hy+3,  4, 4);
        g.fillOval(hx+size-6, hy+9,  4, 4);
        g.setColor(Color.BLACK);
        g.fillOval(hx+size-5, hy+4,  2, 2);
        g.fillOval(hx+size-5, hy+10, 2, 2);
    }

    private void drawPlaying(Graphics2D g) {
        drawAllFoods(g);
        drawSnake(g);
        drawHUD(g);
        if (levelUpTimer > 0)    drawLevelUpBanner(g);
        if (comboDisplayTimer>0 && lastDisplayedCombo>1) drawComboBanner(g);
    }

    private void drawAllFoods(Graphics2D g) {
        for (int[] food : foods) {
            GameState.FoodRarity r = GameState.FoodRarity.values()[food[2]];
            drawFood(g, food[0], food[1], r);
        }
    }

    private void drawFood(Graphics2D g, int tx, int ty, GameState.FoodRarity r) {
        float fx = tx * TILE + TILE / 2f;
        float fy = ty * TILE + TILE / 2f;
        float pulse  = gs.foodPulse;
        float radius = TILE / 2f + pulse;

        for (int ring=4; ring>0; ring--) {
            float rr = radius + ring * 4f;
            int   a  = Math.max(0, 30 - ring*5);
            g.setColor(new Color(r.glowColor.getRed(),r.glowColor.getGreen(),r.glowColor.getBlue(),a));
            g.fill(new Ellipse2D.Float(fx-rr, fy-rr, rr*2, rr*2));
        }
        g.setColor(new Color(0,0,0,60));
        g.fill(new Ellipse2D.Float(fx-radius+2, fy-radius+2, radius*2, radius*2));
        g.setColor(r.color);
        g.fill(new Ellipse2D.Float(fx-radius, fy-radius, radius*2, radius*2));
        g.setColor(new Color(255,255,255,130));
        g.fill(new Ellipse2D.Float(fx-radius*0.45f, fy-radius*0.6f, radius*0.5f, radius*0.35f));

        if (r != GameState.FoodRarity.NORMAL) {
            g.setFont(new Font("Arial", Font.BOLD, 9));
            g.setColor(Color.WHITE);
            String lbl = r.name().charAt(0) + r.name().substring(1).toLowerCase();
            FontMetrics fm = g.getFontMetrics();
            g.drawString(lbl, (int)(fx-fm.stringWidth(lbl)/2f), (int)(fy+radius+12));
        }
    }

    private void drawSnake(Graphics2D g) {
        SkinSystem.Skin skin = SkinSystem.current();
        for (int i = gs.length-1; i >= 0; i--) {
            int sx = gs.bodyX[i] * TILE;
            int sy = gs.bodyY[i] * TILE;
            boolean isHead = (i == 0);
            SkinSystem.drawSegment(g, skin, sx, sy, TILE, isHead, tickCount, i);
            if (isHead) drawEyes(g, sx, sy, gs.dirX, gs.dirY);
        }
    }

    private void drawEyes(Graphics2D g, int hx, int hy, int dx, int dy) {
        int ex1,ey1,ex2,ey2, es=4;
        if      (dx==1)  { ex1=hx+13;ey1=hy+4;  ex2=hx+13;ey2=hy+12; }
        else if (dx==-1) { ex1=hx+3; ey1=hy+4;  ex2=hx+3; ey2=hy+12; }
        else if (dy==-1) { ex1=hx+4; ey1=hy+3;  ex2=hx+12;ey2=hy+3;  }
        else             { ex1=hx+4; ey1=hy+13; ex2=hx+12;ey2=hy+13; }
        g.setColor(Color.WHITE);
        g.fillOval(ex1-es/2,ey1-es/2,es,es);
        g.fillOval(ex2-es/2,ey2-es/2,es,es);
        g.setColor(Color.BLACK);
        g.fillOval(ex1-es/4,ey1-es/4,es/2,es/2);
        g.fillOval(ex2-es/4,ey2-es/4,es/2,es/2);
    }

    private void drawHUD(Graphics2D g) {
        drawPanel(g, 8, 8, 185, 190, 0.55f);
        g.setFont(new Font("Arial", Font.BOLD, 13));
        String[] lines = {
            "Score:   " + gs.score,
            "Best:    " + save.highScore,
            "Level:   " + gs.level,
            "Combo:   x" + gs.comboLevel,
            "Coins:   " + save.coins,
            "Foods:   " + gs.foodsEaten,
            "Skin:    " + SkinSystem.current().displayName,
            "FPS:     " + fps,
        };
        for (int i=0; i<lines.length; i++) {
            g.setColor(i<2 ? Color.WHITE : new Color(180,220,255));
            g.drawString(lines[i], 16, 28+i*21);
        }

        g.setFont(new Font("Arial", Font.BOLD, 11));
        g.setColor(new Color(255,220,100));
        g.drawString("Foods on map: " + foods.size() + "/" + targetFoodCount(), 16, 190);

        drawXpBar(g);

        g.setFont(new Font("Arial", Font.PLAIN, 11));
        g.setColor(new Color(140,200,255,160));
        g.drawImage(nightMode?moonIcon:sunIcon,WIDTH-92,5,16,16,null);
        g.drawString(nightMode?"NIGHT":"DAY",WIDTH-72,20);
        g.drawString("Time: " + formatTime(gs.gameTimeSeconds), WIDTH-90, 35);
    }

    private void drawXpBar(Graphics2D g) {
        int need = GameState.xpForNextLevel(gs.level);
        float pct = (need==Integer.MAX_VALUE) ? 1f : (float)gs.xp/need;
        int bx=8, by=HEIGHT-20, bw=185, bh=12;
        g.setColor(new Color(20,20,40,180));
        g.fillRoundRect(bx,by,bw,bh,6,6);
        g.setColor(new Color(80,200,255));
        g.fillRoundRect(bx,by,(int)(bw*pct),bh,6,6);
        g.setColor(new Color(100,180,255,100));
        g.drawRoundRect(bx,by,bw,bh,6,6);
        g.setFont(new Font("Arial", Font.BOLD, 9));
        g.setColor(Color.WHITE);
        g.drawString("XP", bx+4, by+9);
    }

    private void drawLevelUpBanner(Graphics2D g) {
        float t     = levelUpTimer/2.5f;
        float alpha = t>0.8f ? (1f-t)/0.2f : (t<0.2f ? t/0.2f : 1f);
        float scale = 1f+(float)Math.sin(tickCount*0.12)*0.04f;
        int bw=280,bh=70,bx=(WIDTH-bw)/2,by=HEIGHT/2-120;
        for (int r=12;r>0;r-=3) {
            g.setColor(new Color(255,215,0,(int)(alpha*15)));
            g.fillRoundRect(bx-r,by-r,bw+r*2,bh+r*2,20,20);
        }
        g.setColor(new Color(20,20,40,(int)(alpha*200)));
        g.fillRoundRect(bx,by,bw,bh,16,16);
        g.setColor(new Color(255,215,0,(int)(alpha*180)));
        g.drawRoundRect(bx,by,bw,bh,16,16);
        Graphics2D g2 = (Graphics2D)g.create();
        g2.translate(WIDTH/2, by+bh/2);
        g2.scale(scale,scale);
        g2.setFont(new Font("Arial",Font.BOLD,26));
        g2.setColor(new Color(255,215,0,(int)(alpha*255)));
        g.drawImage(levelUpIcon,bx+15,by+18,32,32,null);
        g.drawImage(levelUpIcon,WIDTH/2-120,by+18,32,32,null);
        String lv="LEVEL UP! "+displayedLevel;
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(lv, -fm.stringWidth(lv)/2, 10);
        g2.dispose();
    }

    private void drawComboBanner(Graphics2D g) {
        float alpha = comboDisplayTimer/1.8f;
        int bw=180,bh=48,bx=(WIDTH-bw)/2,by=80;
        g.setColor(new Color(255,120,0,(int)(alpha*180)));
        g.fillRoundRect(bx,by,bw,bh,12,12);
        g.setColor(new Color(255,200,50,(int)(alpha*200)));
        g.drawRoundRect(bx,by,bw,bh,12,12);
        g.setFont(new Font("Arial",Font.BOLD,22));
        g.setColor(new Color(255,240,100,(int)(alpha*255)));
        String ct = "COMBO x" + lastDisplayedCombo + "!";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(ct, bx+(bw-fm.stringWidth(ct))/2, by+30);
    }

    private void drawPauseOverlay(Graphics2D g) {
        g.setColor(new Color(0,0,0,150));
        g.fillRect(0,0,WIDTH,HEIGHT);
        drawPanel(g, WIDTH/2-130, HEIGHT/2-75, 260, 150, 0.85f);
        g.setFont(new Font("Arial",Font.BOLD,40));
        g.setColor(Color.WHITE);
        String p="PAUSED";
        FontMetrics fm=g.getFontMetrics();
        g.drawString(p,(WIDTH-fm.stringWidth(p))/2,HEIGHT/2-10);
        g.setFont(new Font("Arial",Font.PLAIN,16));
        g.setColor(new Color(180,220,255));
        String h="Press ESC to Continue";
        fm=g.getFontMetrics();
        g.drawString(h,(WIDTH-fm.stringWidth(h))/2,HEIGHT/2+30);
    }

    private void drawGameOverOverlay(Graphics2D g) {
        g.setColor(new Color(0,0,0,(int)(overlayAlpha*170)));
        g.fillRect(0,0,WIDTH,HEIGHT);
        if (overlayAlpha<0.4f) return;
        int pw=400,ph=290,px=(WIDTH-pw)/2,py=HEIGHT/2-150;
        drawPanel(g,px,py,pw,ph,0.88f);
        g.setFont(new Font("Arial",Font.BOLD,48));
        g.setColor(new Color(255,80,80));
        String go="GAME OVER";
        FontMetrics fm=g.getFontMetrics();
        g.drawString(go,(WIDTH-fm.stringWidth(go))/2,py+55);
        g.setFont(new Font("Arial",Font.PLAIN,17));
        g.setColor(Color.WHITE);
        String[] lines={
            "Score: "+gs.score+"   Best: "+save.highScore,
            "Max Combo: x"+gs.comboLevel,
            "Survived: "+formatTime(gs.gameTimeSeconds),
            "Coins Earned this game: +0",
            "Total Coins: " + save.coins
        };
        for (int i=0;i<lines.length;i++) {
            fm=g.getFontMetrics();
            g.drawString(lines[i],(WIDTH-fm.stringWidth(lines[i]))/2,py+95+i*26);
        }
        boolean rh=restartHover,mh=menuHover;
        drawRoundButton(g,px+20,     py+ph-65,170,45,"RESTART", rh?new Color(80,200,120):new Color(50,160,90),  rh);
        drawRoundButton(g,px+pw-190, py+ph-65,170,45,"MENU",    mh?new Color(80,140,255):new Color(50,110,200), mh);
    }

    private void drawVictoryOverlay(Graphics2D g) {
        g.setColor(new Color(0,0,0,(int)(overlayAlpha*160)));
        g.fillRect(0,0,WIDTH,HEIGHT);
        if (overlayAlpha<0.3f) return;
        int pw=400,ph=300,px=(WIDTH-pw)/2,py=HEIGHT/2-160;
        drawPanel(g,px,py,pw,ph,0.88f);
        float hue=(tickCount*3%360)/360f;
        g.setFont(new Font("Arial",Font.BOLD,56));
        g.setColor(Color.getHSBColor(hue,0.9f,1.0f));
        String win="YOU WIN!";
        FontMetrics fm=g.getFontMetrics();
        g.drawString(win,(WIDTH-fm.stringWidth(win))/2,py+65);
        g.setFont(new Font("Arial",Font.PLAIN,16));
        g.setColor(Color.WHITE);
        String[] lines={"Final Score: "+gs.score,"Best Score: "+save.highScore,
            "Level: "+gs.level,"Foods: "+gs.foodsEaten,"Time: "+formatTime(gs.gameTimeSeconds)};
        for (int i=0;i<lines.length;i++) {
            fm=g.getFontMetrics();
            g.drawString(lines[i],(WIDTH-fm.stringWidth(lines[i]))/2,py+100+i*24);
        }
        boolean rh=restartHover,mh=menuHover;
        drawRoundButton(g,px+20,     py+ph-60,170,45,"PLAY AGAIN",rh?new Color(80,200,120):new Color(50,160,90), rh);
        drawRoundButton(g,px+pw-190, py+ph-60,170,45,"MENU",      mh?new Color(80,140,255):new Color(50,110,200),mh);
    }

    private void drawAchievementPopup(Graphics2D g) {
        AchievementSystem.Achievement a = AchievementSystem.getActivePopup();
        if (a==null) return;
        float alpha = AchievementSystem.getPopupAlpha();
        if (alpha<=0) return;
        int pw=280,ph=60,px=WIDTH-pw-12,py=HEIGHT-ph-50;
        g.setColor(new Color(20,20,40,(int)(alpha*220)));
        g.fillRoundRect(px,py,pw,ph,12,12);
        g.setColor(new Color(255,215,0,(int)(alpha*200)));
        g.drawRoundRect(px,py,pw,ph,12,12);
        g.setColor(new Color(255,215,0,(int)(alpha*200)));
        g.fillRoundRect(px+6,py+6,48,48,8,8);
        g.setFont(new Font("Arial",Font.BOLD,22));
        g.setColor(Color.WHITE);
        g.drawImage(badgeIcon,px+10,py+10,40,40,null);
        g.setFont(new Font("Arial",Font.BOLD,13));
        g.setColor(new Color(255,215,0,(int)(alpha*255)));
        g.drawString("Achievement Unlocked!",px+62,py+20);
        g.setFont(new Font("Arial",Font.PLAIN,12));
        g.setColor(new Color(255,255,255,(int)(alpha*220)));
        g.drawString(a.name+": "+a.description,px+62,py+37);
    }

    private void drawPanel(Graphics2D g, int x, int y, int w, int h, float alpha) {
        g.setColor(new Color(10,14,30,(int)(alpha*200)));
        g.fillRoundRect(x,y,w,h,14,14);
        g.setColor(new Color(100,160,255,50));
        g.drawRoundRect(x,y,w,h,14,14);
    }

    private void drawRoundButton(Graphics2D g, int x, int y, int w, int h,
                                  String label, Color base, boolean hovered) {
        g.setColor(new Color(0,0,0,80));
        g.fillRoundRect(x+3,y+3,w,h,14,14);
        g.setColor(hovered ? base.brighter() : base);
        g.fillRoundRect(x,y,w,h,14,14);
        g.setColor(new Color(255,255,255,hovered?50:25));
        g.fillRoundRect(x,y,w,h/2,14,14);
        g.setColor(new Color(255,255,255,80));
        g.drawRoundRect(x,y,w,h,14,14);
        g.setFont(new Font("Arial",Font.BOLD,18));
        g.setColor(Color.WHITE);
        FontMetrics fm=g.getFontMetrics();
        g.drawString(label, x+(w-fm.stringWidth(label))/2, y+h/2+7);
    }

    private void drawIconButton(Graphics2D g, int x, int y, int w, int h,
                                java.awt.image.BufferedImage icon, Color base, boolean hovered) {
        g.setColor(new Color(0,0,0,80));
        g.fillRoundRect(x+3,y+3,w,h,14,14);
        g.setColor(hovered ? base.brighter() : base);
        g.fillRoundRect(x,y,w,h,14,14);
        g.setColor(new Color(255,255,255,hovered?50:25));
        g.fillRoundRect(x,y,w,h/2,14,14);
        g.setColor(new Color(255,255,255,80));
        g.drawRoundRect(x,y,w,h,14,14);
        if (icon != null) {
            int iconSize = Math.min(w, h) - 12;
            int ix = x + (w - iconSize) / 2;
            int iy = y + (h - iconSize) / 2;
            g.drawImage(icon, ix, iy, iconSize, iconSize, null);
        }
    }

    private Color blendColor(Color a, Color b, float t) {
        t=Math.max(0,Math.min(1,t));
        return new Color(
            (int)(a.getRed()  +(b.getRed()  -a.getRed())  *t),
            (int)(a.getGreen()+(b.getGreen()-a.getGreen())*t),
            (int)(a.getBlue() +(b.getBlue() -a.getBlue()) *t));
    }

    private String formatTime(long s) { return String.format("%d:%02d",s/60,s%60); }

    // ==========================================================
    // MOUSE SCALE HELPER
    // ==========================================================

    private int[] scaleMouseCoords(int mx, int my) {
        double scaleX = getWidth() / (double) WIDTH;
        double scaleY = getHeight() / (double) HEIGHT;
        if (scaleX <= 0 || scaleY <= 0) return new int[]{-1, -1};
        return new int[]{(int)(mx / scaleX), (int)(my / scaleY)};
    }

    // ==========================================================
    // BUTTON BOUNDS HELPERS
    // ==========================================================

    // Mode Options popup internals
    private int moPopupX() { return (WIDTH-360)/2; }
    private int moPopupY() { return (HEIGHT-280)/2; }
    private int moBtnX()   { return moPopupX() + 50; }

    // ==========================================================
    // INPUT
    // ==========================================================

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_N) { nightMode = !nightMode; return; }

        switch (screen) {
            case MENU:
                if (key==KeyEvent.VK_ENTER||key==KeyEvent.VK_SPACE) { selectedGameMode="NORMAL"; startNewGame(); } // Normal mode shortcut
                if (key==KeyEvent.VK_LEFT ||key==KeyEvent.VK_A)     SkinSystem.cyclePrev();
                if (key==KeyEvent.VK_RIGHT||key==KeyEvent.VK_D)     SkinSystem.cycleNext();
                if (key==KeyEvent.VK_S)  { screen=Screen.SHOP; SoundSystem.playClick(); }
                break;
            case GAME_MODES:
                if (key==KeyEvent.VK_ESCAPE) { screen=Screen.MENU; SoundSystem.playClick(); }
                break;
            case SETTINGS:
                if (key==KeyEvent.VK_ESCAPE) { screen=Screen.MENU; SoundSystem.playClick(); }
                break;
            case EDIT_PROFILE:
                if (key==KeyEvent.VK_ESCAPE) { screen=Screen.MENU; editNameFocus=false; SoundSystem.playClick(); }
                if (editNameFocus) handleEditNameKey(key, e.getKeyChar());
                break;
            case SHOP:
                if (key==KeyEvent.VK_ESCAPE) { screen=Screen.MENU; SoundSystem.playClick(); commitSave(); }
                break;
            case PLAYING:
                if (key==KeyEvent.VK_ESCAPE) { screen=Screen.PAUSED; break; }
                applyDirection(key);
                break;
            case PAUSED:
                if (key==KeyEvent.VK_ESCAPE) screen=Screen.PLAYING;
                break;
            case GAME_OVER: case VICTORY:
                if (key==KeyEvent.VK_R||key==KeyEvent.VK_ENTER) startNewGame();
                if (key==KeyEvent.VK_M||key==KeyEvent.VK_ESCAPE) goToMenu();
                break;
        }
    }

    private void handleEditNameKey(int key, char ch) {
        if (key == java.awt.event.KeyEvent.VK_BACK_SPACE) {
            if (!editNameBuffer.isEmpty()) editNameBuffer = editNameBuffer.substring(0, editNameBuffer.length()-1);
        } else if (key == java.awt.event.KeyEvent.VK_ENTER) {
            editNameFocus = false;
        } else if (ch >= 32 && ch < 127 && editNameBuffer.length() < 24) {
            editNameBuffer += ch;
        }
    }

    private void applyDirection(int k) {
        if ((k==KeyEvent.VK_W||k==KeyEvent.VK_UP)    &&gs.dirY!=1)  {gs.nextDirX=0; gs.nextDirY=-1;}
        if ((k==KeyEvent.VK_S||k==KeyEvent.VK_DOWN)  &&gs.dirY!=-1) {gs.nextDirX=0; gs.nextDirY=1; }
        if ((k==KeyEvent.VK_A||k==KeyEvent.VK_LEFT)  &&gs.dirX!=1)  {gs.nextDirX=-1;gs.nextDirY=0; }
        if ((k==KeyEvent.VK_D||k==KeyEvent.VK_RIGHT) &&gs.dirX!=-1) {gs.nextDirX=1; gs.nextDirY=0; }
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    @Override
    public void mouseClicked(MouseEvent e) {
        int[] scaled = scaleMouseCoords(e.getX(), e.getY());
        int mx = scaled[0], my = scaled[1];

        switch (screen) {
            case MENU:
                // Online Mode
                if (inRect(mx,my,285,200,230,50)) {
                    SoundSystem.playClick();
                    launchOnlineMode();
                }
                // Offline Mode
                if (inRect(mx,my,285,263,230,50)) {
                    SoundSystem.playClick(); screen=Screen.GAME_MODES;
                }
                // Skin Shop
                if (inRect(mx,my,285,326,230,44)) {
                    SoundSystem.playClick();
                    screen=Screen.SHOP;
                }
                // Mode Options
                if (inRect(mx,my,285,382,230,40)) {
                    SoundSystem.playClick(); openAccountManager();
                }
                // Skin arrows
                if (inRect(mx,my,190,430,80,30))  { SoundSystem.playClick(); SkinSystem.cyclePrev(); }
                if (inRect(mx,my,530,430,80,30))  { SoundSystem.playClick(); SkinSystem.cycleNext(); }
                // Bottom buttons
                int blY2 = HEIGHT-90;
                if (inRect(mx,my,12, blY2+8, 64, 64)) { SoundSystem.playClick(); screen=Screen.SETTINGS; }
                if (inRect(mx,my,84, blY2+8, 64, 64)) { SoundSystem.playClick(); editNameBuffer=settings.playerName; editNameFocus=false; screen=Screen.EDIT_PROFILE; }
                break;
            case GAME_MODES:
                if(inRect(mx,my,moBtnX(),moPopupY()+62,260,44)){selectedGameMode="NORMAL";SoundSystem.playClick();startNewGame();}
                else if(inRect(mx,my,moBtnX(),moPopupY()+116,260,44)){selectedGameMode="PARTY";SoundSystem.playClick();startNewGame();}
                else if(inRect(mx,my,moBtnX(),moPopupY()+170,260,44)){selectedGameMode="INFINITE";SoundSystem.playClick();startNewGame();}
                else if(!inRect(mx,my,moPopupX(),moPopupY(),360,280))screen=Screen.MENU;
                break;
            case SHOP:
                boolean bought = shop.handleClick(mx, my, save);
                if (bought) { commitSave(); particles.spawnAchievement(mx, my); }
                break;
            case SETTINGS: {
                int pw=440,ph=340,px=(WIDTH-pw)/2,py=(HEIGHT-ph)/2;
                // Theme buttons
                SettingsSystem.Theme[] themes=SettingsSystem.Theme.values();
                int tBtnW=80,tStartX=px+110,tY=py+68;
                for(int i=0;i<themes.length;i++)
                    if(inRect(mx,my,tStartX+i*(tBtnW+6),tY,tBtnW,32)){settingsThemeIdx=i;applyTheme(themes[i]);SoundSystem.playClick();}
                // Volume -
                if(inRect(mx,my,px+110,py+128,32,32)){settingsVolume=Math.max(0f,settingsVolume-0.1f);SoundSystem.setVolume(settingsVolume);SoundSystem.playClick();}
                // Volume +
                int barX=px+110+40,barY=py+128;
                if(inRect(mx,my,barX+180+36,barY,32,32)){settingsVolume=Math.min(1f,settingsVolume+0.1f);SoundSystem.setVolume(settingsVolume);SoundSystem.playClick();}
                // Save
                int sbW=130,sbH=40,sbX=px+(pw-sbW)/2,sbY=py+ph-65;
                if(inRect(mx,my,sbX,sbY,sbW,sbH)){settings.theme=themes[settingsThemeIdx];settings.volume=settingsVolume;settings.save();SoundSystem.playClick();screen=Screen.MENU;}
                // ESC area = outside
                if(!inRect(mx,my,px,py,pw,ph)){screen=Screen.MENU;}
                break;
            }
            case EDIT_PROFILE: {
                int pw=420,ph=280,px=(WIDTH-pw)/2,py=(HEIGHT-ph)/2;
                // Name field click
                if(inRect(mx,my,px+28,py+90,pw-56,36)){editNameFocus=true;SoundSystem.playClick();}
                else editNameFocus=false;
                // Change Skin
                if(inRect(mx,my,px+28,py+148,pw-56,38)){SoundSystem.playClick();screen=Screen.SHOP;}
                // Save
                int sbW=130,sbH=40,sbX=px+(pw-sbW)/2,sbY=py+ph-56;
                if(inRect(mx,my,sbX,sbY,sbW,sbH)){
                    if(!editNameBuffer.trim().isEmpty()) settings.playerName=editNameBuffer.trim();
                    settings.save(); SoundSystem.playClick(); screen=Screen.MENU; editNameFocus=false;
                }
                if(!inRect(mx,my,px,py,pw,ph)){screen=Screen.MENU;editNameFocus=false;}
                break;
            }
            case GAME_OVER: case VICTORY: {
                int pw=400;
                int ph=(screen==Screen.VICTORY)?300:290;
                int px=(WIDTH-pw)/2, py=HEIGHT/2-(screen==Screen.VICTORY?160:150);
                if (inRect(mx,my,px+20,     py+ph-65,170,45)) { SoundSystem.playClick(); startNewGame(); }
                if (inRect(mx,my,px+pw-190, py+ph-65,170,45)) { SoundSystem.playClick(); goToMenu(); }
                break;
            }
        }
    }

    private void openAccountManager() {
        JFrame frame=(JFrame)SwingUtilities.getWindowAncestor(this);if(frame==null)return;
        ChooseServerDialog dialog=new ChooseServerDialog(frame);dialog.setVisible(true);String url=dialog.getSelectedUrl();
        if(url==null||url.trim().isEmpty())return;AccountSession.manageAccount(frame,url.trim());requestFocusInWindow();
    }

    private void launchOnlineMode() {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (frame == null) return;

        // Use the player name from the profile — no prompt needed
        String name = settings.playerName;
        if (name == null || name.trim().isEmpty()) name = System.getProperty("user.name", "Player");

        // Show the Choose a Server screen
        ChooseServerDialog dialog = new ChooseServerDialog(frame);
        dialog.setVisible(true);
        String serverUrl = dialog.getSelectedUrl();
        if (serverUrl == null || serverUrl.trim().isEmpty()) return;

        final String finalUrl = serverUrl.trim();
        if (!AccountSession.ensureLogin(frame, finalUrl)) return;
        SoundSystem.stopMusic();
        final String finalName = name;
        OnlineGamePanel ogp = new OnlineGamePanel(finalName, finalUrl, () -> {
            SwingUtilities.invokeLater(() -> {
                frame.getContentPane().removeAll();
                frame.getContentPane().add(this);
                frame.pack();
                frame.revalidate();
                requestFocusInWindow();
                SoundSystem.startMenuMusic();
                screen = Screen.MENU;
            });
        });

        frame.getContentPane().removeAll();
        frame.getContentPane().add(ogp);
        frame.pack();
        frame.revalidate();
        ogp.requestFocusInWindow();
        ogp.connect();
    }

    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e)  {
        onlineHover=offlineHover=shopHover=modeOptionsHover=false;normalModeHover=partyModeHover=infiniteModeHover=false;
        restartHover=menuHover=false;
        partyModeHover=infiniteModeHover=closeOptionsHover=false;
        skinLeftHover=skinRightHover=false;
        settingsHover=editHover=false;
        settingsSaveHover=settingsVolDownHover=settingsVolUpHover=false;
        editSkinHover=editSaveHover=false;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        int[] scaled = scaleMouseCoords(e.getX(), e.getY());
        int mx = scaled[0], my = scaled[1];

        onlineHover      = inRect(mx,my,285,200,230,50);
        offlineHover     = inRect(mx,my,285,263,230,50);
        shopHover        = inRect(mx,my,285,326,230,44);
        modeOptionsHover = inRect(mx,my,285,382,230,40);
        normalModeHover=screen==Screen.GAME_MODES&&inRect(mx,my,moBtnX(),moPopupY()+62,260,44);
        partyModeHover=screen==Screen.GAME_MODES&&inRect(mx,my,moBtnX(),moPopupY()+116,260,44);
        infiniteModeHover=screen==Screen.GAME_MODES&&inRect(mx,my,moBtnX(),moPopupY()+170,260,44);
        skinLeftHover    = inRect(mx,my,190,430,80,30);
        skinRightHover   = inRect(mx,my,530,430,80,30);
        int blYh = HEIGHT-90;
        settingsHover = screen==Screen.MENU && inRect(mx,my,12, blYh+8, 64, 64);
        editHover     = screen==Screen.MENU && inRect(mx,my,84, blYh+8, 64, 64);
        // Settings overlay hovers
        if (screen==Screen.SETTINGS) {
            int pw=440,ph=340,spx=(WIDTH-pw)/2,spy=(HEIGHT-ph)/2;
            int sbW=130,sbH=40,sbX=spx+(pw-sbW)/2,sbY=spy+ph-65;
            settingsSaveHover  = inRect(mx,my,sbX,sbY,sbW,sbH);
            settingsVolDownHover = inRect(mx,my,spx+110,spy+128,32,32);
            int bx2=spx+110+40+180+36;
            settingsVolUpHover   = inRect(mx,my,bx2,spy+128,32,32);
        } else { settingsSaveHover=settingsVolDownHover=settingsVolUpHover=false; }
        // Edit overlay hovers
        if (screen==Screen.EDIT_PROFILE) {
            int pw=420,ph=280,epx=(WIDTH-pw)/2,epy=(HEIGHT-ph)/2;
            editSkinHover = inRect(mx,my,epx+28,epy+148,pw-56,38);
            int sbW=130,sbH=40,sbX=epx+(pw-sbW)/2,sbY=epy+ph-56;
            editSaveHover = inRect(mx,my,sbX,sbY,sbW,sbH);
        } else { editSkinHover=editSaveHover=false; }

        restartHover = false; menuHover=false;
        if (screen==Screen.GAME_OVER||screen==Screen.VICTORY) {
            int pw=400;
            int ph=(screen==Screen.VICTORY)?300:290;
            int px=(WIDTH-pw)/2, py=HEIGHT/2-(screen==Screen.VICTORY?160:150);
            restartHover = inRect(mx,my,px+20,     py+ph-65,170,45);
            menuHover    = inRect(mx,my,px+pw-190, py+ph-65,170,45);
        }

        partyModeHover    = screen==Screen.GAME_MODES && inRect(mx,my, moBtnX(), moPopupY()+68,  240, 48);
        infiniteModeHover = screen==Screen.GAME_MODES && inRect(mx,my, moBtnX(), moPopupY()+130, 240, 48);

        if (screen==Screen.SHOP) shop.handleMouseMove(mx,my);
        repaint();
    }

    @Override public void mouseDragged(MouseEvent e) {}

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (screen==Screen.SHOP) shop.scroll((int)e.getWheelRotation(), HEIGHT);
    }

    private boolean inRect(int mx,int my,int x,int y,int w,int h) {
        return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;
    }

    // =========================================================================
    // CHOOSE A SERVER DIALOG
    // =========================================================================
    private class ChooseServerDialog extends JDialog {
        private static final String VERCEL_API_URL =
            "https://sneakysnakes-server-list.vercel.app/api";

        private String selectedUrl = null;
        private final JPanel center;
        private boolean loading = false;

        public ChooseServerDialog(Frame owner) {
            super(owner, "Select Server", true);
            setSize(420, 480);
            setLocationRelativeTo(owner);
            setResizable(false);
            setUndecorated(true);

            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBackground(new Color(20, 24, 38));
            mainPanel.setBorder(
                BorderFactory.createLineBorder(new Color(60, 90, 160), 2)
            );

            JLabel titleLabel = new JLabel(
                "ONLINE SERVERS", SwingConstants.CENTER
            );
            titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
            titleLabel.setForeground(new Color(100, 180, 255));
            titleLabel.setBorder(
                BorderFactory.createEmptyBorder(16, 0, 10, 0)
            );
            mainPanel.add(titleLabel, BorderLayout.NORTH);

            center = new JPanel();
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
            center.setBackground(new Color(20, 24, 38));
            center.setBorder(
                BorderFactory.createEmptyBorder(10, 24, 20, 24)
            );
            mainPanel.add(center, BorderLayout.CENTER);
            setContentPane(mainPanel);

            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowOpened(java.awt.event.WindowEvent e) {
                    refreshServerMenu();
                }
            });
        }

        // Busca os servidores em segundo plano para não congelar a interface Swing.
        private void refreshServerMenu() {
            if (loading) return;
            loading = true;
            center.removeAll();
            addCenteredLabel("Loading servers...", new Color(140, 160, 190));
            center.revalidate();
            center.repaint();

            new SwingWorker<java.util.List<String>, Void>() {
                @Override
                protected java.util.List<String> doInBackground() {
                    return fetchServers();
                }

                @Override
                protected void done() {
                    loading = false;
                    if (!isDisplayable()) return;

                    java.util.List<String> servers;
                    try {
                        servers = get();
                    } catch (Exception e) {
                        System.err.println(
                            "[GamePanel] Não foi possível concluir a busca: " +
                            e.getMessage()
                        );
                        servers = new java.util.ArrayList<>();
                    }

                    showServerMenu(servers);
                }
            }.execute();
        }

        private void addCenteredLabel(String message, Color color) {
            JLabel label = new JLabel(message, SwingConstants.CENTER);
            label.setFont(new Font("Arial", Font.PLAIN, 14));
            label.setForeground(color);
            label.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(Box.createVerticalGlue());
            center.add(label);
            center.add(Box.createVerticalGlue());
        }

        private void showServerMenu(java.util.List<String> servers) {
            center.removeAll();

            if (servers == null || servers.isEmpty()) {
                addCenteredLabel(
                    "<html><div style='text-align:center;color:#888888;'>" +
                    "There's currently no servers running!<br>" +
                    "Ask a friend to host a server or host your own,<br>" +
                    "to play with friends!</div></html>",
                    new Color(140, 140, 140)
                );
            } else {
                JButton randomBtn = new JButton("Join a random server");
                randomBtn.setFont(new Font("Arial", Font.BOLD, 16));
                randomBtn.setForeground(Color.WHITE);
                randomBtn.setBackground(new Color(50, 140, 220));
                randomBtn.setFocusPainted(false);
                randomBtn.setBorderPainted(false);
                randomBtn.setOpaque(true);
                randomBtn.setPreferredSize(new Dimension(300, 44));
                randomBtn.setMinimumSize(new Dimension(300, 44));
                randomBtn.setMaximumSize(new Dimension(300, 44));
                randomBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
                randomBtn.setCursor(
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                );

                randomBtn.addActionListener(ev -> {
                    selectedUrl = servers.get(
                        (int) (Math.random() * servers.size())
                    );
                    dispose();
                });

                randomBtn.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        randomBtn.setBackground(new Color(80, 180, 255));
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        randomBtn.setBackground(new Color(50, 140, 220));
                    }
                });

                center.add(Box.createVerticalStrut(10));
                center.add(randomBtn);
                center.add(Box.createVerticalStrut(16));
            }

            JLabel orLabel = new JLabel(
                "─────  or enter a desired server  ─────",
                SwingConstants.CENTER
            );
            orLabel.setFont(new Font("Arial", Font.PLAIN, 12));
            orLabel.setForeground(new Color(100, 120, 160));
            orLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(orLabel);
            center.add(Box.createVerticalStrut(8));

            JTextField searchBar = new JTextField("wss://");
            searchBar.setFont(new Font("Arial", Font.PLAIN, 14));
            searchBar.setForeground(new Color(160, 190, 220));
            searchBar.setBackground(new Color(30, 36, 54));
            searchBar.setCaretColor(Color.WHITE);
            searchBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                    new Color(60, 90, 160), 1, true
                ),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
            ));
            searchBar.setPreferredSize(new Dimension(300, 38));
            searchBar.setMaximumSize(new Dimension(300, 38));
            searchBar.setAlignmentX(Component.CENTER_ALIGNMENT);
            searchBar.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    if (searchBar.getText().equals("wss://")) {
                        searchBar.setText("wss://");
                    }
                    searchBar.setForeground(Color.WHITE);
                }
            });
            center.add(searchBar);
            center.add(Box.createVerticalStrut(10));

            JButton connectBtn = new JButton("Connect");
            connectBtn.setFont(new Font("Arial", Font.BOLD, 14));
            connectBtn.setForeground(Color.WHITE);
            connectBtn.setBackground(new Color(35, 150, 80));
            connectBtn.setFocusPainted(false);
            connectBtn.setBorderPainted(false);
            connectBtn.setOpaque(true);
            connectBtn.setPreferredSize(new Dimension(300, 40));
            connectBtn.setMinimumSize(new Dimension(300, 40));
            connectBtn.setMaximumSize(new Dimension(300, 40));
            connectBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            connectBtn.setCursor(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            );
            connectBtn.addActionListener(ev -> {
                String address = searchBar.getText().trim();
                if (address.startsWith("ws://") ||
                    address.startsWith("wss://")) {
                    if (address.length() > 5) {
                        selectedUrl = address;
                        dispose();
                        return;
                    }
                }
                searchBar.setForeground(new Color(255, 80, 80));
                searchBar.setText("ws://host:port");
            });
            center.add(connectBtn);
            center.add(Box.createVerticalStrut(8));

            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.setFont(new Font("Arial", Font.PLAIN, 12));
            cancelBtn.setForeground(new Color(150, 170, 200));
            cancelBtn.setBackground(new Color(30, 36, 54));
            cancelBtn.setFocusPainted(false);
            cancelBtn.setBorderPainted(false);
            cancelBtn.setOpaque(true);
            cancelBtn.setPreferredSize(new Dimension(300, 34));
            cancelBtn.setMinimumSize(new Dimension(300, 34));
            cancelBtn.setMaximumSize(new Dimension(300, 34));
            cancelBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            cancelBtn.addActionListener(ev -> dispose());
            center.add(cancelBtn);

            center.revalidate();
            center.repaint();
        }

        // A API retorna { success: true, servers: [{ host, port, ... }] }.
        // Convertemos cada objeto em ws://host:port para o OnlineGamePanel.
        private java.util.List<String> fetchServers() {
            java.util.List<String> addresses = new java.util.ArrayList<>();
            java.net.HttpURLConnection connection = null;

            try {
                java.net.URL url = new java.net.URL(
                    VERCEL_API_URL + "/servers"
                );
                connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(20000);
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty(
                    "User-Agent", "SneakySnakes-Game-Client"
                );

                int status = connection.getResponseCode();
                if (status != java.net.HttpURLConnection.HTTP_OK) {
                    System.err.println(
                        "[GamePanel] API Vercel retornou HTTP " + status
                    );
                    return addresses;
                }

                StringBuilder response = new StringBuilder();
                try (java.io.BufferedReader reader =
                    new java.io.BufferedReader(
                        new java.io.InputStreamReader(
                            connection.getInputStream(),
                            java.nio.charset.StandardCharsets.UTF_8
                        )
                    )) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                String json = response.toString();
                java.util.regex.Pattern objectPattern =
                    java.util.regex.Pattern.compile("\\{([^{}]*)\\}");
                java.util.regex.Matcher objectMatcher =
                    objectPattern.matcher(json);

                while (objectMatcher.find()) {
                    String object = objectMatcher.group(1);

                    java.util.regex.Matcher hostMatcher =
                        java.util.regex.Pattern.compile(
                            "\\\"host\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
                        ).matcher(object);
                    java.util.regex.Matcher portMatcher =
                        java.util.regex.Pattern.compile(
                            "\\\"port\\\"\\s*:\\s*(\\d+)"
                        ).matcher(object);

                    if (!hostMatcher.find() || !portMatcher.find()) {
                        continue;
                    }

                    String host = hostMatcher.group(1).trim();
                    int port;
                    try {
                        port = Integer.parseInt(portMatcher.group(1));
                    } catch (NumberFormatException invalidPort) {
                        continue;
                    }

                    if (host.isEmpty() || host.contains("/") ||
                        host.contains(":") || port < 1 || port > 65535) {
                        continue;
                    }

                    java.util.regex.Matcher secureMatcher =
                        java.util.regex.Pattern.compile("\"secure\"\\s*:\\s*true", java.util.regex.Pattern.CASE_INSENSITIVE)
                            .matcher(object);
                    boolean secure = secureMatcher.find() || port == 443;
                    String address = (secure ? "wss://" : "ws://") + host + ":" + port;
                    if (!addresses.contains(address)) {
                        addresses.add(address);
                    }
                }

                System.out.println(
                    "[GamePanel] Servidores encontrados: " + addresses.size()
                );

            } catch (java.net.SocketTimeoutException timeout) {
                System.err.println(
                    "[GamePanel] Tempo esgotado ao consultar a API Vercel."
                );
            } catch (Exception e) {
                System.err.println(
                    "[GamePanel] Erro ao buscar servidores: " + e.getMessage()
                );
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            return addresses;
        }

        public String getSelectedUrl() {
            return selectedUrl;
        }
    } // Fim do ChooseServerDialog

}
