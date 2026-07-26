package com.university.schedule.data;

import android.content.Context;
import android.util.Log;

import com.university.schedule.util.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class NetworkClient {

    private static final String TAG = "NetworkClient";
    /** Имя файла расписания, лежащего в app/src/main/assets/ (офлайн-копия). */
    private static final String ASSET_NAME = "schedule.xlsx";

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
                File cacheFile = new File(context.getCacheDir(), Constants.CACHE_FILE_NAME);
                try (InputStream is = body.byteStream();
                     OutputStream os = new FileOutputStream(cacheFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
                    os.flush();
                }
                Log.d(TAG, "Сохранено из сети: " + cacheFile.length() + " байт");
                return cacheFile;
            }
        } catch (Exception e) {
            Log.w(TAG, "Сеть недоступна (" + e.getMessage() + "), беру расписание из assets");
            File f = copyFromAssets();
            if (f != null) return f;
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        }
    }

    /** Копирует assets/schedule.xlsx в кэш. null, если файла в assets нет. */
    private File copyFromAssets() {
        try (InputStream is = context.getAssets().open(ASSET_NAME)) {
            File cacheFile = new File(context.getCacheDir(), Constants.CACHE_FILE_NAME);
            try (OutputStream os = new FileOutputStream(cacheFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
                os.flush();
            }
            Log.d(TAG, "Взято из assets: " + cacheFile.length() + " байт");
            return cacheFile;
        } catch (IOException e) {
            Log.e(TAG, "В assets нет schedule.xlsx: " + e.getMessage());
            return null;
        }
    }

    public File getCachedFile() {
        File f = new File(context.getCacheDir(), Constants.CACHE_FILE_NAME);
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
    }
}