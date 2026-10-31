package com.sneakysnakes;

import android.content.Context;
import android.app.Activity;
import android.graphics.BitmapFactory;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public class NativeGameView extends View {
    static final int TILE = 16;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    NativeSocket net;
    int world=64, id=-1, dx=1, dy=0, nx=1, ny=0, score=0, coinsN=0, applesN=0, xp=0, level=1, fps=0, frameCount=0;
    boolean connected=false, alive=true, sprint=false, offline=false, doublePoints=false;
    String mode="ONLINE", partyEvent="", status="Conectando...";
    int partyMoves=0, partyEventTicks=0;
    long last=0, startedAt=System.currentTimeMillis(), fpsAt=System.currentTimeMillis(), levelUpUntil=0;
    final ArrayList<int[]> snake=new ArrayList<>();
    final List<int[]> foods=new CopyOnWriteArrayList<>(), coins=new CopyOnWriteArrayList<>(), apples=new CopyOnWriteArrayList<>();
    final Map<Integer,Player> others=new ConcurrentHashMap<>();
    int steeringPointer=-1, sprintPointer=-1;
    boolean respawnPressed=false;
    float steeringX, steeringY;
    Bitmap appleBitmap, coinBitmap, snakeTexture;
    String skinId="bright_blue", joinName="Player";
    boolean authPending=false;
    boolean showSnakeFace=true, detailedGraphics=true;

    static class Player { String name; boolean alive; int[][] body; }

    NativeGameView(Context c,String name,String address) { this(c,name,address,false,"ONLINE"); }

    NativeGameView(Context c,String name,String address,boolean local,String selectedMode) {
        super(c);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        try {
            appleBitmap=BitmapFactory.decodeStream(c.getAssets().open("icons/applemin.png"));
            coinBitmap=BitmapFactory.decodeStream(c.getAssets().open("icons/coin.png"));
        } catch(Exception ignored) {}
        if(c instanceof Activity) {
            android.content.SharedPreferences prefs=((Activity)c).getPreferences(Context.MODE_PRIVATE);
            showSnakeFace=prefs.getBoolean("snake_face",true);
            detailedGraphics=prefs.getBoolean("detailed_graphics",true);
            skinId=prefs.getString("server_equipped_skin",prefs.getString("equipped_skin","bright_blue"));
            String textureName=textureForSkin(skinId);
            if(textureName!=null) {
                try { snakeTexture=BitmapFactory.decodeStream(c.getAssets().open("textures/"+textureName)); }
                catch(Exception ignored) {}
            }
        }
        offline=local; mode=selectedMode;
        if (offline) {
            connected=true; status=mode;
            int x=world/2,y=world/2;
            for(int i=0;i<4;i++) snake.add(new int[]{x-i,y});
            for(int i=0;i<5;i++) spawnLocalFood();
            for(int i=0;i<3;i++) spawnLocalCoin();
            for(int i=0;i<2;i++) spawnLocalApple();
            NativeAudio.randomTrack();
        } else {
            joinName=name;
            net=new NativeSocket(address,this::message);
            net.start();
            String token="";
            if(c instanceof Activity) token=((Activity)c).getPreferences(Context.MODE_PRIVATE).getString("account_token","");
            if(!token.isEmpty()) {
                authPending=true;
                try { JSONObject auth=new JSONObject();auth.put("type","auth_resume");auth.put("token",token);net.send(auth.toString()); }
                catch(Exception ignored) { authPending=false;sendJoin(); }
            } else sendJoin();
        }
    }

    static String esc(String s) { return s.replace("\\","\\\\").replace("\"","\\\""); }
    void sendJoin() { send("{\"type\":\"join\",\"name\":\""+esc(joinName)+"\",\"skin\":\""+esc(skinId)+"\"}"); }
    void send(String s) { if(net!=null) net.send(s); }

    void message(String s) {
        post(() -> {
            try {
                JSONObject o=new JSONObject(s);
                String type=o.optString("type");
                if(type.equals("welcome")) {
                    id=o.optInt("id"); world=Math.max(32,o.optInt("worldTiles",64));
                    int x=world/2,y=world/2; snake.clear();
                    for(int i=0;i<4;i++) snake.add(new int[]{x-i,y});
                    connected=!authPending; status=authPending?"Autenticando conta...":"Conectado";
                    if(connected) NativeAudio.randomTrack();
                } else if(type.equals("auth_resumed")) {
                    JSONObject profile=o.optJSONObject("profile");
                    if(profile!=null)saveServerProfile(profile);
                    authPending=false;sendJoin();connected=true;status="Conectado";NativeAudio.randomTrack();
                } else if(type.equals("auth_error")) {
                    if(authPending) {
                        authPending=false;
                        if(getContext() instanceof Activity)((Activity)getContext()).getPreferences(Context.MODE_PRIVATE).edit().remove("account_token").apply();
                        sendJoin();connected=true;status="Conectado como visitante";
                    }
                } else if(type.equals("state")) {
                    JSONArray a=o.optJSONArray("players");
                    if(a!=null) {
                        Map<Integer,Player> fresh=new HashMap<>();
                        for(int i=0;i<a.length();i++) {
                            JSONObject q=a.optJSONObject(i);
                            if(q==null||q.optInt("id")==id) continue;
                            Player z=new Player(); z.name=q.optString("name"); z.alive=q.optBoolean("alive");
                            JSONArray b=q.optJSONArray("body");
                            z.body=new int[b==null?0:b.length()][2];
                            if(b!=null) for(int j=0;j<b.length();j++) {
                                JSONArray pt=b.optJSONArray(j);
                                if(pt!=null&&pt.length()>1) { z.body[j][0]=pt.optInt(0); z.body[j][1]=pt.optInt(1); }
                            }
                            fresh.put(q.optInt("id"),z);
                        }
                        others.clear(); others.putAll(fresh);
                    }
                } else if(type.equals("foods")) items(o.optJSONArray("foods"),foods,true);
                else if(type.equals("profile")) {
                    JSONObject profile=o.optJSONObject("profile");
                    if(profile!=null)saveServerProfile(profile);
                } else if(type.equals("coin_result")) {
                    if(o.optBoolean("accepted") && !o.optBoolean("guest") && getContext() instanceof Activity) {
                        Activity activity=(Activity)getContext();
                        android.content.SharedPreferences prefs=activity.getPreferences(Context.MODE_PRIVATE);
                        prefs.edit().putInt("server_coins",Math.max(0,o.optInt("balance",0))).apply();
                        JSONObject profile=o.optJSONObject("profile");
                        if(profile!=null)saveServerProfile(profile);
                    }
                } else if(type.equals("server_shutdown")) {
                    status="Servidor reiniciando...";
                } else if(type.equals("coin_spawn")) items(o.optJSONArray("coins"),coins,false);
                else if(type.equals("apple_spawn")) items(o.optJSONArray("apples"),apples,false);
                else if(type.equals("respawned")) {
                    int x=o.optInt("x",world/2), y=o.optInt("y",world/2);
                    snake.clear();
                    for(int i=0;i<4;i++) snake.add(new int[]{x-i,y});
                    dx=1; dy=0; nx=1; ny=0; score=0; alive=true; status="Conectado";
                    NativeAudio.randomTrack();
                } else if(type.equals("player_died")&&o.optInt("id")==id) die(true);
                invalidate();
            } catch(Exception e) { status="Erro ao ler dados"; invalidate(); }
        });
    }

    void saveServerProfile(JSONObject profile) {
        if(!(getContext() instanceof Activity))return;
        Activity activity=(Activity)getContext();
        JSONArray owned=profile.optJSONArray("ownedSkins");
        StringBuilder ids=new StringBuilder();
        if(owned!=null)for(int i=0;i<owned.length();i++){if(ids.length()>0)ids.append('|');ids.append(owned.optString(i));}
        activity.getPreferences(Context.MODE_PRIVATE).edit()
            .putInt("server_coins",Math.max(0,profile.optInt("coins",0)))
            .putString("server_owned_skins",ids.toString())
            .putString("server_equipped_skin",profile.optString("equippedSkin","bright_green"))
            .putString("account_nickname",profile.optString("nickname",""))
            .putInt("skin_high_score",Math.max(0,profile.optInt("highScore",0)))
            .apply();
    }

    void items(JSONArray a,List<int[]> out,boolean rarity) {
        out.clear(); if(a==null)return;
        for(int i=0;i<a.length();i++) {
            JSONArray v=a.optJSONArray(i);
            if(v!=null&&v.length()>=2) out.add(new int[]{v.optInt(0),v.optInt(1),rarity&&v.length()>2?v.optInt(2):0});
        }
    }

    boolean occupied(int x,int y) {
        for(int[] q:snake) if(q[0]==x&&q[1]==y)return true;
        for(int[] q:foods) if(q[0]==x&&q[1]==y)return true;
        for(int[] q:coins) if(q[0]==x&&q[1]==y)return true;
        for(int[] q:apples) if(q[0]==x&&q[1]==y)return true;
        return false;
    }
    void spawnLocalFood() {
        for(int i=0;i<200;i++) { int x=(int)(Math.random()*world),y=(int)(Math.random()*world); if(!occupied(x,y)){foods.add(new int[]{x,y,(int)(Math.random()*4)});return;} }
    }
    void spawnLocalCoin(){
        for(int i=0;i<300;i++) {
            int x=(int)(Math.random()*world), y=(int)(Math.random()*world);
            if(!occupied(x,y) && !containsItem(coins,x,y) && !containsItem(apples,x,y)) {
                coins.add(new int[]{x,y,0});
                return;
            }
        }
    }

    boolean containsItem(List<int[]> items,int x,int y) {
        for(int[] item:items) if(item[0]==x&&item[1]==y) return true;
        return false;
    }

    int xpForNextLevel() {
        int[] thresholds={10,25,50,100,200,400,800};
        return level<=thresholds.length?thresholds[level-1]:Integer.MAX_VALUE;
    }

    void addXp(int amount) {
        xp+=amount;
        while(level<8 && xp>=xpForNextLevel()) {
            xp-=xpForNextLevel();
            level++;
            levelUpUntil=System.currentTimeMillis()+2500;
            NativeAudio.play("levelup");
            int targetCoins=3+(level-1)*2;
            while(coins.size()<targetCoins) spawnLocalCoin();
        }
    }

    int targetOfflineCoins() { return 3+(level-1)*2; }
    void spawnLocalApple(){for(int i=0;i<300;i++){int x=(int)(Math.random()*world),y=(int)(Math.random()*world);if(!occupied(x,y)){apples.add(new int[]{x,y,0});return;}}}

    void step() {
        if(!connected||!alive||snake.isEmpty())return;
        dx=nx; dy=ny;
        int x=snake.get(0)[0]+dx,y=snake.get(0)[1]+dy;
        if(offline && "INFINITE".equals(mode)) {
            if(x<0)x=world-1; else if(x>=world)x=0;
            if(y<0)y=world-1; else if(y>=world)y=0;
        } else if(x<0||y<0||x>=world||y>=world) {
            if(!offline)send("{\"type\":\"die\",\"reason\":\"border\"}"); die(true); return;
        }
        for(int i=1;i<snake.size()-1;i++) if(snake.get(i)[0]==x&&snake.get(i)[1]==y) {
            if(!offline)send("{\"type\":\"die\",\"reason\":\"self\"}"); die(true); return;
        }
        for(Player z:others.values()) if(z.alive) for(int[] a:z.body) if(a[0]==x&&a[1]==y) {
            if(!offline)send("{\"type\":\"die\",\"reason\":\"player\"}"); die(true); return;
        }

        int grow=0;
        for(int[] a:foods) if(a[0]==x&&a[1]==y) {
            int r=Math.min(3,Math.max(0,a[2]));
            grow=new int[]{1,3,8,20}[r];
            score+=new int[]{10,30,80,200}[r]*(doublePoints?2:1); addXp(new int[]{5,15,40,100}[r]);
            foods.remove(a); NativeAudio.play("eat"); break;
        }
        int[] tail=snake.get(snake.size()-1);
        snake.add(0,new int[]{x,y});
        if(grow==0) snake.remove(snake.size()-1);
        else {
            for(int i=1;i<grow;i++) snake.add(new int[]{tail[0],tail[1]});
            if(offline) spawnLocalFood();
        }

        for(int[] a:coins) if(a[0]==x&&a[1]==y) {
            coins.remove(a); coinsN+=3; NativeAudio.play("eat");
            if(!offline) {
                send("{\"type\":\"coin_collect\",\"x\":"+x+",\"y\":"+y+"}");
            } else {
                if(getContext() instanceof Activity) {
                    Activity activity=(Activity)getContext();
                    android.content.SharedPreferences prefs=activity.getPreferences(Context.MODE_PRIVATE);
                    int balance=Math.max(0,prefs.getInt("offline_coins",0));
                    prefs.edit().putInt("offline_coins",balance+3).apply();
                }
                while(coins.size()<targetOfflineCoins()) spawnLocalCoin();
            }
            break;
        }
        for(int[] a:apples) if(a[0]==x&&a[1]==y) {
            apples.remove(a); score+=50; applesN++; addXp(25); NativeAudio.play("eat");
            if(!offline)send("{\"type\":\"apple_collect\",\"x\":"+x+",\"y\":"+y+"}");
            else spawnLocalApple();
            break;
        }

        if(offline) {
            if("PARTY".equals(mode)) {
                partyMoves++;
                if(partyEventTicks>0 && --partyEventTicks==0) { partyEvent=""; doublePoints=false; }
                if(partyMoves>=220) {
                    partyMoves=0;
                    String[] ev={"DOUBLE POINTS!","SPEED UP!","SLOW MOTION!","BONUS FOOD!"};
                    partyEvent=ev[(int)(Math.random()*ev.length)]; partyEventTicks=120;
                    doublePoints="DOUBLE POINTS!".equals(partyEvent);
                    if("BONUS FOOD!".equals(partyEvent)){spawnLocalFood();spawnLocalFood();}
                    NativeAudio.play("levelup");
                }
            }
            if("CLASSIC".equals(mode)&&snake.size()>=60) { status="VITÓRIA!"; alive=false; NativeAudio.stopMusic(); NativeAudio.play("win"); }
            return;
        }

        StringBuilder b=new StringBuilder("[");
        for(int i=0;i<snake.size();i++){if(i>0)b.append(',');b.append('[').append(snake.get(i)[0]).append(',').append(snake.get(i)[1]).append(']');}
        b.append(']');
        send("{\"type\":\"move\",\"dir\":["+dx+","+dy+"],\"head\":["+x+","+y+"],\"body\":"+b+",\"sprint\":"+sprint+"}");
    }

    void die(boolean sendSound) {
        alive=false; status="Você morreu"; NativeAudio.stopMusic();
        if(getContext() instanceof Activity) {
            Activity a=(Activity)getContext();
            android.content.SharedPreferences prefs=a.getPreferences(Context.MODE_PRIVATE);
            int best=Math.max(prefs.getInt("skin_high_score",0),score);
            prefs.edit().putInt("skin_high_score",best).apply();
        }
        if(sendSound) NativeAudio.play("hit");
    }

    public void loop() {
        post(new Runnable(){public void run(){
            if(!connected){invalidate();postDelayed(this,200);return;}
            long n=System.currentTimeMillis();
            int delay=sprint?105:145;
            if(offline&&"PARTY".equals(mode)) {
                if("SPEED UP!".equals(partyEvent)) delay=95;
                if("SLOW MOTION!".equals(partyEvent)) delay=210;
            }
            if(n-last>delay){last=n;step();}
            invalidate();postDelayed(this,32);
        }});
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); float w=getWidth(),h=getHeight(); c.drawColor(0xff080e18);
        if(snake.isEmpty()) {
            label(c,status,w/2,h/2-42*density(),23,0xff75d9ff);
            if(!alive)drawRespawnButton(c,w,h);
            return;
        }
        float z=Math.min(22,Math.max(12,Math.min(w/18,h/14))),cx=w/2,cy=h/2;
        int hx=snake.get(0)[0],hy=snake.get(0)[1];
        float ox=cx-(hx+.5f)*z,oy=cy-(hy+.5f)*z;
        p.setColor(0xff172638);c.drawRect(ox,oy,ox+world*z,oy+world*z,p);
        p.setColor(detailedGraphics?0x267fa8c9:0x147fa8c9);p.setStrokeWidth(1);
        int x0=Math.max(0,(int)(-ox/z)),x1=Math.min(world,(int)((w-ox)/z)+1);
        int y0=Math.max(0,(int)(-oy/z)),y1=Math.min(world,(int)((h-oy)/z)+1);
        for(int x=x0;x<=x1;x++)c.drawLine(ox+x*z,Math.max(0,oy),ox+x*z,Math.min(h,oy+world*z),p);
        for(int y=y0;y<=y1;y++)c.drawLine(Math.max(0,ox),oy+y*z,Math.min(w,ox+world*z),oy+y*z,p);
        for(int[] a:foods)cell(c,ox,oy,z,a[0],a[1],new int[]{0xff50d080,0xffffd84d,0xffd76aff,0xffff5656}[Math.min(3,Math.max(0,a[2]))]);
        for(int[] a:coins)drawItem(c,ox,oy,z,a[0],a[1],coinBitmap,0xffffcf35);
        for(int[] a:apples)drawItem(c,ox,oy,z,a[0],a[1],appleBitmap,0xffef4545);
        for(Player q:others.values())if(q.alive)for(int[] a:q.body)cell(c,ox,oy,z,a[0],a[1],0xffd18a4c);
        for(int i=snake.size()-1;i>=0;i--)drawSnakeSegment(c,ox,oy,z,snake.get(i)[0],snake.get(i)[1],i==0);
        if(showSnakeFace && !snake.isEmpty()) drawSnakeFace(c,ox,oy,z,snake.get(0)[0],snake.get(0)[1]);
        drawHud(c,w,h);
        controls(c,w,h);
        if(!alive){
            p.setColor(0xaa000000);c.drawRect(0,0,w,h,p);
            label(c,status,w/2,h/2-42*density(),30,status.contains("VITÓRIA")?0xffffd45c:-1);
            drawRespawnButton(c,w,h);
        }
    }

    float density(){return getResources().getDisplayMetrics().density;}

    void drawRespawnButton(Canvas c,float w,float h) {
        float d=density(), bw=210*d, bh=58*d, x=w/2-bw/2, y=h/2+8*d;
        p.setColor(0xff16834b);c.drawRoundRect(x,y,x+bw,y+bh,14*d,14*d,p);
        p.setColor(0xffa7ffd0);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);
        c.drawRoundRect(x,y,x+bw,y+bh,14*d,14*d,p);p.setStyle(Paint.Style.FILL);
        label(c,"RESPAWNAR",w/2,y+bh/2+6*d,19*d,Color.WHITE);
    }

    boolean hitRespawn(float x,float y) {
        float d=density(),bw=210*d,bh=58*d,left=getWidth()/2-bw/2,top=getHeight()/2+8*d;
        return !alive && x>=left && x<=left+bw && y>=top && y<=top+bh;
    }

    void respawn() {
        if(alive)return;
        if(offline) {
            int x=world/2,y=world/2;snake.clear();
            for(int i=0;i<4;i++)snake.add(new int[]{x-i,y});
            alive=true;score=0;dx=1;dy=0;nx=1;ny=0;status="Vivo";
            NativeAudio.randomTrack();
        } else {
            status="Solicitando respawn...";
            send("{\"type\":\"respawn\"}");
        }
        invalidate();
    }

    void drawHud(Canvas c,float w,float h) {
        float d=getResources().getDisplayMetrics().density;
        float left=8*d, top=8*d, panelW=195*d, panelH=185*d;
        p.setColor(0xc80a0e1e); c.drawRoundRect(left,top,left+panelW,top+panelH,12*d,12*d,p);
        p.setColor(0x3264a0ff); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d);
        c.drawRoundRect(left,top,left+panelW,top+panelH,12*d,12*d,p); p.setStyle(Paint.Style.FILL);
        String[] lines={
            "Score:   "+score,
            "Level:   "+level,
            "Combo:   x1",
            "Coins:   "+coinsN,
            "Apples:  "+applesN,
            "Server Est: "+((System.currentTimeMillis()-startedAt)/1000)+"s",
            "Players: "+(others.size()+1),
            "Sprint:  "+(sprint?"ON":"OFF"),
            "FPS:     "+fps
        };
        for(int i=0;i<lines.length;i++) {
            p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.create("Arial",Typeface.BOLD));
            p.setTextSize(13*d); p.setColor(i==0?Color.WHITE:0xffb4dcff);
            c.drawText(lines[i],18*d,28*d+i*19*d,p);
        }
        p.setColor(0xb4141428); c.drawRoundRect(left,h-20*d,left+panelW,h-8*d,6*d,6*d,p);
        float progress=xpForNextLevel()==Integer.MAX_VALUE?1f:Math.min(1f,(float)xp/Math.max(1,xpForNextLevel()));
        p.setColor(0xff50c8ff); c.drawRoundRect(left,h-20*d,left+panelW*progress,h-8*d,6*d,6*d,p);
        p.setColor(0x6464b4ff); p.setStyle(Paint.Style.STROKE);
        c.drawRoundRect(left,h-20*d,left+panelW,h-8*d,6*d,6*d,p); p.setStyle(Paint.Style.FILL);
        p.setTextSize(9*d); p.setTypeface(Typeface.DEFAULT_BOLD); p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.LEFT); c.drawText("XP",left+4*d,h-11*d,p);

        p.setTextSize(11*d); p.setTypeface(Typeface.DEFAULT); p.setColor(0xff8cc8ff);
        p.setTextAlign(Paint.Align.RIGHT); c.drawText("☀ DAY",w-12*d,22*d,p);
        if(!status.isEmpty()) {
            p.setTextAlign(Paint.Align.LEFT); p.setTextSize(11*d);
            p.setColor(connected?0xc864ff78:0xc8ff7850);
            c.drawText(status,8*d,h-28*d,p);
        }
        if(levelUpUntil>System.currentTimeMillis()) {
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(22*d); p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setColor(0xffffd45c); c.drawText("LEVEL UP!  LEVEL "+level,w/2,h*.22f,p);
        }
        if(!partyEvent.isEmpty()) {
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(14*d); p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setColor(0xffffdc64); c.drawText(partyEvent,w/2,top+panelH+24*d,p);
        }
    }

    String textureForSkin(String id) {
        switch(id) {
            case "midnight": return "moon.png";
            case "toxic": return "toxic.png";
            case "rich": return "money.png";
            case "tex_snake": return "snake_scale.png";
            case "tex_pixel": return "pixel_camo.png";
            case "tex_neon_grid": return "neon_grid.png";
            case "tex_galaxy": return "galaxy.png";
            case "tex_fire": return "fire.png";
            case "tex_tomato": return "tomato.png";
            default: return null;
        }
    }

    int skinColor() {
        switch(skinId) {
            case "cocoa": return 0xff562424;
            case "bright_blue": return 0xff0d69ac;
            case "bright_green": return 0xff4b974b;
            case "lilac": return 0xffa75e9b;
            case "hurricane": return 0xff635f62;
            case "violet": return 0xff6b327c;
            case "bright_red": return 0xffc4281c;
            case "yeller": return 0xffffdc00;
            case "really_blue": return 0xff002060;
            case "really_red": return 0xffff0000;
            case "gold": return 0xffc89600;
            case "neon_green": return 0xff00ea00;
            case "neon_pink": return 0xffff0096;
            case "neon_blue": return 0xff0096ff;
            case "obsidian": return 0xff141423;
            case "lava": return 0xffc83c00;
            case "ice": return 0xffb4e6ff;
            default: return 0xff269bc7;
        }
    }

    void drawSnakeSegment(Canvas c,float ox,float oy,float z,int x,int y,boolean head) {
        float left=ox+x*z,top=oy+y*z;
        if(left>getWidth()||top>getHeight()||left+z<0||top+z<0)return;
        if("rainbow".equals(skinId)) {
            float hue=((System.currentTimeMillis()/12 + x*17 + y*7)%360)/360f;
            int rainbow=Color.HSVToColor(new float[]{hue,0.9f,1f});
            cell(c,ox,oy,z,x,y,head?Color.rgb(Math.min(255,Color.red(rainbow)+35),Math.min(255,Color.green(rainbow)+35),Math.min(255,Color.blue(rainbow)+35)):rainbow);
        } else if(detailedGraphics && snakeTexture!=null) {
            Rect dst=new Rect((int)(left+1),(int)(top+1),(int)(left+z-1),(int)(top+z-1));
            c.drawBitmap(snakeTexture,null,dst,p);
            p.setColor(0x55000000);c.drawRoundRect(dst.left,dst.top,dst.right,dst.bottom,4,4,p);
        } else {
            int base=skinColor();
            cell(c,ox,oy,z,x,y,head?Color.rgb(Math.min(255,Color.red(base)+35),Math.min(255,Color.green(base)+35),Math.min(255,Color.blue(base)+35)):base);
        }
    }

    void drawItem(Canvas c,float ox,float oy,float z,int x,int y,Bitmap bitmap,int fallback) {
        float left=ox+x*z, top=oy+y*z;
        if(left>getWidth()||top>getHeight()||left+z<0||top+z<0)return;
        if(detailedGraphics && bitmap!=null && !bitmap.isRecycled()) {
            Rect dst=new Rect((int)(left+1),(int)(top+1),(int)(left+z-1),(int)(top+z-1));
            p.setAlpha(255); c.drawBitmap(bitmap,null,dst,p);
        } else {
            p.setColor(fallback); c.drawCircle(left+z/2,top+z/2,z*.38f,p);
            if(fallback==0xffef4545) {
                p.setColor(0xff3ca85b); c.drawOval(left+z*.48f,top+z*.05f,left+z*.78f,top+z*.32f,p);
            }
        }
    }

    void drawSnakeFace(Canvas c,float ox,float oy,float z,int x,int y) {
        float left=ox+x*z,top=oy+y*z,cx=left+z/2,cy=top+z/2;
        float ex=dx*z*.18f,ey=dy*z*.18f;
        p.setColor(Color.WHITE);
        c.drawCircle(cx-ex-ey*.65f,cy-ey+ex*.65f,z*.10f,p);
        c.drawCircle(cx+ex-ey*.65f,cy+ey+ex*.65f,z*.10f,p);
        p.setColor(0xff102333);
        c.drawCircle(cx-ex-ey*.65f+dx*z*.025f,cy-ey+ex*.65f+dy*z*.025f,z*.045f,p);
        c.drawCircle(cx+ex-ey*.65f+dx*z*.025f,cy+ey+ex*.65f+dy*z*.025f,z*.045f,p);
    }

    void cell(Canvas c,float ox,float oy,float z,int x,int y,int color) {
        float a=ox+x*z,b=oy+y*z;
        if(a>getWidth()||b>getHeight()||a+z<0||b+z<0)return;
        p.setColor(color);c.drawRoundRect(a+1,b+1,a+z-1,b+z-1,4,4,p);
    }
    void label(Canvas c,String s,float x,float y,float size,int color) {
        p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,p);
    }
    void controls(Canvas c,float w,float h) {
        // Only the sprint button remains visible; movement is controlled by dragging anywhere.
        float d=getResources().getDisplayMetrics().density;
        float runX=w-100*d, runY=h-105*d, runR=68*d;
        p.setColor(0xbb9b5b25);
        c.drawCircle(runX,runY,runR,p);
        p.setColor(0x88e4a35d);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2*d);
        c.drawCircle(runX,runY,runR-5*d,p); p.setStyle(Paint.Style.FILL);
        label(c,sprint?"SPRINT":"CORRER",runX,runY+6*d,17*d,-1);
    }

    void updateDragDirection(float x,float y) {
        float mx=x-steeringX, my=y-steeringY;
        float threshold=12*getResources().getDisplayMetrics().density;
        if(Math.hypot(mx,my)<threshold) return;
        if(Math.abs(mx)>Math.abs(my)) {
            if(mx<0 && dx!=1){nx=-1;ny=0;}
            else if(mx>0 && dx!=-1){nx=1;ny=0;}
        } else {
            if(my<0 && dy!=1){nx=0;ny=-1;}
            else if(my>0 && dy!=-1){nx=0;ny=1;}
        }
        steeringX=x; steeringY=y;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        final int action=e.getActionMasked();
        final int index=e.getActionIndex();
        final int pointer=e.getPointerId(index);
        final float d=getResources().getDisplayMetrics().density;
        final float runX=getWidth()-100*d, runY=getHeight()-105*d, runR=68*d;

        if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            float x=e.getX(index), y=e.getY(index);
            if(hitRespawn(x,y)) { respawn(); return true; }
            if(!alive)return true;
            if(Math.hypot(x-runX,y-runY)<=runR+20*d) {
                sprintPointer=pointer; sprint=true;
            } else if(steeringPointer==-1) {
                steeringPointer=pointer; steeringX=x; steeringY=y;
            }
            invalidate();
            return true;
        }

        if(action==MotionEvent.ACTION_MOVE) {
            for(int i=0;i<e.getPointerCount();i++) {
                int pid=e.getPointerId(i);
                if(pid==steeringPointer) updateDragDirection(e.getX(i),e.getY(i));
                if(pid==sprintPointer) {
                    float x=e.getX(i),y=e.getY(i);
                    sprint=Math.hypot(x-runX,y-runY)<=runR+30*d;
                }
            }
            invalidate();
            return true;
        }

        if(action==MotionEvent.ACTION_POINTER_UP || action==MotionEvent.ACTION_UP) {
            if(pointer==steeringPointer) steeringPointer=-1;
            if(pointer==sprintPointer) { sprintPointer=-1; sprint=false; }
            if(action==MotionEvent.ACTION_UP) {
                steeringPointer=-1; sprintPointer=-1; sprint=false;
            }
            invalidate();
            return true;
        }

        if(action==MotionEvent.ACTION_CANCEL) {
            steeringPointer=-1; sprintPointer=-1; sprint=false;
            invalidate();
            return true;
        }
        return true;
    }
    void close(){if(net!=null)net.close();NativeAudio.stopMusic();}
}