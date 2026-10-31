package com.sneakysnakes;

import android.app.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    NativeGameView game;
    EditText name, host, port;
    Switch secure;
    static final String SERVER_API = "https://sneakysnakes-server-list.vercel.app/api/servers";
    LinearLayout root;
    NativeSocket shopSocket;
    boolean shopVisible=false;
    static final String[] SKIN_IDS={"cocoa","bright_blue","lilac","hurricane","violet","bright_red","bright_green","yeller","really_blue","really_red","gold","neon_green","neon_pink","neon_blue","rainbow"};
    static final String[] SKIN_NAMES={"Cocoa","Blue","Lilac","Grey","Violet","Red","Green","Yellow","Bright Blue","Bright Red","Gold","Classic","Neon Pink","Neon Blue","Rainbow"};
    static final int[] SKIN_COSTS={50,100,150,200,250,300,0,350,400,450,450,450,500,500,700};
    static final int[] SKIN_COLORS={0xff562424,0xff0d69ac,0xffa75e9b,0xff635f62,0xff6b327c,0xffc4281c,0xff4b974b,0xffffdc00,0xff002060,0xffff0000,0xffc89600,0xff00ea00,0xffff0096,0xff0096ff,0xffff5050};
    static final int[] SKIN_UNLOCK_SCORES={50,100,150,200,250,300,0,350,400,450,450,450,500,500,700};
    String equippedSkin(){return getPreferences(0).getString("server_equipped_skin","bright_blue");}
    int wallet(){return getPreferences(0).getInt("server_coins",0);}
    boolean owns(String id){
        if(id.equals("bright_green")||id.equals("bright_blue"))return true;
        if(id.equals("cocoa")||id.equals("bright_blue")||id.equals("lilac")||id.equals("hurricane")||id.equals("violet")||id.equals("bright_red")||id.equals("yeller")||id.equals("really_blue")||id.equals("really_red")||id.equals("gold")||id.equals("neon_green")||id.equals("neon_pink")||id.equals("neon_blue")||id.equals("rainbow")) {
            return getPreferences(0).getInt("skin_high_score",0)>=SKIN_UNLOCK_SCORES[indexOfSkin(id)];
        }
        String data=getPreferences(0).getString("server_owned_skins","");
        for(String owned:data.split("\\|"))if(owned.equals(id))return true;
        return false;
    }
    void sendShopCommand(String type,String skin){
        if(shopSocket==null)return;
        try{JSONObject o=new JSONObject();o.put("type",type);if(skin!=null)o.put("skin",skin);shopSocket.send(o.toString());}
        catch(Exception ignored){}
    }


    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        hideSystemUI();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        NativeAudio.init(getAssets());
        showHome();
        NativeAudio.menuMusic();
    }

    int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    GradientDrawable shape(int color,int stroke) {
        GradientDrawable g=new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(13));
        if(stroke!=0)g.setStroke(dp(1),stroke);
        return g;
    }

    TextView text(String value,int size,int color) {
        TextView t=new TextView(this); t.setText(value); t.setTextColor(color);
        t.setTextSize(size); t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        return t;
    }

    Button button(String value,boolean primary) {
        Button b=new Button(this); b.setText(value); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setTextSize(14); b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(40)); b.setMinimumHeight(dp(40));
        b.setPadding(dp(12),0,dp(12),0);
        b.setBackground(shape(primary?0xff176b91:0xff15283b,primary?0xff55c9ed:0xff34516b));
        b.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN)NativeAudio.play("click");return false;});
        return b;
    }

    void base(String title,String subtitle) {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL); root.setPadding(dp(20),dp(10),dp(20),dp(10));
        root.setBackgroundColor(0xff080e18);
        TextView logo=text("SNEAKYSNAKES",29,Color.WHITE);
        logo.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        root.addView(logo,new LinearLayout.LayoutParams(-1,dp(46)));
        View line=new View(this); line.setBackgroundColor(0xff26394b);
        root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        if(title!=null) {
            TextView heading=text(title,19,0xffe8f4ff);
            heading.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(34));
            hp.topMargin=dp(5); root.addView(heading,hp);
        }
        if(subtitle!=null) {
            TextView sub=text(subtitle,12,0xff8fa9bf);
            root.addView(sub,new LinearLayout.LayoutParams(-1,dp(22)));
        }
        setContentView(root);
    }

    void addButton(String label,boolean primary,Runnable action) {
        Button b=button(label,primary);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(42));
        lp.topMargin=dp(5); root.addView(b,lp); b.setOnClickListener(v->action.run());
    }

    void showHome() {
        base(null,null);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(14)));
        addButton("JOGAR",true,()->showPlayMenu());
        addButton("LOJA DE SKINS",false,()->showShop());
        addButton("CONFIGURAÇÕES",false,()->showSettings());
        root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(8)));
        root.addView(text("ANDROID NATIVO  •  SNEAKYSNAKES",11,0xff71879b),new LinearLayout.LayoutParams(-1,dp(22)));
    }

    void showPlayMenu() {
        base("JOGAR","Escolha como deseja jogar");
        addButton("JOGAR ON-LINE",true,()->showOnline());
        addButton("JOGAR OFF-LINE",false,()->showOfflineModes());
        addButton("VOLTAR",false,()->showHome());
    }

    void showOfflineModes() {
        base("JOGAR OFF-LINE","Escolha um modo de jogo");
        addButton("CLASSIC MODE",true,()->startOffline("CLASSIC"));
        addButton("PARTY MODE",false,()->startOffline("PARTY"));
        addButton("INFINITE MODE",false,()->startOffline("INFINITE"));
        addButton("VOLTAR",false,()->showPlayMenu());
    }

    EditText edit(String hint,String value,boolean numeric) {
        EditText e=new EditText(this); e.setSingleLine(true); e.setText(value); e.setHint(hint);
        e.setTextColor(Color.WHITE); e.setHintTextColor(0xff8299ad); e.setTextSize(15);
        e.setPadding(dp(13),0,dp(13),0); e.setBackground(shape(0xff101c2a,0xff31485e));
        if(numeric)e.setInputType(2);
        return e;
    }

    void showOnline() {
        base("JOGAR ON-LINE","Entre em um servidor oficial ou informe um endereço");
        name=edit("Seu nome","Player",false);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(48)); np.topMargin=dp(6); root.addView(name,np);
        TextView divider=text("SERVIDOR OFICIAL",12,0xff83a9c5);
        LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,dp(22)); dp1.topMargin=dp(7); root.addView(divider,dp1);
        Button random=button("ENTRAR EM SERVIDOR OFICIAL ALEATÓRIO",true);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(48)); root.addView(random,rp);
        random.setOnClickListener(v->randomOfficialServer(random));
        TextView manual=text("OU CONECTAR MANUALMENTE",12,0xff83a9c5);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(34)); mp.topMargin=dp(5); root.addView(manual,mp);
        host=edit("Host ou IP","163.176.148.122",false);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(46)); root.addView(host,hp);
        port=edit("Porta","3000",true);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(46)); pp.topMargin=dp(7); root.addView(port,pp);
        secure=new Switch(this); secure.setText("Conexão segura (wss://)");
        secure.setTextColor(0xffdce9f5); secure.setTextSize(13);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(42)); root.addView(secure,sp);
        addButton("CONECTAR",true,()->startOnline());
        addButton("VOLTAR",false,()->showPlayMenu());
    }

    String playerName() {
        String n=name==null?"Player":name.getText().toString().trim();
        return n.isEmpty()?"Player":n;
    }

    void startOnline() {
        String h=host.getText().toString().trim(), p=port.getText().toString().trim();
        if(h.startsWith("ws://"))h=h.substring(5); else if(h.startsWith("wss://"))h=h.substring(6);
        if(h.contains("/"))h=h.substring(0,h.indexOf('/'));
        if(h.contains(":")){String[] bits=h.split(":",2);h=bits[0];if(p.isEmpty()||p.equals("3000"))p=bits[1];}
        int pn;try{pn=Integer.parseInt(p);}catch(Exception e){pn=-1;}
        if(h.isEmpty()||h.contains(" ")||pn<1||pn>65535){
            Toast.makeText(this,"Confira o host e a porta (1–65535).",Toast.LENGTH_LONG).show();return;
        }
        String address=(secure.isChecked()?"wss://":"ws://")+h+":"+pn;
        getPreferences(0).edit().putString("last_player_name",playerName()).putString("last_server_url",address).apply();
        launchGame(playerName(),address,false,"ONLINE");
    }

    void randomOfficialServer(Button b) {
        b.setEnabled(false); b.setText("Buscando servidores...");
        new Thread(()->{
            String chosen=null,error=null; int count=0; HttpURLConnection c=null;
            try {
                c=(HttpURLConnection)new URL(SERVER_API).openConnection();
                c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setUseCaches(false);c.setInstanceFollowRedirects(true);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","SneakySnakes-Android");
                int status=c.getResponseCode();
                if(status!=200) throw new IOException("HTTP "+status);
                StringBuilder body=new StringBuilder();
                try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){
                    String line;while((line=r.readLine())!=null)body.append(line);
                }
                JSONObject json=new JSONObject(body.toString());
                if(!json.optBoolean("success",false)) throw new IOException("A API não confirmou a lista.");
                JSONArray servers=json.optJSONArray("servers");
                if(servers!=null){ArrayList<String> valid=new ArrayList<>();
                    for(int i=0;i<servers.length();i++){JSONObject s=servers.optJSONObject(i);if(s==null)continue;
                        String h=s.optString("host","").trim();int p=s.optInt("port",3000);
                        if(!h.isEmpty()&&!h.contains("/")&&!h.contains(":")&&p>0&&p<=65535)valid.add(h+"|"+p);
                    }
                    count=valid.size();if(!valid.isEmpty())chosen=valid.get(new Random().nextInt(valid.size()));
                }
            }catch(Exception e){error=e.getMessage();}finally{if(c!=null)c.disconnect();}
            final String selected=chosen,problem=error;final int found=count;
            runOnUiThread(()->{
                b.setEnabled(true);b.setText("ENTRAR EM SERVIDOR OFICIAL ALEATÓRIO");
                if(selected==null){Toast.makeText(this,problem==null?"Nenhum servidor oficial disponível.":"Falha ao buscar: "+problem,Toast.LENGTH_LONG).show();return;}
                String[] v=selected.split("\\|",2);
                String address="ws://"+v[0]+":"+v[1];
                getPreferences(0).edit().putString("last_player_name",playerName()).putString("last_server_url",address).apply();
                launchGame(playerName(),address,false,"ONLINE");
            });
        },"official-server-list").start();
    }

    void showShop() {
        shopVisible=true;
        base("LOJA DE SKINS","Moedas e compras salvas neste celular");
        renderShop();
    }

    void saveLocalShop(String skin) {
        String current=getPreferences(0).getString("server_owned_skins","");
        boolean alreadyOwned=current.equals(skin) || current.startsWith(skin+"|") ||
            current.endsWith("|"+skin) || current.contains("|"+skin+"|");
        if(!alreadyOwned) current=current.isEmpty()?skin:current+"|"+skin;
        getPreferences(0).edit().putString("server_owned_skins",current)
            .putString("server_equipped_skin",skin).apply();
    }

    void renderShop(){
        base("LOJA DE SKINS","Saldo local: "+wallet()+" moedas");
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<SKIN_IDS.length;i++){
            final String id=SKIN_IDS[i];
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10),dp(5),dp(10),dp(5));row.setBackground(shape(0xff101c2a,0xff263e54));
            LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,dp(64));rowLp.bottomMargin=dp(7);list.addView(row,rowLp);
            View preview=new View(this);preview.setBackground(shape(SKIN_COLORS[i],0xffa9c9df));
            LinearLayout.LayoutParams prevLp=new LinearLayout.LayoutParams(dp(38),dp(38));prevLp.rightMargin=dp(10);row.addView(preview,prevLp);
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
            TextView nm=text(SKIN_NAMES[i],14,Color.WHITE);nm.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            TextView price=text(owns(id)?"Desbloqueada":("Pontuação: "+SKIN_UNLOCK_SCORES[i]),11,0xffffd45c);price.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            info.addView(nm);info.addView(price);row.addView(info,new LinearLayout.LayoutParams(0,-1,1));
            boolean owned=owns(id);
            Button action=button(owned?(equippedSkin().equals(id)?"EQUIPADA":"EQUIPAR"):"COMPRAR",owned);
            row.addView(action,new LinearLayout.LayoutParams(dp(112),dp(42)));
            action.setEnabled(!owned || !equippedSkin().equals(id));
            action.setOnClickListener(v->{
                if(owned){
                    getPreferences(0).edit().putString("server_equipped_skin",id).apply();
                    Toast.makeText(this,SKIN_NAMES[indexOfSkin(id)]+" equipada!",Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(this,"Consiga a pontuação necessária em uma partida para desbloquear!",Toast.LENGTH_SHORT).show(); return;
                }
                renderShop();
            });
        }
        scroll.addView(list);LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,0,1);slp.topMargin=dp(6);root.addView(scroll,slp);
        addButton("VOLTAR",false,()->{shopVisible=false;showHome();});
    }

    int indexOfSkin(String id){
        for(int i=0;i<SKIN_IDS.length;i++)if(SKIN_IDS[i].equals(id))return i;
        return 0;
    }

    void showSettings() {
        base("CONFIGURAÇÕES","Personalize áudio e aparência");
        Switch sound=new Switch(this);sound.setText("Sons e músicas");sound.setTextColor(Color.WHITE);
        sound.setChecked(NativeAudio.isEnabled());root.addView(sound,new LinearLayout.LayoutParams(-1,dp(48)));
        sound.setOnCheckedChangeListener((v,on)->{NativeAudio.setEnabled(on);if(on)NativeAudio.menuMusic();});
        Switch graphics=new Switch(this);graphics.setText("Gráficos detalhados");graphics.setTextColor(Color.WHITE);
        graphics.setChecked(getPreferences(0).getBoolean("detailed_graphics",true));
        root.addView(graphics,new LinearLayout.LayoutParams(-1,dp(48)));
        graphics.setOnCheckedChangeListener((v,on)->getPreferences(0).edit().putBoolean("detailed_graphics",on).apply());
        Switch face=new Switch(this);face.setText("Rosto da cobra");face.setTextColor(Color.WHITE);
        face.setChecked(getPreferences(0).getBoolean("snake_face",true));
        root.addView(face,new LinearLayout.LayoutParams(-1,dp(48)));
        face.setOnCheckedChangeListener((v,on)->getPreferences(0).edit().putBoolean("snake_face",on).apply());
        addButton("VOLTAR",true,()->showHome());
    }

    void startOffline(String mode){launchGame(playerName(),"",true,mode);}
    void launchGame(String n,String address,boolean offline,String mode){
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);
        if(!offline)getPreferences(0).edit().putString("last_player_name",n).putString("last_server_url",address).apply();
        shopVisible=false;
        if(shopSocket!=null){shopSocket.close();shopSocket=null;}
        NativeAudio.stopMusic();game=new NativeGameView(this,n,address,offline,mode);setContentView(game);game.loop();
    }

    @Override protected void onPause() {
        NativeAudio.pauseForFocusLoss();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUI();
        NativeAudio.resumeAfterFocusGain();
    }

    @Override public void onBackPressed(){
        if(game!=null){game.close();game=null;NativeAudio.menuMusic();showHome();return;}
        showHome();
    }
    @Override protected void onDestroy(){if(game!=null)game.close();if(shopSocket!=null)shopSocket.close();NativeAudio.release();super.onDestroy();}
}