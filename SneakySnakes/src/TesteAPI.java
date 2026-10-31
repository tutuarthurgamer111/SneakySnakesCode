import java.net.*;
import java.io.*;

public class TesteAPI {
    public static void main(String[] args) throws Exception {
        URL url = new URL("https://sneakysnakes-server-list.vercel.app/api/servers");

        System.out.println("Conectando...");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();

        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "SneakySnakes-Test");

        System.out.println("Aguardando resposta...");
        System.out.println("HTTP: " + c.getResponseCode());

        try (BufferedReader r = new BufferedReader(
            new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null)
                System.out.println(line);
        }

        c.disconnect();
    }
}
