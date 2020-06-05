package ru.nordia.dsnitro;

import com.sun.jna.platform.win32.Kernel32;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;

import static org.fusesource.jansi.Ansi.ansi;

import org.fusesource.jansi.AnsiConsole;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Main {
    private static String dict = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static long timeStarted = System.currentTimeMillis();
    private static Scanner scanner = new Scanner(System.in);

    private static List<String> proxies;
    private static FileWriter writer;

    private static String username = "";
    private static String token = "";
    private static int checked = 0;
    private static int invalid = 0;
    private static int valid = 0;

    static {
        Timer timer = new Timer(300, e -> {
            long timePassed = System.currentTimeMillis() - timeStarted;
            String uptime = "Uptime: ";

            if (timePassed < TimeUnit.SECONDS.toMillis(60)) {
                uptime += timePassed / TimeUnit.SECONDS.toMillis(1) + " seconds";
            } else if (timePassed < TimeUnit.MINUTES.toMillis(60)) {
                uptime += timePassed / TimeUnit.MINUTES.toMillis(1) + " minutes";
            } else if (timePassed < TimeUnit.HOURS.toMillis(24)) {
                uptime += timePassed / TimeUnit.HOURS.toMillis(1) + " hours";
            } else {
                uptime += timePassed / TimeUnit.DAYS.toMillis(1) + " days";
            }

            Kernel32.INSTANCE.SetConsoleTitle("DNG by Nordia#9573 " + (username.equals("") ? "" : "| Logged in as " + username + " ") + "| " + uptime + " | Checked: " + checked + " | Invalid: " + invalid + " | Valid: " + valid);
        });
        timer.setInitialDelay(0);
        timer.setRepeats(true);
        timer.start();

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | UnsupportedLookAndFeelException | IllegalAccessException | InstantiationException e) {
            error(e);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(AnsiConsole::systemUninstall));
        AnsiConsole.systemInstall();

        java.util.logging.Logger.getLogger("org.apache.http.wire").setLevel(java.util.logging.Level.FINEST);
        java.util.logging.Logger.getLogger("org.apache.http.headers").setLevel(java.util.logging.Level.FINEST);
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
        System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
        System.setProperty("org.apache.commons.logging.simplelog.log.httpclient.wire", "ERROR");
        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "ERROR");
        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.headers", "ERROR");
    }

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);

            System.out.print(ansi().bold().fgMagenta().a("\n >> Использовать автоприменение валидных кодов? [Yes/No]: ").reset());

            if (scanner.nextLine().equalsIgnoreCase("yes")) {
                login();
            } else {
                clearConsole();
            }

            System.out.print(ansi().bold().fgMagenta().a("\n >> Использовать кастомные прокси? [Yes/No]: ").reset());

            if (scanner.nextLine().equalsIgnoreCase("yes")) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Выбрать прокси");

                File proxy = null;

                if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    proxy = fileChooser.getSelectedFile();
                } else {
                    System.exit(1);
                }

                proxies = FileUtils.readLines(proxy, StandardCharsets.UTF_8);
            } else {
                System.out.print(ansi().bold().fgMagenta().a("\n\n >> Начинаю загрузку прокси.. "));

                proxies = getProxies(getDate(0));

                if (proxies.isEmpty()) proxies = getProxies(getDate(1));
            }

            if (proxies.isEmpty()) error(" >> Недостаточно прокси.");
            if (!proxies.stream().allMatch(p -> p.matches("([0-9]*(\\.)?)*:[0-9]*"))) error(" >> Файл с прокси не является валидным.");

            clearConsole();

            System.out.println("\n >> Загружено " + proxies.size() + " штук | Тип: HTTP/HTTPS | Timeout: | <5000\n\n");

            writer = new FileWriter(new File(new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toString()).getParent().replaceFirst(".{6}", "") + File.separator + "valid.txt"), true);

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

                    checked++;

                    if (!message.equals("Unknown Gift Code") && !message.equals("service not exists") && !message.equals("You are being rate limited.") && !message.startsWith("You are being blocked")) {
                        try {
                            writer.append(code).append(" | ").append(message).append("\n");

                            System.out.println(ansi().fgGreen().a(code));
                            valid++;

                            writer.flush();
                        } catch (IOException ignored) {
                        }
                    } else {
                        System.out.println(ansi().fgRed().a(code));
                        invalid++;
                    }
                });
            }
        } catch (Throwable e) {
            error(e);
        }
    }

    private static void login() throws IOException, ParseException, InterruptedException {
        clearConsole();

        System.out.print(ansi().bold().fgMagenta().a("\n >> Введите токен: ").reset());

        String tkn = scanner.nextLine();

        HttpGet httpGet = new HttpGet("https://discord.com/api/v6/users/@me");
        httpGet.addHeader("authorization", tkn);

        JSONObject response = (JSONObject) new JSONParser().parse(new InputStreamReader(HttpClients.createMinimal().execute(httpGet).getEntity().getContent()));

        Object name = response.get("username");

        if (name == null) {
            System.out.println(ansi().bold().fgMagenta().a("\n >> Токен не является валидным. Попробуйте ещё раз."));

            Thread.sleep(2000);

            login();
        } else {
            token = tkn;
            username = name + "#" + response.get("discriminator");
            clearConsole();
        }
    }

    private static JSONObject sendRequest(String[] proxy, String code) throws IOException, ParseException {
        HttpClient client = HttpClients.custom()
                .setProxy(new HttpHost(proxy[0], Integer.parseInt(proxy[1])))
                .build();

        if (!token.equals("")) {
            HttpPost httpPost = new HttpPost("https://discord.com/api/v6/entitlements/gift-codes/" + code + "/redeem");
            httpPost.addHeader("authorization", token);

            return (JSONObject) new JSONParser().parse(new InputStreamReader(client.execute(httpPost).getEntity().getContent()));
        } else {
            return (JSONObject) new JSONParser().parse(new InputStreamReader(client.execute(new HttpGet("https://discord.com/api/v6/entitlements/gift-codes/" + code + "?with_application=false&with_subscription_plan=true")).getEntity().getContent()));
        }
    }

    private static List<String> getProxies(String date) throws IOException, ParseException {
        HttpResponse response = HttpClients.createDefault().execute(new HttpGet("https://checkerproxy.net/api/archive/" + date));

        return (List<String>) ((JSONArray) new JSONParser().parse(new InputStreamReader(response.getEntity().getContent())))
                .stream()
                .filter(object -> (long) ((JSONObject) object).get("type") <= 2 && (long) ((JSONObject) object).get("timeout") <= 5000)
                .map(object -> ((JSONObject) object).get("addr"))
                .collect(Collectors.toList());
    }

    private static void error(String message) {
        try {
            System.out.println(ansi().bold().fgYellow().a("\n\n" + message));
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void error(Throwable throwable) {
        try {
            System.out.println(ansi().bold().fgYellow().a("\n\n"));
            throwable.printStackTrace();
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static String getCode() {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < 16; i++) {
            builder.append(dict.charAt(ThreadLocalRandom.current().nextInt(dict.length())));
        }

        return builder.toString();
    }

    private static void clearConsole() throws IOException, InterruptedException {
        new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
    }

    private static String getDate(int offset) {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(offset)));
    }
}
