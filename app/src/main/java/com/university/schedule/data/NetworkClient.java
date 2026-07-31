package com.university.schedule.data;

import android.content.Context;
import android.util.Log;

import com.university.schedule.util.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class NetworkClient {

    private static final String TAG = "NetworkClient";
    /** Имя файла расписания, лежащего в app/src/main/assets/ (офлайн-копия). */
    private static final String ASSET_NAME = "schedule.xlsx";

    /** Ищет в HTML ссылки вида href="....xlsx" (одинарные/двойные кавычки). */
    private static final Pattern XLSX_LINK_PATTERN =
            Pattern.compile("href=[\"']([^\"']+\\.xlsx)[\"']", Pattern.CASE_INSENSITIVE);

    private static final long MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024; // 50 МБ

    private final OkHttpClient client;
    private final Context context;

    public NetworkClient(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    /** Синхронно качает файл по url в кэш. При ЛЮБОЙ ошибке сети — берёт копию из assets. */
    public File downloadSync(String url) throws IOException {
        return downloadToCache(url, Constants.CACHE_FILE_NAME, true);
    }

    /**
     * Динамическая загрузка файла переносов: имя файла на сайте вуза может
     * меняться (хэш в пути), поэтому сначала скачивается HTML страницы
     * Constants.TRANSFERS_PAGE_URL, на ней ищется ссылка на .xlsx с
     * фрагментом "перенос"/"perenos" в имени, и уже эта ссылка качается.
     * Если поиск на странице не удался — используется запасная прямая
     * ссылка Constants.TRANSFERS_FALLBACK_URL как последний рубеж.
     */
    public File downloadTransfersSync() throws IOException {
        String resolvedUrl = null;
        try {
            resolvedUrl = discoverTransfersUrl();
        } catch (Exception e) {
            Log.w(TAG, "Не удалось найти ссылку на файл переносов на странице: " + e.getMessage());
        }
        if (resolvedUrl == null) {
            Log.w(TAG, "Использую запасную прямую ссылку на файл переносов");
            resolvedUrl = Constants.TRANSFERS_FALLBACK_URL;
        }
        return downloadToCache(resolvedUrl, Constants.TRANSFERS_CACHE_FILE_NAME, false);
    }

    /**
     * Скачивает HTML-страницу Constants.TRANSFERS_PAGE_URL и ищет на ней первую
     * ссылку на .xlsx, чьё имя содержит Constants.TRANSFERS_FILENAME_HINT
     * (без учёта регистра). Относительные ссылки (без http/https) достраиваются
     * до абсолютных на основе адреса самой страницы.
     */
    private String discoverTransfersUrl() throws IOException {
        Request request = new Request.Builder()
                .url(Constants.TRANSFERS_PAGE_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) UniSchedule/1.0")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Пустой ответ от страницы переносов");
            String html = body.string();

            String hint = Constants.TRANSFERS_FILENAME_HINT.toLowerCase();
            Matcher m = XLSX_LINK_PATTERN.matcher(html);
            while (m.find()) {
                String link = m.group(1);
                if (link.toLowerCase().contains(hint)) {
                    String resolved = resolveUrl(Constants.TRANSFERS_PAGE_URL, link);
                    Log.d(TAG, "Найдена ссылка на файл переносов: " + resolved);
                    return resolved;
                }
            }
            Log.w(TAG, "На странице не найдено ссылок .xlsx с фрагментом \"" + hint + "\"");
            return null;
        }
    }

    /** Достраивает относительную ссылку (например "/upload/iblock/.../file.xlsx") до абсолютной. */
    private String resolveUrl(String pageUrl, String maybeRelative) {
        if (maybeRelative.startsWith("http://") || maybeRelative.startsWith("https://")) {
            return maybeRelative;
        }
        try {
            URL base = new URL(pageUrl);
            URL resolved = new URL(base, maybeRelative);
            return resolved.toString();
        } catch (Exception e) {
            return maybeRelative;
        }
    }

    private File downloadToCache(String url, String cacheFileName, boolean fallbackToAssetsOnError) throws IOException {
        Log.d(TAG, "Загрузка: " + url);
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) UniSchedule/1.0")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
                ResponseBody body = response.body();
                if (body == null) throw new IOException("Пустой ответ");

                long declaredLength = body.contentLength();
                if (declaredLength > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("Файл слишком большой: " + declaredLength + " байт");
                }

                File cacheFile = new File(context.getCacheDir(), cacheFileName);
                File tmpFile = new File(context.getCacheDir(), cacheFileName + ".tmp");
                long total = 0;
                try (InputStream is = body.byteStream();
                     OutputStream os = new FileOutputStream(tmpFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        total += len;
                        if (total > MAX_DOWNLOAD_BYTES) {
                            throw new IOException("Файл превышает допустимый размер при загрузке");
                        }
                        os.write(buf, 0, len);
                    }
                    os.flush();
                } catch (IOException e) {
                    //noinspection ResultOfMethodCallIgnored
                    tmpFile.delete();
                    throw e;
                }

                if (cacheFile.exists() && !cacheFile.delete()) {
                    Log.w(TAG, "Не удалось удалить старый файл кэша перед заменой");
                }
                if (!tmpFile.renameTo(cacheFile)) {
                    //noinspection ResultOfMethodCallIgnored
                    tmpFile.delete();
                    throw new IOException("Не удалось сохранить скачанный файл в кэш");
                }

                Log.d(TAG, "Сохранено из сети: " + cacheFile.length() + " байт (" + cacheFileName + ")");
                return cacheFile;
            }
        } catch (Exception e) {
            if (!fallbackToAssetsOnError) {
                // Для файла переносов офлайн-копии в assets нет смысла делать —
                // это динамические данные, устаревший ассет хуже, чем их отсутствие.
                if (e instanceof IOException) throw (IOException) e;
                throw new IOException(e);
            }
            Log.w(TAG, "Сеть недоступна (" + e.getMessage() + "), беру расписание из assets");
            File f = copyFromAssets(cacheFileName);
            if (f != null) return f;
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        }
    }

    /** Копирует assets/schedule.xlsx в кэш. null, если файла в assets нет. */
    private File copyFromAssets(String cacheFileName) {
        try (InputStream is = context.getAssets().open(ASSET_NAME)) {
            File cacheFile = new File(context.getCacheDir(), cacheFileName);
            try (OutputStream os = new FileOutputStream(cacheFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
                os.flush();
            }
            Log.d(TAG, "Взято из assets: " + cacheFile.length() + " байт");
            return cacheFile;
        } catch (IOException e) {
            Log.e(TAG, "В assets нет " + ASSET_NAME + ": " + e.getMessage());
            return null;
        }
    }

    public File getCachedFile() {
        File f = new File(context.getCacheDir(), Constants.CACHE_FILE_NAME);
        return f.exists() ? f : null;
    }

    public File getCachedTransfersFile() {
        File f = new File(context.getCacheDir(), Constants.TRANSFERS_CACHE_FILE_NAME);
        return f.exists() ? f : null;
    }

    public boolean isCacheValid() {
        File f = getCachedFile();
        if (f == null) return false;
        long age = System.currentTimeMillis() - f.lastModified();
        return age < Constants.SCHEDULE_TTL_HOURS * 3600L * 1000L;
    }

    public void clearCache() {
        File f = getCachedFile();
        if (f != null && f.exists()) f.delete();
        File tf = getCachedTransfersFile();
        if (tf != null && tf.exists()) tf.delete();
    }
}
