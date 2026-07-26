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

/**
 * Скачивание .xlsx расписания через OkHttp. Репозиторий вызывает downloadSync()
 * из фонового потока, поэтому здесь синхронный execute(). Кэш — файл во
 * внутреннем cacheDir приложения.
 */
public class NetworkClient {

    private static final String TAG = "NetworkClient";

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

    /** Верхняя граница размера скачиваемого файла — защита от аномально большого/испорченного ответа. */
    private static final long MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024; // 50 МБ

    /** Синхронно качает файл по url в кэш и возвращает его. Вызывать НЕ из UI-потока. */
    public File downloadSync(String url) throws IOException {
        Log.d(TAG, "Загрузка: " + url);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "UniSchedule/1.0 (Android)")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Пустой ответ");

            long declaredLength = body.contentLength();
            if (declaredLength > MAX_DOWNLOAD_BYTES) {
                throw new IOException("Файл расписания слишком большой: " + declaredLength + " байт");
            }

            File cacheFile = new File(context.getCacheDir(), Constants.CACHE_FILE_NAME);
            // Пишем во временный файл и переименовываем только при полном успехе,
            // чтобы оборванная загрузка (killed process, обрыв сети) никогда
            // не оставляла в кэше повреждённый .xlsx, который потом молча
            // считался бы "валидным кэшем" в isCacheValid().
            File tmpFile = new File(context.getCacheDir(), Constants.CACHE_FILE_NAME + ".tmp");
            long total = 0;
            try (InputStream is = body.byteStream();
                 OutputStream os = new FileOutputStream(tmpFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) {
                    total += len;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw new IOException("Файл расписания превышает допустимый размер при загрузке");
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

            Log.d(TAG, "Сохранено: " + cacheFile.length() + " байт");
            return cacheFile;
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