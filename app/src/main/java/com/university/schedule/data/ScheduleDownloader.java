package com.university.schedule.data;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.TlsVersion;

public class ScheduleDownloader {

    private static final String TAG = "ScheduleDownloader";
    private static final String PAGE_URL = "https://www.rsatu.ru/students/raspisanie-zanyatiy/";
    private static final String BASE_URL = "https://www.rsatu.ru";
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private static final Pattern HREF =
            Pattern.compile("<a\\s[^>]*href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private final OkHttpClient client;

    public ScheduleDownloader() {
        // широкий TLS-конфиг: OkHttp переберёт спеки и договорится с сервером
        ConnectionSpec modern = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                .build();
        this.client = new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(modern, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public interface Callback {
        void onSuccess(File file);
        void onError(String message);
    }

    public void download(final Context context, final Callback callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    File f = downloadSync(context);
                    if (f != null) callback.onSuccess(f);
                    else callback.onError("Файл не найден");
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка загрузки", e);
                    callback.onError(e.getMessage());
                }
            }
        }).start();
    }

    public File downloadSync(Context context) throws IOException {
        String html = fetchPage(PAGE_URL);
        Log.d(TAG, "Страница получена, байт: " + html.length());

        List<String> links = new ArrayList<>();
        Matcher m = HREF.matcher(html);
        while (m.find()) {
            String href = m.group(1);
            if (href == null) continue;
            String low = href.toLowerCase();
            if (low.contains(".xlsx") && low.contains("raspisanie")) links.add(href);
        }
        if (links.isEmpty()) {
            m = HREF.matcher(html);
            while (m.find()) {
                String href = m.group(1);
                if (href != null && href.toLowerCase().contains(".xlsx")) links.add(href);
            }
        }
        if (links.isEmpty()) throw new IOException("Ссылка на .xlsx не найдена на странице");

        String fileUrl = links.get(0);
        if (!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://")) {
            try {
                fileUrl = new URL(new URL(PAGE_URL), fileUrl).toString();
            } catch (Exception e) {
                if (fileUrl.startsWith("//")) fileUrl = "https:" + fileUrl;
                else if (fileUrl.startsWith("/")) fileUrl = BASE_URL + fileUrl;
            }
        }
        Log.d(TAG, "Найдена ссылка: " + fileUrl);

        String pathOnly = fileUrl;
        try {
            String p = URI.create(fileUrl).getPath();
            if (p != null && !p.isEmpty()) pathOnly = p;
        } catch (Exception ignored) {
            int qIdx = pathOnly.indexOf('?');
            if (qIdx >= 0) pathOnly = pathOnly.substring(0, qIdx);
            int hIdx = pathOnly.indexOf('#');
            if (hIdx >= 0) pathOnly = pathOnly.substring(0, hIdx);
        }
        String filename = pathOnly.substring(pathOnly.lastIndexOf('/') + 1);
        if (filename.isEmpty() || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            filename = "schedule.xlsx";
        }
        File dir = new File(context.getFilesDir(), "downloads");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Не удалось создать каталог downloads");
        }
        File out = new File(dir, filename);
        File tmp = new File(dir, filename + ".tmp");

        Request req = new Request.Builder().url(fileUrl)
                .header("User-Agent", UA)
                .header("Accept", "*/*")
                .header("Referer", PAGE_URL)
                .build();
        try (Response resp = client.newCall(req).execute()) {
            Log.d(TAG, "Ответ файла: HTTP " + resp.code());
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + " при скачивании файла");
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("Пустое тело ответа");
            try (InputStream in = body.byteStream();
                 FileOutputStream fos = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                fos.flush();
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                throw e;
            }
        }
        if (out.exists() && !out.delete()) {
            Log.w(TAG, "Не удалось удалить старый файл перед заменой: " + out.getName());
        }
        if (!tmp.renameTo(out)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Не удалось сохранить скачанный файл " + out.getName());
        }
        Log.d(TAG, "Сохранено: " + out.getAbsolutePath() + " (" + out.length() + " байт)");
        return out;
    }

    private String fetchPage(String url) throws IOException {
        Request req = new Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            Log.d(TAG, "Ответ страницы: HTTP " + resp.code() + " url=" + resp.request().url());
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + " при загрузке страницы");
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("Пустое тело страницы");
            return body.string();
        }
    }
}