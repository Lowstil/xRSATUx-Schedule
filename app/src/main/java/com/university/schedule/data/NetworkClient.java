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
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Скачивание файлов расписания и переносов занятий с сайта вуза.
 * 1) Динамический поиск актуальной ссылки на файл на странице сайта вуза по шаблону и хинту.
 * 2) Если поиск не удался — переход на резервную прямую ссылку из Constants.
 * 3) Поддержка SSL/TLS для сертификатов rsatu.ru (включая неполную цепочку промежуточных сертификатов).
 * 4) Загрузка во временный файл и атомарная замена в кэше.
 */
public class NetworkClient {
    private static final String TAG = "NetworkClient";
    private static final String ASSET_NAME = "schedule.xlsx";
    private static final Pattern XLSX_LINK_PATTERN =
            Pattern.compile("href=[\"']([^\"']+\\.xlsx)[\"']", Pattern.CASE_INSENSITIVE);
    private static final long MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024;

    private final OkHttpClient client;
    private final Context context;
    private volatile boolean lastFetchFromAssets = false;

    public NetworkClient(Context context) {
        this.context = context.getApplicationContext();
        this.client = buildHttpClient();
    }

    private OkHttpClient buildHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true);
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            X509TrustManager defaultTm = null;
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    defaultTm = (X509TrustManager) tm;
                    break;
                }
            }

            final X509TrustManager finalDefaultTm = defaultTm;
            X509TrustManager customTm = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    if (finalDefaultTm != null) finalDefaultTm.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    try {
                        if (finalDefaultTm != null) {
                            finalDefaultTm.checkServerTrusted(chain, authType);
                            return;
                        }
                    } catch (CertificateException e) {
                        // Резервное доверие для rsatu.ru, если сервер вуза не отсылает промежуточный сертификат
                        if (chain != null && chain.length > 0) {
                            for (X509Certificate cert : chain) {
                                String dn = cert.getSubjectX500Principal().getName();
                                if (dn != null && dn.toLowerCase().contains("rsatu.ru")) {
                                    Log.d(TAG, "Доверие сертификату вуза rsatu.ru: " + dn);
                                    return;
                                }
                            }
                        }
                        throw e;
                    }
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return finalDefaultTm != null ? finalDefaultTm.getAcceptedIssuers() : new X509Certificate[0];
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{customTm}, new SecureRandom());

            builder.sslSocketFactory(sslContext.getSocketFactory(), customTm);
            builder.hostnameVerifier((hostname, session) -> {
                if (hostname != null && (hostname.equalsIgnoreCase("www.rsatu.ru") || hostname.equalsIgnoreCase("rsatu.ru") || hostname.endsWith(".rsatu.ru"))) {
                    return true;
                }
                return HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session);
            });
        } catch (Exception e) {
            Log.w(TAG, "Не удалось настроить кастомный SSL: " + e.getMessage());
        }
        return builder.build();
    }

    public boolean lastFetchFromAssets() {
        return lastFetchFromAssets;
    }

    /**
     * Скачивает основное расписание: ищет актуальную ссылку на странице SCHEDULE_PAGE_URL,
     * а если не находит — использует SCHEDULE_FALLBACK_URL.
     */
    public File downloadScheduleSync() throws IOException {
        lastFetchFromAssets = false;
        String resolvedUrl = null;
        try {
            resolvedUrl = discoverScheduleUrl();
        } catch (Exception e) {
            Log.w(TAG, "Не удалось найти ссылку на основное расписание на странице: " + e.getMessage());
        }
        if (resolvedUrl == null) {
            Log.w(TAG, "Использую запасную прямую ссылку на основное расписание");
            resolvedUrl = Constants.SCHEDULE_FALLBACK_URL;
        }
        // Если кэша ещё нет (первый запуск устройства), разрешаем fallbackToAssets,
        // чтобы приложение гарантированно открылось с расписанием даже при сбоях сети/эмулятора.
        boolean allowAssetsFallback = (getCachedFile() == null);
        return downloadToCache(resolvedUrl, Constants.CACHE_FILE_NAME, allowAssetsFallback);
    }

    /**
     * Алиас для обратной совместимости.
     */
    public File downloadSync(String url) throws IOException {
        if (url != null && !url.isEmpty() && !url.contains("university.ru")) {
            boolean allowAssetsFallback = (getCachedFile() == null);
            return downloadToCache(url, Constants.CACHE_FILE_NAME, allowAssetsFallback);
        }
        return downloadScheduleSync();
    }

    private String discoverScheduleUrl() throws IOException {
        Request request = new Request.Builder()
                .url(Constants.SCHEDULE_PAGE_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) UniSchedule/1.0")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Пустой ответ от страницы расписания");
            String html = body.string();
            String hint = Constants.SCHEDULE_FILENAME_HINT.toLowerCase();
            Matcher m = XLSX_LINK_PATTERN.matcher(html);
            String firstXlsx = null;
            while (m.find()) {
                String link = m.group(1);
                if (firstXlsx == null) firstXlsx = link;
                if (link.toLowerCase().contains(hint)) {
                    String resolved = resolveUrl(Constants.SCHEDULE_PAGE_URL, link);
                    Log.d(TAG, "Найдена ссылка на основное расписание: " + resolved);
                    return resolved;
                }
            }
            if (firstXlsx != null) {
                String resolved = resolveUrl(Constants.SCHEDULE_PAGE_URL, firstXlsx);
                Log.d(TAG, "Не найдено по хинту, берём первую .xlsx: " + resolved);
                return resolved;
            }
            Log.w(TAG, "На странице не найдено ссылок .xlsx");
            return null;
        }
    }

    /**
     * Скачивает журнал переносов: ищет актуальную ссылку на странице TRANSFERS_PAGE_URL,
     * а если не находит — использует TRANSFERS_FALLBACK_URL.
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
                if (e instanceof IOException) throw (IOException) e;
                throw new IOException(e);
            }
            Log.w(TAG, "Сеть недоступна (" + e.getMessage() + "), беру расписание из assets");
            File f = copyFromAssets(cacheFileName);
            if (f != null) {
                lastFetchFromAssets = true;
                return f;
            }
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        }
    }

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
