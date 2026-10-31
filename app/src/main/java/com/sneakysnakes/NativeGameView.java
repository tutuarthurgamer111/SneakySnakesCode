package com.sneakysnakes;

import android.content.Context;
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
    int world=64, id=-1, dx=1, dy=0, nx=1, ny=0, score=0, coinsN=0;
    boolean connected=false, alive=true, sprint=false, offline=false, doublePoints=false;
    String mode="ONLINE", partyEvent="", status="Conectando...";
    int partyMoves=0, partyEventTicks=0;
    long last=0;
    final ArrayList<int[]> snake=new ArrayList<>();
    final List<int[]> foods=new CopyOnWriteArrayList<>(), coins=new CopyOnWriteArrayList<>(), apples=new CopyOnWriteArrayList<>();
    final Map<Integer,Player> others=new ConcurrentHashMap<>();

    static class Player { String name; boolean alive; int[][] body; }

    NativeGameView(Context c,String name,String address) { this(c,name,address,false,"ONLINE"); }

    NativeGameView(Context c,String name,String address,boolean local,String selectedMode) {
        super(c);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
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
            net=new NativeSocket(address,this::message);
            net.start();
            net.send("{\"type\":\"join\",\"name\":\""+esc(name)+"\",\"skin\":\"bright_blue\"}");
        }
    }

    static String esc(String s) { return s.replace("\\","\\\\").replace("\"","\\\""); }
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
                    connected=true; status="Conectado"; NativeAudio.randomTrack();
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
                else if(type.equals("coin_spawn")) items(o.optJSONArray("coins"),coins,false);
                else if(type.equals("apple_spawn")) items(o.optJSONArray("apples"),apples,false);
                else if(type.equals("player_died")&&o.optInt("id")==id) die(true);
                invalidate();
            } catch(Exception e) { status="Erro ao ler dados"; invalidate(); }
        });
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
        return false;
    }
    void spawnLocalFood() {
        for(int i=0;i<200;i++) { int x=(int)(Math.random()*world),y=(int)(Math.random()*world); if(!occupied(x,y)){foods.add(new int[]{x,y,(int)(Math.random()*4)});return;} }
    }
    void spawnLocalCoin(){coins.add(new int[]{(int)(Math.random()*world),(int)(Math.random()*world),0});}
    void spawnLocalApple(){apples.add(new int[]{(int)(Math.random()*world),(int)(Math.random()*world),0});}

    void step() {
        if(!connected||!alive||snake.isEmpty())return;
        dx=nx; dy=ny;
        int x=snake.get(0)[0]+dx,y=snake.get(0)[1]+dy;
        if(x<0||y<0||x>=world||y>=world) { if(!offline)send("{\"type\":\"die\",\"reason\":\"border\"}"); die(true); return; }
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
            score+=new int[]{10,30,80,200}[r]*(doublePoints?2:1);
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
            coins.remove(a); coinsN++; NativeAudio.play("eat");
            if(!offline)send("{\"type\":\"coin_collect\",\"x\":"+x+",\"y\":"+y+"}");
            else spawnLocalCoin();
            break;
        }
        for(int[] a:apples) if(a[0]==x&&a[1]==y) {
            apples.remove(a); score+=50; NativeAudio.play("eat");
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
        if(snake.isEmpty()){label(c,status,w/2,h/2,23,0xff75d9ff);return;}
        float z=Math.min(22,Math.max(12,Math.min(w/18,h/14))),cx=w/2,cy=h/2;
        int hx=snake.get(0)[0],hy=snake.get(0)[1];
        float ox=cx-(hx+.5f)*z,oy=cy-(hy+.5f)*z;
        p.setColor(0xff172638);c.drawRect(ox,oy,ox+world*z,oy+world*z,p);
        p.setColor(0x267fa8c9);p.setStrokeWidth(1);
        int x0=Math.max(0,(int)(-ox/z)),x1=Math.min(world,(int)((w-ox)/z)+1);
        int y0=Math.max(0,(int)(-oy/z)),y1=Math.min(world,(int)((h-oy)/z)+1);
        for(int x=x0;x<=x1;x++)c.drawLine(ox+x*z,Math.max(0,oy),ox+x*z,Math.min(h,oy+world*z),p);
        for(int y=y0;y<=y1;y++)c.drawLine(Math.max(0,ox),oy+y*z,Math.min(w,ox+world*z),oy+y*z,p);
        for(int[] a:foods)cell(c,ox,oy,z,a[0],a[1],new int[]{0xff50d080,0xffffd84d,0xffd76aff,0xffff5656}[Math.min(3,Math.max(0,a[2]))]);
        for(int[] a:coins)cell(c,ox,oy,z,a[0],a[1],0xffffcf35);
        for(int[] a:apples)cell(c,ox,oy,z,a[0],a[1],0xffef4545);
        for(Player q:others.values())if(q.alive)for(int[] a:q.body)cell(c,ox,oy,z,a[0],a[1],0xffd18a4c);
        for(int i=snake.size()-1;i>=0;i--)cell(c,ox,oy,z,snake.get(i)[0],snake.get(i)[1],i==0?0xff7ce3ff:0xff269bc7);
        p.setColor(0xcc071321);c.drawRoundRect(10,10,Math.min(w-10,340),96,12,12,p);
        label(c,"SNEAKYSNAKES • "+status,Math.min(w/2,175),34,14,-1);
        label(c,mode+(partyEvent.isEmpty()?"":" • "+partyEvent),Math.min(w/2,175),57,12,0xffffd66b);
        label(c,"Pontos: "+score+"  Moedas: "+coinsN+"  Mapa: "+world+"x"+world,Math.min(w/2,175),80,11,0xff91dfff);
        controls(c,w,h);
        if(!alive){p.setColor(0xaa000000);c.drawRect(0,0,w,h,p);label(c,status,w/2,h/2,30,status.contains("VITÓRIA")?0xffffd45c:-1);}
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
        p.setColor(0x88345f82);c.drawCircle(82,h-86,70,p);
        label(c,"▲",82,h-126,28,-1);label(c,"▼",82,h-37,28,-1);
        label(c,"◀",40,h-81,28,-1);label(c,"▶",124,h-81,28,-1);
        p.setColor(0xbb9b5b25);c.drawCircle(w-72,h-76,55,p);
        label(c,sprint?"SPRINT":"CORRER",w-72,h-71,14,-1);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX(),y=e.getY(),w=getWidth(),h=getHeight();
        if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE) {
            if(x>w-145&&y>h-145)sprint=true;
            else if(x<165&&y>h-170) {
                float a=x-82,b=y-(h-86);
                if(Math.abs(a)>Math.abs(b)) {
                    if(a< -14&&dx!=1){nx=-1;ny=0;}
                    else if(a>14&&dx!=-1){nx=1;ny=0;}
                } else {
                    if(b< -14&&dy!=1){nx=0;ny=-1;}
                    else if(b>14&&dy!=-1){nx=0;ny=1;}
                }
            }
            invalidate();return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){sprint=false;return true;}
        return true;
    }
    void close(){if(net!=null)net.close();NativeAudio.stopMusic();}
}