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
    private static volatile String token = loadToken();
    private AccountSession() {}

    public static String token() { return token == null ? "" : token; }
    public static void clearToken() {
        token="";
        try { Files.deleteIfExists(TOKEN_FILE); } catch(Exception ignored) {}
    }

    private static String loadToken() {
        try { return Files.exists(TOKEN_FILE) ? Files.readString(TOKEN_FILE, StandardCharsets.UTF_8).trim() : ""; }
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
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String jsonString(String json, String key) {
        Matcher m=Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        return m.find()?m.group(1).replace("\\n","\n").replace("\\\"","\"").replace("\\\\","\\"):null;
    }

    public static boolean ensureLogin(Component parent, String serverUrl) {
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
}
