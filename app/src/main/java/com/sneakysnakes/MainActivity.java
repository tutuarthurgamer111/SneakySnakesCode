package com.sneakysnakes;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    NativeGameView game;
    EditText name, host, port;
    Switch secure;
    TextView info;
    static final String SERVER_API = "https://sneakysnakes-server-list.vercel.app/api/servers";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        NativeAudio.init(getAssets());
        menu();
        NativeAudio.menuMusic();
    }

    TextView label(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextColor(Color.WHITE); t.setTextSize(size);
        t.setGravity(Gravity.CENTER); return t;
    }

    Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(12, 4, 12, 4);
        b.setMinHeight(44);
        b.setMinimumHeight(44);
        b.setIncludeFontPadding(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xff18334d);
        bg.setCornerRadius(12 * getResources().getDisplayMetrics().density);
        bg.setStroke((int)(1 * getResources().getDisplayMetrics().density), 0xff3c7298);
        b.setBackground(bg);
        b.setOnTouchListener((view,event) -> { if(event.getAction()==MotionEvent.ACTION_DOWN) NativeAudio.play("click"); return false; });
        return b;
    }

    void addMenuButton(LinearLayout parent, Button b, int heightDp) {
        int d = (int)(getResources().getDisplayMetrics().density + 0.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, heightDp * d);
        lp.setMargins(0, 5 * d, 0, 5 * d);
        parent.addView(b, lp);
    }

    void menu() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        root.setPadding(24, 8, 24, 8); root.setBackgroundColor(0xff080e18);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL); content.setGravity(Gravity.CENTER);
        scroll.addView(content);
        content.addView(label("SNEAKYSNAKES", 30), new LinearLayout.LayoutParams(-1, 58));
        name = new EditText(this); name.setSingleLine(); name.setText("Player");
        name.setHint("Seu nome"); name.setTextColor(Color.WHITE); name.setHintTextColor(0xff9aaabd);
        content.addView(name, new LinearLayout.LayoutParams(-1, 48));

        content.addView(label("SERVIDOR", 14), new LinearLayout.LayoutParams(-1, 34));
        host = new EditText(this); host.setSingleLine(); host.setText("163.176.148.122");
        host.setHint("Host ou IP"); host.setTextColor(Color.WHITE); host.setHintTextColor(0xff9aaabd);
        content.addView(host, new LinearLayout.LayoutParams(-1, 48));
        port = new EditText(this); port.setSingleLine(); port.setInputType(2); port.setText("3000");
        port.setHint("Porta"); port.setTextColor(Color.WHITE); port.setHintTextColor(0xff9aaabd);
        content.addView(port, new LinearLayout.LayoutParams(-1, 48));
        secure = new Switch(this); secure.setText("Conexão segura (wss://)");
        secure.setTextColor(Color.WHITE); secure.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(secure, new LinearLayout.LayoutParams(-1, 42));

        Button random = button("🎲 Entrar em servidor oficial aleatório");
        addMenuButton(content, random, 54);
        random.setOnClickListener(v -> randomOfficialServer(random));
        Button connect = button("CONECTAR ONLINE");
        addMenuButton(content, connect, 54);
        connect.setOnClickListener(v -> startOnline());

        content.addView(label("MODOS OFFLINE", 17), new LinearLayout.LayoutParams(-1, 42));
        Button classic = button("CLASSIC MODE");
        Button party = button("PARTY MODE");
        Button infinite = button("INFINITE MODE");
        addMenuButton(content, classic, 52);
        addMenuButton(content, party, 52);
        addMenuButton(content, infinite, 52);
        classic.setOnClickListener(v -> startOffline("CLASSIC"));
        party.setOnClickListener(v -> startOffline("PARTY"));
        infinite.setOnClickListener(v -> startOffline("INFINITE"));

        Button sound = button(NativeAudio.isEnabled() ? "🔊 Sons: ligados" : "🔇 Sons: desligados");
        addMenuButton(content, sound, 50);
        sound.setOnClickListener(v -> {
            NativeAudio.setEnabled(!NativeAudio.isEnabled());
            sound.setText(NativeAudio.isEnabled() ? "🔊 Sons: ligados" : "🔇 Sons: desligados");
            if (NativeAudio.isEnabled()) NativeAudio.menuMusic();
        });
        info = label("Android nativo • Canvas + WebSocket", 12);
        content.addView(info, new LinearLayout.LayoutParams(-1, 34));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    String playerName() {
        String n = name == null ? "Player" : name.getText().toString().trim();
        return n.isEmpty() ? "Player" : n;
    }

    void startOnline() {
        String h = host.getText().toString().trim();
        String p = port.getText().toString().trim();
        if (h.startsWith("ws://")) h = h.substring(5);
        else if (h.startsWith("wss://")) h = h.substring(6);
        if (h.contains("/")) h = h.substring(0, h.indexOf('/'));
        if (h.contains(":")) {
            String[] bits = h.split(":", 2);
            h = bits[0];
            if (p.isEmpty() || p.equals("3000")) p = bits[1];
        }
        int pn;
        try { pn = Integer.parseInt(p); } catch (Exception e) { pn = -1; }
        if (h.isEmpty() || h.contains(" ") || pn < 1 || pn > 65535) {
            Toast.makeText(this, "Confira o host e a porta (1–65535).", Toast.LENGTH_LONG).show();
            return;
        }
        String address = (secure.isChecked() ? "wss://" : "ws://") + h + ":" + pn;
        launchGame(playerName(), address, false, "ONLINE");
    }

    void randomOfficialServer(Button b) {
        b.setEnabled(false); b.setText("Buscando servidores...");
        new Thread(() -> {
            String selected = null; int count = 0; String error = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(SERVER_API).openConnection();
                c.setRequestMethod("GET"); c.setConnectTimeout(10000); c.setReadTimeout(15000);
                c.setRequestProperty("Accept", "application/json");
                StringBuilder body = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line; while ((line = r.readLine()) != null) body.append(line);
                }
                JSONObject json = new JSONObject(body.toString());
                JSONArray servers = json.optJSONArray("servers");
                if (servers != null) {
                    ArrayList<String> valid = new ArrayList<>();
                    for (int i = 0; i < servers.length(); i++) {
                        JSONObject s = servers.optJSONObject(i);
                        if (s == null) continue;
                        String sh = s.optString("host", "").trim();
                        int sp = s.optInt("port", -1);
                        if (!sh.isEmpty() && !sh.contains("/") && !sh.contains(":") && sp > 0 && sp <= 65535)
                            valid.add(sh + "|" + sp);
                    }
                    count = valid.size();
                    if (!valid.isEmpty()) selected = valid.get(new Random().nextInt(valid.size()));
                }
            } catch (Exception e) { error = e.getMessage(); }
            finally { if (c != null) c.disconnect(); }
            final String chosen = selected; final int found = count; final String problem = error;
            runOnUiThread(() -> {
                b.setEnabled(true); b.setText("🎲 Entrar em servidor oficial aleatório");
                if (chosen == null) {
                    Toast.makeText(this, problem == null ? "Nenhum servidor oficial disponível." : "Falha ao buscar servidores: " + problem, Toast.LENGTH_LONG).show();
                    return;
                }
                String[] parts = chosen.split("\\|", 2);
                host.setText(parts[0]); port.setText(parts[1]); secure.setChecked(false);
                Toast.makeText(this, "Servidor escolhido entre " + found + " oficial(is). Conectando...", Toast.LENGTH_SHORT).show();
                startOnline();
            });
        }, "official-server-list").start();
    }

    void startOffline(String mode) { launchGame(playerName(), "", true, mode); }

    void launchGame(String n, String address, boolean offline, String mode) {
        NativeAudio.stopMusic();
        game = new NativeGameView(this, n, address, offline, mode);
        setContentView(game); game.loop();
    }

    @Override public void onBackPressed() {
        if (game != null) { game.close(); game = null; NativeAudio.menuMusic(); menu(); }
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (game != null) game.close();
        NativeAudio.release(); super.onDestroy();
    }
}