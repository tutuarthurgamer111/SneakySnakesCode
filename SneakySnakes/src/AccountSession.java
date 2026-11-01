import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

public final class AccountSession {
    private static final Path TOKEN_FILE = Paths.get(System.getProperty("user.home"), ".sneakysnakes-session");
    private static final Path NICKNAME_FILE = Paths.get(System.getProperty("user.home"), ".sneakysnakes-nickname");
    private static volatile String token = loadToken();
    private static volatile String nickname = loadNickname();
    private static volatile String lastServerUrl = "";
    private AccountSession() {}

    public static String token() { return token == null ? "" : token; }
    public static String nickname() { return nickname == null ? "" : nickname; }
    public static void setNickname(String value) {
        nickname = value == null ? "" : value.trim();
        try {
            if (nickname.isEmpty()) Files.deleteIfExists(NICKNAME_FILE);
            else Files.writeString(NICKNAME_FILE, nickname, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }
    public static void clearToken() {
        token="";
        setNickname("");
        try { Files.deleteIfExists(TOKEN_FILE); } catch(Exception ignored) {}
    }

    private static String loadToken() {
        try { return Files.exists(TOKEN_FILE) ? Files.readString(TOKEN_FILE, StandardCharsets.UTF_8).trim() : ""; }
        catch (Exception ignored) { return ""; }
    }
    private static String loadNickname() {
        try { return Files.exists(NICKNAME_FILE) ? Files.readString(NICKNAME_FILE, StandardCharsets.UTF_8).trim() : ""; }
        catch (Exception ignored) { return ""; }
    }

    private static void saveToken(String value) {
        try {
            Files.writeString(TOKEN_FILE, value, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            TOKEN_FILE.toFile().setReadable(false, false);
            TOKEN_FILE.toFile().setWritable(false, false);
            TOKEN_FILE.toFile().setReadable(true, true);
            TOKEN_FILE.toFile().setWritable(true, true);
            token = value;
        } catch (Exception e) {
            throw new IllegalStateException("Could not save account session", e);
        }
    }

    private static String jsonEscape(String value) {
        return value.replace("\", "\").replace("\"", "\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String jsonString(String json, String key) {
        Matcher m=Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        return m.find()?m.group(1).replace("\\n","\n").replace("\\"","\"").replace("\","\"):null;
    }

    public static boolean syncShopAction(String action, String skinId, SaveSystem save) {
        String currentToken=token();
        if(currentToken.isEmpty() || !lastServerUrl.startsWith("wss://")) return false;
        CountDownLatch done=new CountDownLatch(1);
        boolean[] success={false};
        final WebSocketClient[] client=new WebSocketClient[1];
        client[0]=new WebSocketClient(lastServerUrl, message -> {
            String type=jsonString(message,"type");
            if("auth_resumed".equals(type)) {
                client[0].send("{\"type\":\""+action+"\",\"skinId\":\""+jsonEscape(skinId)+"\"}");
            } else if("shop_result".equals(type)) {
                success[0]=message.contains("\"accepted\":true");
                String profile=extractObject(message,"profile");
                if(profile!=null) {
                    String receivedNickname=jsonString(profile,"nickname");
                    if(receivedNickname!=null&&!receivedNickname.trim().isEmpty()) setNickname(receivedNickname);
                }
                if(success[0] && profile!=null && save!=null) {
                    int coins=number(profile,"coins",-1);
                    if(coins>=0) save.coins=coins;
                    String equipped=jsonString(profile,"equippedSkin");
                    if(equipped!=null) {
                        save.currentSkinId=equipped;
                        SkinSystem.setById(equipped);
                    }
                    String owned=stringArray(profile,"ownedSkins");
                    if(owned!=null) {
                        save.unlockedSkins=owned;
                        SkinSystem.deserializeUnlocked(owned);
                    }
                    save.save();
                }
                done.countDown();
            } else if("auth_error".equals(type) || "account_error".equals(type)) {
                if("INVALID_SESSION".equals(jsonString(message,"code"))) clearToken();
                done.countDown();
            }
        }, () -> client[0].send("{\"type\":\"auth_resume\",\"token\":\""+jsonEscape(currentToken)+"\"}"), () -> done.countDown());
        new Thread(client[0],"sneakysnakes-shop-sync").start();
        try {
            if(!done.await(12,TimeUnit.SECONDS)) success[0]=false;
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            success[0]=false;
        } finally {
            client[0].close();
        }
        return success[0];
    }

    private static String extractObject(String json,String key) {
        int i=json.indexOf("\""+key+"\"");
        if(i<0)return null;
        int start=json.indexOf('{',i);
        if(start<0)return null;
        int depth=0;
        boolean quoted=false,escape=false;
        for(int p=start;p<json.length();p++) {
            char c=json.charAt(p);
            if(quoted) {
                if(escape)escape=false;
                else if(c=='\\')escape=true;
                else if(c=='"')quoted=false;
            } else if(c=='"')quoted=true;
            else if(c=='{')depth++;
            else if(c=='}'&&--depth==0)return json.substring(start,p+1);
        }
        return null;
    }

    private static int number(String json,String key,int fallback) {
        Matcher m=Pattern.compile("\""+Pattern.quote(key)+"\"\\s*:\\s*(-?\\d+)").matcher(json);
        try { return m.find()?Integer.parseInt(m.group(1)):fallback; }
        catch(Exception e) { return fallback; }
    }

    private static String stringArray(String json,String key) {
        int i=json.indexOf("\""+key+"\"");
        if(i<0)return null;
        int a=json.indexOf('[',i),b=json.indexOf(']',a);
        if(a<0||b<0)return null;
        Matcher m=Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json.substring(a+1,b));
        StringBuilder out=new StringBuilder();
        while(m.find()) {
            if(out.length()>0)out.append('|');
            out.append(m.group(1));
        }
        return out.toString();
    }

    public static boolean ensureLogin(Component parent, String serverUrl) {
        lastServerUrl=serverUrl;
        if (!token().isEmpty()) return true;
        Object[] options={"Login","Create account","Play as guest","Cancel"};
        int choice=JOptionPane.showOptionDialog(parent,"Sign in to sync coins and skins between PC and Android.","SneakySnakes Account",
            JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,options,options[0]);
        if(choice==2)return true;
        if(choice<0||choice==3)return false;
        if(!serverUrl.startsWith("wss://")) {
            JOptionPane.showMessageDialog(parent,"Accounts require a secure WSS server. Guest mode is still available.","Secure connection required",JOptionPane.WARNING_MESSAGE);
            return false;
        }
        JTextField username=new JTextField(18), nickname=new JTextField(18);
        JPasswordField password=new JPasswordField(18);
        JPanel form=new JPanel(new GridLayout(choice==1?3:2,2,6,6));
        form.add(new JLabel("Username:"));form.add(username);
        form.add(new JLabel("Password:"));form.add(password);
        if(choice==1){form.add(new JLabel("Nickname:"));form.add(nickname);}
        if(JOptionPane.showConfirmDialog(parent,form,choice==1?"Create account":"Login",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return false;
        String user=username.getText().trim(), pass=new String(password.getPassword()), nick=nickname.getText().trim();
        if(user.isEmpty()||pass.isEmpty()||(choice==1&&nick.isEmpty()))return false;

        CountDownLatch done=new CountDownLatch(1);
        String[] error={null}, recovery={null};
        String type=choice==1?"auth_register":"auth_login";
        final WebSocketClient[] client=new WebSocketClient[1];
        client[0]=new WebSocketClient(serverUrl, message->{
            String responseType=jsonString(message,"type");
            if("auth_logged_in".equals(responseType)||"auth_registered".equals(responseType)) {
                String received=jsonString(message,"token");
                if(received==null||received.isEmpty())error[0]="INVALID_SERVER_RESPONSE";
                else {
                    try { saveToken(received); } catch(Exception e){error[0]="TOKEN_SAVE_FAILED";}
                    recovery[0]=jsonString(message,"recoveryCode");
                    String receivedNickname=jsonString(message,"nickname");
                    if(receivedNickname==null) {
                        String profile=extractObject(message,"profile");
                        if(profile!=null) receivedNickname=jsonString(profile,"nickname");
                    }
                    if(receivedNickname!=null&&!receivedNickname.trim().isEmpty()) setNickname(receivedNickname);
                }
                done.countDown();
            } else if("auth_error".equals(responseType)) {
                error[0]=jsonString(message,"code");done.countDown();
            }
        },()->{
            String payload="{\"type\":\""+type+"\",\"username\":\""+jsonEscape(user)+"\",\"password\":\""+jsonEscape(pass)+"\""+
                (choice==1?",\"nickname\":\""+jsonEscape(nick)+"\"":"")+"}";
            client[0].send(payload);
        },()->{});
        new Thread(client[0],"sneakysnakes-account-auth").start();
        try {
            if(!done.await(15,TimeUnit.SECONDS))error[0]="CONNECTION_TIMEOUT";
        } catch(InterruptedException e){Thread.currentThread().interrupt();error[0]="INTERRUPTED";}
        finally {client[0].close();}
        if(error[0]!=null) {
            JOptionPane.showMessageDialog(parent,"Could not sign in: "+error[0],"Account",JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if(recovery[0]!=null&&!recovery[0].isEmpty())
            JOptionPane.showMessageDialog(parent,"Save this recovery code now. It is shown only once:\n\n"+recovery[0],"Account recovery code",JOptionPane.WARNING_MESSAGE);
        else JOptionPane.showMessageDialog(parent,"Account signed in. Your profile will sync when the game connects.","Account",JOptionPane.INFORMATION_MESSAGE);
        return true;
    }

    public static boolean changeNickname(Component parent, String serverUrl, String requestedNickname, SaveSystem save) {
        String currentToken = token();
        String desired = requestedNickname == null ? "" : requestedNickname.trim();
        if (currentToken.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Entre na sua conta antes de alterar o nickname.",
                "Conta necessária", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (serverUrl == null || !serverUrl.startsWith("wss://")) {
            JOptionPane.showMessageDialog(parent, "A alteração exige uma conexão segura (WSS).",
                "Conexão segura necessária", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (desired.isEmpty() || desired.codePointCount(0, desired.length()) > 24) {
            JOptionPane.showMessageDialog(parent, "O nickname deve ter entre 1 e 24 caracteres.",
                "Nickname inválido", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        CountDownLatch done = new CountDownLatch(1);
        boolean[] success = {false};
        String[] error = {null};
        final WebSocketClient[] client = new WebSocketClient[1];
        client[0] = new WebSocketClient(serverUrl, message -> {
            String type = jsonString(message, "type");
            if ("auth_resumed".equals(type)) {
                client[0].send("{\"type\":\"auth_nickname_change\",\"nickname\":\""+jsonEscape(desired)+"\"}");
            } else if ("nickname_result".equals(type)) {
                success[0] = message.contains("\"accepted\":true");
                error[0] = jsonString(message, "code");
                String profile = extractObject(message, "profile");
                if (profile != null) {
                    String receivedNickname = jsonString(profile, "nickname");
                    if (receivedNickname != null && !receivedNickname.trim().isEmpty()) setNickname(receivedNickname);
                    int balance = number(profile, "coins", -1);
                    if (balance >= 0 && save != null) { save.coins = balance; save.save(); }
                }
                if (success[0]) {
                    String receivedNickname = profile == null ? desired : jsonString(profile, "nickname");
                    setNickname(receivedNickname == null ? desired : receivedNickname);
                }
                done.countDown();
            } else if ("auth_error".equals(type) || "account_error".equals(type)) {
                error[0] = jsonString(message, "code");
                if ("INVALID_SESSION".equals(error[0])) clearToken();
                done.countDown();
            }
        }, () -> client[0].send("{\"type\":\"auth_resume\",\"token\":\""+jsonEscape(currentToken)+"\"}"),
        () -> { error[0] = "SERVER_DISCONNECTED"; done.countDown(); });

        new Thread(client[0], "sneakysnakes-nickname-change").start();
        try {
            if (!done.await(15, TimeUnit.SECONDS)) error[0] = "CONNECTION_TIMEOUT";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error[0] = "INTERRUPTED";
        } finally {
            client[0].close();
        }

        if (success[0]) {
            JOptionPane.showMessageDialog(parent, "Nickname alterado! Foram cobradas 100 moedas.",
                "Conta SneakySnakes", JOptionPane.INFORMATION_MESSAGE);
            return true;
        }
        String reason = error[0] == null ? "SERVER_DISCONNECTED" : error[0];
        String message;
        switch (reason) {
            case "INSUFFICIENT_COINS": message = "Você precisa de 100 moedas para alterar o nickname."; break;
            case "NICKNAME_TAKEN": message = "Esse nickname já está sendo usado."; break;
            case "SAME_NICKNAME": message = "Esse já é o seu nickname atual."; break;
            case "INVALID_NICKNAME": message = "Nickname inválido. Use até 24 caracteres."; break;
            case "LOGIN_REQUIRED": message = "Entre na sua conta para alterar o nickname."; break;
            default: message = "Não foi possível alterar o nickname: " + reason; break;
        }
        JOptionPane.showMessageDialog(parent, message, "Não foi possível alterar", JOptionPane.WARNING_MESSAGE);
        return false;
    }

    public static void manageAccount(Component parent, String serverUrl) { manageAccount(parent, serverUrl, null); }

    public static void manageAccount(Component parent, String serverUrl, SaveSystem save) {
        lastServerUrl = serverUrl == null ? "" : serverUrl.trim();
        if (!lastServerUrl.startsWith("wss://")) {
            JOptionPane.showMessageDialog(parent, "A conta precisa de um servidor seguro (WSS).",
                "Conexão segura necessária", JOptionPane.WARNING_MESSAGE); return;
        }
        if (token().isEmpty()) { ensureLogin(parent, lastServerUrl); return; }
        Object[] options = {"Alterar nickname (100 moedas)", "Sair da conta", "Excluir conta", "Cancelar"};
        int choice = JOptionPane.showOptionDialog(parent, "Conta conectada. O que deseja fazer?",
            "Conta SneakySnakes", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[3]);
        if (choice == 0) {
            String requested = JOptionPane.showInputDialog(parent, "Novo nickname (custa 100 moedas):",
                nickname().isEmpty() ? "Alterar nickname" : nickname());
            if (requested != null) changeNickname(parent, lastServerUrl, requested, save);
            return;
        }
        if (choice == 1) { clearToken(); JOptionPane.showMessageDialog(parent, "Você saiu da conta neste computador."); return; }
        if (choice != 2) return;
        if (JOptionPane.showConfirmDialog(parent, "A conta será excluída permanentemente. Deseja continuar?",
            "Primeira confirmação", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        if (JOptionPane.showConfirmDialog(parent, "Todos os dados, moedas e skins desta conta serão perdidos. Tem certeza?",
            "Última confirmação", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) != JOptionPane.YES_OPTION) return;
        JPasswordField password = new JPasswordField(18);
        if (JOptionPane.showConfirmDialog(parent, password, "Digite a senha da conta para confirmar",
            JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String pass = new String(password.getPassword());
        if (pass.isEmpty()) { JOptionPane.showMessageDialog(parent, "A senha é obrigatória."); return; }
        if (deleteRemoteAccount(lastServerUrl, pass)) { clearToken(); JOptionPane.showMessageDialog(parent, "Conta excluída permanentemente."); }
    }
    private static boolean deleteRemoteAccount(String serverUrl, String password) {
        String currentToken = token(); if (currentToken.isEmpty()) return false;
        CountDownLatch done = new CountDownLatch(1); boolean[] success = {false}; String[] error = {null};
        final WebSocketClient[] client = new WebSocketClient[1];
        client[0] = new WebSocketClient(serverUrl, message -> {
            String type = jsonString(message, "type");
            if ("auth_resumed".equals(type)) client[0].send("{\"type\":\"auth_delete\",\"password\":\""+jsonEscape(password)+"\"}");
            else if ("account_deleted".equals(type)) { success[0] = true; done.countDown(); }
            else if ("auth_error".equals(type) || "account_error".equals(type)) { error[0] = jsonString(message, "code"); done.countDown(); }
        }, () -> client[0].send("{\"type\":\"auth_resume\",\"token\":\""+jsonEscape(currentToken)+"\"}"), () -> done.countDown());
        new Thread(client[0], "sneakysnakes-account-delete").start();
        try { if (!done.await(15, TimeUnit.SECONDS)) error[0] = "CONNECTION_TIMEOUT"; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); error[0] = "INTERRUPTED"; }
        finally { client[0].close(); }
        if (!success[0]) JOptionPane.showMessageDialog(null, "Não foi possível excluir a conta: "+
            (error[0] == null ? "SERVER_DISCONNECTED" : error[0]), "Erro ao excluir conta", JOptionPane.ERROR_MESSAGE);
        return success[0];
    }
}
