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
    EditText accountUser, accountPass, accountNick;
    boolean shopVisible=false;
    boolean resolvingAccountServer=false;
    final String guestPlayerName="Guest"+(100000+new Random().nextInt(900000));
    // Shared catalog: identical IDs, score unlocks and coin prices to the PC version.
    static final String[] SKIN_IDS={"cocoa","bright_blue","lilac","hurricane","violet","bright_red","bright_green","yeller","really_blue","really_red","gold","neon_green","neon_pink","neon_blue","rainbow","obsidian","lava","ice","midnight","toxic","rich","tex_snake","tex_pixel","tex_neon_grid","tex_galaxy","tex_fire","tex_tomato"};
    static final String[] SKIN_NAMES={"Cocoa","Blue","Lilac","Grey","Violet","Red","Green","Yellow","Bright Blue","Bright Red","Gold","Classic","Neon Pink","Neon Blue","Rainbow","Obsidian","Lava","Ice Crystal","Midnight","Toxic","Rich","Snake Scale","Pixel Camo","Neon Grid","Galaxy","Fire","Tomatoes"};
    static final int[] SKIN_COSTS={0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,150,200,200,120,300,300,350,400,400,500,500,1000};
    static final int[] SKIN_COLORS={0xff562424,0xff0d69ac,0xffa75e9b,0xff635f62,0xff6b327c,0xffc4281c,0xff4b974b,0xffffdc00,0xff002060,0xffff0000,0xffc89600,0xff00ea00,0xffff0096,0xff0096ff,0xffff5050,0xff141423,0xffc83c00,0xffb4e6ff,0xff0a0a3c,0xff32c832,0xffffed59,0xff508c3c,0xff647850,0xff00c8c8,0xff1e0050,0xffc85000,0xffed4620};
    static final int[] SKIN_UNLOCK_SCORES={50,100,150,200,250,300,0,350,400,450,450,450,500,500,700,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1};
    String equippedSkin(){return getPreferences(0).getString("server_equipped_skin","bright_blue");}
    int wallet(){return getPreferences(0).getInt("server_coins",0);}
    boolean owns(String id){
        if(id.equals("bright_green"))return true;
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
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(root,new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
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
        addButton("CONTA",false,()->showAccount());
        addButton("LOJA DE SKINS",false,()->showShop());
        addButton("CONFIGURAÇÕES",false,()->showSettings());
        if(getPreferences(0).getString("account_token","").isEmpty()){TextView hint=text("Para criar uma conta, clique em Conta.",11,0xff8fa9bf);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(28));hp.topMargin=dp(4);root.addView(hint,hp);}
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
        addButton("Normal mode",true,()->startOffline("CLASSIC"));
        addButton("Party mode",false,()->startOffline("PARTY"));
        addButton("Infinite Mode",false,()->startOffline("INFINITE"));
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
        String visibleName=playerName();
        TextView nameLabel=text((getPreferences(0).getString("account_token","").isEmpty()?"Visitante: ":"Conta: ")+visibleName,13,0xff8fe0ad);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(34)); np.topMargin=dp(6); root.addView(nameLabel,np);
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
        String token=getPreferences(0).getString("account_token","");
        if(!token.isEmpty()) {
            String nickname=getPreferences(0).getString("account_nickname","");
            if(!nickname.trim().isEmpty())return nickname.trim();
        }
        return guestPlayerName;
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
                        if(!h.isEmpty()&&!h.contains("/")&&!h.contains(":")&&p>0&&p<=65535)valid.add(h+"|"+p+"|"+(s.optBoolean("secure",false)||p==443));
                    }
                    count=valid.size();if(!valid.isEmpty())chosen=valid.get(new Random().nextInt(valid.size()));
                }
            }catch(Exception e){error=e.getMessage();}finally{if(c!=null)c.disconnect();}
            final String selected=chosen,problem=error;final int found=count;
            runOnUiThread(()->{
                b.setEnabled(true);b.setText("ENTRAR EM SERVIDOR OFICIAL ALEATÓRIO");
                if(selected==null){Toast.makeText(this,problem==null?"Nenhum servidor oficial disponível.":"Falha ao buscar: "+problem,Toast.LENGTH_LONG).show();return;}
                String[] v=selected.split("\\|",3);
                boolean tls=v.length>2&&Boolean.parseBoolean(v[2]);
                String address=(tls?"wss://":"ws://")+v[0]+":"+v[1];
                getPreferences(0).edit().putString("last_player_name",playerName()).putString("last_server_url",address).apply();
                launchGame(playerName(),address,false,"ONLINE");
            });
        },"official-server-list").start();
    }

    void showAccount() {
        base("CONTA","Use a mesma conta no PC e no Android");
        accountUser=edit("Usuário (3 a 20 caracteres)","",false);
        accountPass=edit("Senha (mínimo 8 caracteres)","",false);
        accountPass.setInputType(129);
        accountNick=edit("Apelido (somente ao registrar)","",false);
        for(EditText field:new EditText[]{accountUser,accountPass,accountNick}) {
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));
            lp.topMargin=dp(7);root.addView(field,lp);
        }
        String saved=getPreferences(0).getString("account_nickname","");
        if(!saved.isEmpty()) {
            TextView current=text("Conectado como "+saved,13,0xff8fe0ad);
            root.addView(current,new LinearLayout.LayoutParams(-1,dp(30)));
        }
        addButton("ENTRAR",true,()->sendAccountAuth(false));
        addButton("CRIAR CONTA",false,()->sendAccountAuth(true));
        addButton("SAIR DA CONTA",false,()->{
            getPreferences(0).edit().remove("account_token").remove("account_nickname").remove("server_owned_skins").putInt("server_coins",0).apply();
            Toast.makeText(this,"Sessão removida deste aparelho.",Toast.LENGTH_SHORT).show();showAccount();
        });
        if(!getPreferences(0).getString("account_token","").isEmpty())
            addButton("ALTERAR NICKNAME (100 MOEDAS)",false,()->changeNicknameRemote());
        addButton("DELETAR CONTA",false,()->confirmDeleteAccount());
        addButton("VOLTAR",false,()->showHome());
    }

    void changeNicknameRemote() {
        if(getPreferences(0).getString("account_token","").isEmpty()) {
            Toast.makeText(this,"Entre na conta antes de alterar o nickname.",Toast.LENGTH_LONG).show();return;
        }
        String url=getPreferences(0).getString("last_server_url","");
        if(!url.startsWith("wss://")){ensureOfficialAccountServer(()->changeNicknameRemote());return;}
        EditText input=edit("Novo nickname (até 24 caracteres)",getPreferences(0).getString("account_nickname",""),false);
        new AlertDialog.Builder(this).setTitle("Alterar nickname")
            .setMessage("A alteração custa 100 moedas.")
            .setView(input).setNegativeButton("Cancelar",null)
            .setPositiveButton("ALTERAR",(dialog,which)->sendNicknameChange(input.getText().toString().trim()))
            .show();
    }

    void sendNicknameChange(String nickname) {
        if(nickname.isEmpty()||nickname.codePointCount(0,nickname.length())>24) {
            Toast.makeText(this,"Use um nickname de 1 a 24 caracteres.",Toast.LENGTH_LONG).show();return;
        }
        String url=getPreferences(0).getString("last_server_url","");
        String token=getPreferences(0).getString("account_token","");
        if(!url.startsWith("wss://")||token.isEmpty())return;
        if(shopSocket!=null)shopSocket.close();
        final boolean[] sent={false};
        shopSocket=new NativeSocket(url,raw->{
            try {
                JSONObject response=new JSONObject(raw);
                String type=response.optString("type");
                if(type.equals("auth_resumed")&&!sent[0]) {
                    sent[0]=true;
                    JSONObject request=new JSONObject();request.put("type","auth_nickname_change");request.put("nickname",nickname);
                    shopSocket.send(request.toString());
                } else if(type.equals("nickname_result")) {
                    boolean accepted=response.optBoolean("accepted",false);
                    JSONObject profile=response.optJSONObject("profile");
                    if(profile!=null)saveAccountProfile(profile);
                    String code=response.optString("code","");
                    runOnUiThread(()->{
                        if(accepted)Toast.makeText(this,"Nickname alterado! 100 moedas cobradas.",Toast.LENGTH_LONG).show();
                        else {
                            String message=code.equals("INSUFFICIENT_COINS")?"Você precisa de 100 moedas.":
                                code.equals("NICKNAME_TAKEN")?"Esse nickname já está em uso.":
                                code.equals("SAME_NICKNAME")?"Esse já é seu nickname atual.":
                                code.equals("INVALID_NICKNAME")?"Nickname inválido.":"Não foi possível alterar: "+code;
                            Toast.makeText(this,message,Toast.LENGTH_LONG).show();
                        }
                        if(shopSocket!=null){shopSocket.close();shopSocket=null;}
                        showAccount();
                    });
                } else if(type.equals("auth_error")||type.equals("account_error")) {
                    String code=response.optString("code","ACCOUNT_ERROR");
                    runOnUiThread(()->{
                        Toast.makeText(this,"Conta: "+code,Toast.LENGTH_LONG).show();
                        if(shopSocket!=null){shopSocket.close();shopSocket=null;}
                    });
                }
            } catch(Exception ignored) {}
        });
        shopSocket.start();
        try { JSONObject auth=new JSONObject();auth.put("type","auth_resume");auth.put("token",token);shopSocket.send(auth.toString()); }
        catch(Exception e) { shopSocket.close();shopSocket=null;Toast.makeText(this,"Erro ao conectar à conta.",Toast.LENGTH_LONG).show(); }
    }

    void confirmDeleteAccount() {
        if(getPreferences(0).getString("account_token","").isEmpty()){Toast.makeText(this,"Entre na conta antes de excluí-la.",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(this).setTitle("Excluir conta?").setMessage("A conta será excluída permanentemente. Deseja continuar?")
            .setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->new AlertDialog.Builder(this)
            .setTitle("Última confirmação").setMessage("Todas as moedas, skins e dados serão perdidos. Esta ação não pode ser desfeita.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d2,w2)->askDeletePassword()).show()).show();
    }
    void askDeletePassword(){
        EditText password=edit("Senha da conta","",false);password.setInputType(129);
        new AlertDialog.Builder(this).setTitle("Confirme sua senha").setMessage("Digite a senha da conta para concluir a exclusão.")
            .setView(password).setNegativeButton("Cancelar",null).setPositiveButton("Confirmar",(d,w)->{String value=password.getText().toString();if(value.isEmpty()){Toast.makeText(this,"Digite sua senha.",Toast.LENGTH_SHORT).show();return;}deleteAccountRemote(value);}).show();
    }
    void deleteAccountRemote(String password){
        String url=getPreferences(0).getString("last_server_url",""),token=getPreferences(0).getString("account_token","");
        if(!url.startsWith("wss://")){ensureOfficialAccountServer(()->deleteAccountRemote(password));return;}
        if(token.isEmpty()){Toast.makeText(this,"Entre na conta antes de excluí-la.",Toast.LENGTH_LONG).show();return;}
        if(shopSocket!=null)shopSocket.close();final boolean[] sent={false};
        shopSocket=new NativeSocket(url,raw->{try{JSONObject response=new JSONObject(raw);String type=response.optString("type");
            if(type.equals("auth_resumed")&&!sent[0]){sent[0]=true;JSONObject request=new JSONObject();request.put("type","auth_delete");request.put("password",password);shopSocket.send(request.toString());}
            else if(type.equals("account_deleted"))runOnUiThread(()->{getPreferences(0).edit().remove("account_token").remove("account_nickname").remove("server_owned_skins").remove("server_equipped_skin").putInt("server_coins",0).apply();Toast.makeText(this,"Conta excluída permanentemente.",Toast.LENGTH_LONG).show();if(shopSocket!=null){shopSocket.close();shopSocket=null;}showAccount();});
            else if(type.equals("auth_error")||type.equals("account_error")){String code=response.optString("code","DELETE_FAILED");runOnUiThread(()->Toast.makeText(this,"Não foi possível excluir: "+code,Toast.LENGTH_LONG).show());if(shopSocket!=null){shopSocket.close();shopSocket=null;}}
        }catch(Exception ignored){}});
        shopSocket.start();try{JSONObject auth=new JSONObject();auth.put("type","auth_resume");auth.put("token",token);shopSocket.send(auth.toString());}catch(Exception e){shopSocket.close();shopSocket=null;}
    }

    void ensureOfficialAccountServer(Runnable ready) {
        if(resolvingAccountServer)return;
        resolvingAccountServer=true;
        Toast.makeText(this,"Localizando servidor oficial seguro...",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            String selected=null; String error=null; HttpURLConnection connection=null;
            try {
                connection=(HttpURLConnection)new URL(SERVER_API).openConnection();
                connection.setConnectTimeout(12000);connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept","application/json");
                int status=connection.getResponseCode();
                if(status!=200)throw new IOException("HTTP "+status);
                StringBuilder body=new StringBuilder();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),"UTF-8"))){
                    String line;while((line=reader.readLine())!=null)body.append(line);
                }
                JSONObject json=new JSONObject(body.toString());
                JSONArray servers=json.optJSONArray("servers");
                if(servers!=null)for(int i=0;i<servers.length();i++){
                    JSONObject server=servers.optJSONObject(i);if(server==null)continue;
                    String host=server.optString("host","").trim();int port=server.optInt("port",3000);
                    boolean secure=server.optBoolean("secure",false)||port==443;
                    if(secure&&!host.isEmpty()&&!host.contains("/")&&!host.contains(":")&&port>0&&port<=65535){
                        selected="wss://"+host+":"+port;break;
                    }
                }
                if(selected==null)throw new IOException("Nenhum servidor WSS disponível.");
            }catch(Exception e){error=e.getMessage();}
            finally{if(connection!=null)connection.disconnect();}
            final String address=selected,problem=error;
            runOnUiThread(()->{
                resolvingAccountServer=false;
                if(address==null){Toast.makeText(this,"Não foi possível localizar o servidor oficial: "+problem,Toast.LENGTH_LONG).show();return;}
                getPreferences(0).edit().putString("last_server_url",address).apply();
                ready.run();
            });
        },"official-account-server").start();
    }

    void sendAccountAuth(boolean register) {
        String url=getPreferences(0).getString("last_server_url","");
        if(!url.startsWith("wss://")) {
            ensureOfficialAccountServer(()->sendAccountAuth(register));return;
        }
        String username=accountUser.getText().toString().trim();
        String password=accountPass.getText().toString();
        String nickname=accountNick.getText().toString().trim();
        if(username.isEmpty()||password.isEmpty()||(register&&nickname.isEmpty())) {
            Toast.makeText(this,"Preencha os campos necessários.",Toast.LENGTH_SHORT).show();return;
        }
        if(shopSocket!=null)shopSocket.close();
        shopSocket=new NativeSocket(url,raw->{
            try {
                JSONObject response=new JSONObject(raw);
                String type=response.optString("type");
                if(type.equals("auth_logged_in")||type.equals("auth_registered")) {
                    String token=response.optString("token","");
                    JSONObject profile=response.optJSONObject("profile");
                    if(token.isEmpty()||profile==null)throw new Exception("Resposta incompleta do servidor");
                    saveAccountProfile(profile);
                    getPreferences(0).edit().putString("account_token",token).apply();
                    String recovery=response.optString("recoveryCode","");
                    runOnUiThread(()->{
                        if(!recovery.isEmpty())new AlertDialog.Builder(this).setTitle("Código de recuperação")
                            .setMessage("Anote e guarde este código em local seguro. Ele será mostrado somente agora:\\n\\n"+recovery)
                            .setPositiveButton("OK",null).show();
                        else Toast.makeText(this,"Login realizado! Conta sincronizada.",Toast.LENGTH_LONG).show();
                        if(shopSocket!=null){shopSocket.close();shopSocket=null;}
                        showAccount();
                    });
                } else if(type.equals("auth_error")) {
                    String code=response.optString("code","AUTH_FAILED");
                    runOnUiThread(()->Toast.makeText(this,"Não foi possível entrar: "+code,Toast.LENGTH_LONG).show());
                    if(shopSocket!=null){shopSocket.close();shopSocket=null;}
                }
            } catch(Exception ignored) {}
        });
        shopSocket.start();
        try {
            JSONObject request=new JSONObject();request.put("type",register?"auth_register":"auth_login");
            request.put("username",username);request.put("password",password);
            if(register)request.put("nickname",nickname);
            shopSocket.send(request.toString());
        } catch(Exception e) { Toast.makeText(this,"Erro ao preparar solicitação.",Toast.LENGTH_SHORT).show(); }
    }

    void saveAccountProfile(JSONObject profile) {
        JSONArray owned=profile.optJSONArray("ownedSkins");
        StringBuilder ids=new StringBuilder();
        if(owned!=null)for(int i=0;i<owned.length();i++){if(ids.length()>0)ids.append('|');ids.append(owned.optString(i));}
        getPreferences(0).edit()
            .putInt("server_coins",profile.optInt("coins",0))
            .putString("server_owned_skins",ids.toString())
            .putString("server_equipped_skin",profile.optString("equippedSkin","bright_green"))
            .putString("account_nickname",profile.optString("nickname",""))
            .putString("last_player_name",profile.optString("nickname",""))
            .putInt("skin_high_score",profile.optInt("highScore",0))
            .apply();
    }

    void requestShopAction(String action,String skinId) {
        String url=getPreferences(0).getString("last_server_url","");
        String token=getPreferences(0).getString("account_token","");
        if(!url.startsWith("wss://")||token.isEmpty()) {
            Toast.makeText(this,"Entre em uma conta usando um servidor seguro (WSS).",Toast.LENGTH_LONG).show();return;
        }
        if(shopSocket!=null)shopSocket.close();
        final boolean[] sent={false};
        shopSocket=new NativeSocket(url,raw->{
            try {
                JSONObject response=new JSONObject(raw);
                String type=response.optString("type");
                if(type.equals("auth_resumed")&&!sent[0]) {
                    sent[0]=true;
                    JSONObject request=new JSONObject();request.put("type",action);request.put("skinId",skinId);
                    shopSocket.send(request.toString());
                } else if(type.equals("shop_result")) {
                    if(response.optBoolean("accepted")) {
                        JSONObject profile=response.optJSONObject("profile");
                        if(profile!=null)saveAccountProfile(profile);
                        runOnUiThread(()->{Toast.makeText(this,"Loja sincronizada!",Toast.LENGTH_SHORT).show();renderShop();});
                    } else {
                        String code=response.optString("code","SHOP_FAILED");
                        runOnUiThread(()->Toast.makeText(this,"Não foi possível concluir: "+code,Toast.LENGTH_LONG).show());
                    }
                    if(shopSocket!=null){shopSocket.close();shopSocket=null;}
                } else if(type.equals("auth_error")||type.equals("account_error")) {
                    String code=response.optString("code","ACCOUNT_ERROR");
                    runOnUiThread(()->Toast.makeText(this,"Conta: "+code,Toast.LENGTH_LONG).show());
                    if(shopSocket!=null){shopSocket.close();shopSocket=null;}
                }
            } catch(Exception ignored) {}
        });
        shopSocket.start();
        try { JSONObject auth=new JSONObject();auth.put("type","auth_resume");auth.put("token",token);shopSocket.send(auth.toString()); }
        catch(Exception e) { shopSocket.close();shopSocket=null; }
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
            TextView price=text(owns(id)?"Desbloqueada":(SKIN_COSTS[i]>0?(SKIN_COSTS[i]+" moedas"):( "Pontuação: "+SKIN_UNLOCK_SCORES[i])),11,0xffffd45c);price.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            info.addView(nm);info.addView(price);row.addView(info,new LinearLayout.LayoutParams(0,-1,1));
            boolean owned=owns(id);
            Button action=button(owned?(equippedSkin().equals(id)?"EQUIPADA":"EQUIPAR"):"COMPRAR",owned);
            row.addView(action,new LinearLayout.LayoutParams(dp(112),dp(42)));
            action.setEnabled(!owned || !equippedSkin().equals(id));
            action.setOnClickListener(v->{
                if(owned) {
                    requestShopAction("skin_equip",id);
                } else if(SKIN_COSTS[indexOfSkin(id)]>0) {
                    requestShopAction("shop_buy",id);
                } else {
                    Toast.makeText(this,"Consiga a pontuação necessária em uma partida para desbloquear!",Toast.LENGTH_SHORT).show();
                }
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