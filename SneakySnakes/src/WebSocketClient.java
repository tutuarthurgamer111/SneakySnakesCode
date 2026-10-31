import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javax.net.ssl.*;

/**
 * Minimal WebSocket client (RFC 6455) — no external dependencies.
 * Handles ws:// and wss:// (TLS), text frame send/receive, ping/pong, and close.
 */
public class WebSocketClient implements Runnable {

    private final String url;
    private final Consumer<String>  onMessage;
    private final Runnable          onConnected;
    private final Runnable          onDisconnected;

    private Socket socket;
    private OutputStream out;
    private InputStream  in;

    private volatile boolean running = false;
    private final BlockingQueue<String> sendQueue = new LinkedBlockingQueue<>();

    public WebSocketClient(String url, Consumer<String> onMessage,
                           Runnable onConnected, Runnable onDisconnected) {
        this.url            = url;
        this.onMessage      = onMessage;
        this.onConnected    = onConnected;
        this.onDisconnected = onDisconnected;
    }

    @Override
    public void run() {
        try {
            // Detecta protocolo: ws:// ou wss://
            boolean useTLS = false;
            String rest = url;
            if (rest.startsWith("wss://")) {
                useTLS = true;
                rest = rest.substring(6);
            } else if (rest.startsWith("ws://")) {
                rest = rest.substring(5);
            }

            // Porta padrão depende do protocolo
            String host;
            int port = useTLS ? 443 : 80;
            String path = "/";

            int slashIdx = rest.indexOf('/');
            if (slashIdx >= 0) { path = rest.substring(slashIdx); rest = rest.substring(0, slashIdx); }

            int colonIdx = rest.indexOf(':');
            if (colonIdx >= 0) { port = Integer.parseInt(rest.substring(colonIdx + 1)); host = rest.substring(0, colonIdx); }
            else { host = rest; }

            // Cria socket normal ou SSL dependendo do protocolo
            if (useTLS) {
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) factory.createSocket(host, port);
                sslSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
                sslSocket.startHandshake();
                socket = sslSocket;
            } else {
                socket = new Socket(host, port);
            }

            socket.setTcpNoDelay(true);
            socket.setSoTimeout(0);
            out = socket.getOutputStream();
            in  = socket.getInputStream();

            // WebSocket handshake
            String key = Base64.getEncoder().encodeToString(generateKey());
            // No wss sem porta explícita, omite porta no Host header
            String hostHeader = (useTLS && port == 443) || (!useTLS && port == 80)
                ? host
                : host + ":" + port;
            String handshake =
                "GET " + path + " HTTP/1.1\r\n" +
                "Host: " + hostHeader + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + key + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n";
            out.write(handshake.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Lê resposta HTTP
            StringBuilder resp = new StringBuilder();
            int b, prev = 0;
            while ((b = in.read()) != -1) {
                resp.append((char) b);
                if (prev == '\r' && b == '\n' && resp.length() > 4) {
                    String tail = resp.substring(resp.length() - 4);
                    if (tail.equals("\r\n\r\n")) break;
                }
                prev = b;
            }
            System.out.println(resp.toString());
            if (!resp.toString().contains("101")) throw new IOException("Handshake failed: " + resp);

            running = true;
            onConnected.run();

            // Thread de escrita
            Thread writer = new Thread(() -> {
                while (running) {
                    try {
                        String msg = sendQueue.poll(200, TimeUnit.MILLISECONDS);
                        if (msg != null) writeFrame(msg);
                    } catch (InterruptedException ie) { break; }
                    catch (IOException ioe) { break; }
                }
            }, "ws-writer");
            writer.setDaemon(true);
            writer.start();

            // Loop de leitura
            while (running) {
                String msg = readFrame();
                if (msg == null) break;
                onMessage.accept(msg);
            }

        } catch (Exception e) {
            // conexão falhou ou foi fechada
        } finally {
            running = false;
            close();
            onDisconnected.run();
        }
    }

    public void send(String msg) {
        sendQueue.offer(msg);
    }

    public void close() {
        running = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    private void writeFrame(String msg) throws IOException {
        byte[] payload = msg.getBytes(StandardCharsets.UTF_8);
        int len = payload.length;
        byte[] mask = new byte[4];
        new Random().nextBytes(mask);

        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81); // FIN + text opcode
        if (len <= 125) {
            frame.write(0x80 | len);
        } else if (len <= 65535) {
            frame.write(0x80 | 126);
            frame.write((len >> 8) & 0xFF);
            frame.write(len & 0xFF);
        } else {
            frame.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) frame.write((int) ((len >> (i * 8)) & 0xFF));
        }
        frame.write(mask);
        for (int i = 0; i < len; i++) frame.write(payload[i] ^ mask[i % 4]);

        synchronized (out) { out.write(frame.toByteArray()); out.flush(); }
    }

    private String readFrame() throws IOException {
        int b1 = in.read(); if (b1 == -1) return null;
        int b2 = in.read(); if (b2 == -1) return null;
        int opcode = b1 & 0x0F;
        boolean masked = (b2 & 0x80) != 0;
        long payloadLen = b2 & 0x7F;

        if (payloadLen == 126) {
            payloadLen = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
        } else if (payloadLen == 127) {
            payloadLen = 0;
            for (int i = 0; i < 8; i++) payloadLen = (payloadLen << 8) | (in.read() & 0xFF);
        }

        byte[] maskKey = null;
        if (masked) { maskKey = new byte[4]; for (int i = 0; i < 4; i++) maskKey[i] = (byte) in.read(); }

        byte[] payload = new byte[(int) payloadLen];
        int read = 0;
        while (read < payload.length) {
            int r = in.read(payload, read, payload.length - read);
            if (r == -1) return null;
            read += r;
        }
        if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= maskKey[i % 4];

        if (opcode == 8) return null;                          // close
        if (opcode == 9) { writeFrame(""); return readFrame(); } // ping -> pong
        if (opcode == 1 || opcode == 0) return new String(payload, StandardCharsets.UTF_8);
        return readFrame(); // ignora frames desconhecidos
    }

    private byte[] generateKey() {
        byte[] b = new byte[16];
        new Random().nextBytes(b);
        return b;
    }
}