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
/**
* Скачивание файлов расписания и переносов.
*
* ПОЧЕМУ ТАК (фикс инцидента "скачалось овер старое расписание"):
* 1) Ссылка на файл расписания больше не жёсткая: страница вуза
*    сканируется и берётся первая ссылка .xlsx, чьё имя содержит
*    Constants.SCHEDULE_FILENAME_HINT (имя файла на сайте меняется —
*    в нём дата; жёсткая ссылка устаревает мгновенно). Жёсткая ссылка
*    Constants.SCHEDULE_URL — только запасной вариант.
* 2) Убита молчаливая подмена сети старой копией из assets: assets
*    используется ТОЛЬКО когда нет сети И нет ранее скачанного кэша
*    (самый первый запуск без интернета). Если кэш есть — исключение
*    уходит в UI (понятное сообщение через AppError), а БД сохраняет
*    ранее загруженное расписание, а не вшитое в APK старьё.
* 3) Флаг lastFetchFromAssets() позволяет ScheduleRepository не ставить
*    отметку "обновлено сейчас", если данные взяты из офлайн-копии.
*/
public class NetworkClient {
private static final String TAG = "NetworkClient";
/** Имя файла расписания в app/src/main/assets/ (офлайн-копия для первого запуска). */
private static final String ASSET_NAME = "schedule.xlsx";
/** Ищет в HTML ссылки вида href="....xlsx" (одинарные/двойные кавычки). */
private static final Pattern XLSX_LINK_PATTERN =
Pattern.compile("href=[\"']([^\"']+\\.xlsx)[\"']", Pattern.CASE_INSENSITIVE);
private static final long MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024; // 50 МБ
private final OkHttpClient client;
private final Context context;
private volatile boolean lastFetchFromAssets;
public NetworkClient(Context context) {
this.context = context.getApplicationContext();
this.client = new OkHttpClient.Builder()
.connectTimeout(30, TimeUnit.SECONDS)
.readTimeout(60, TimeUnit.SECONDS)
.writeTimeout(60, TimeUnit.SECONDS)
.followRedirects(true)
.build();
}
/** true, если последний downloadSync вернул офлайн-копию из assets, а не файл из сети. */
public boolean lastFetchFromAssets() {
return lastFetchFromAssets;
}
/**
* Расписание: текущий файл ищется по имени на странице вуза, прямая
* ссылка — запасной вариант. При ошибке сети: есть кэш — исключение в UI
* (БД не трогается); нет кэша — офлайн-копия из assets с флагом.
*/
public File downloadSync(String url) throws IOException {
lastFetchFromAssets = false;
String discovered = null;
try {
discovered = discoverUrl(Constants.SCHEDULE_PAGE_URL, Constants.SCHEDULE_FILENAME_HINT);
} catch (Exception e) {
Log.w(TAG, "Поиск ссылки расписания на странице не удался: " + e.getMessage());
}
IOException last = null;
if (discovered != null) {
try {
return downloadToCache(discovered, Constants.CACHE_FILE_NAME);
} catch (IOException e) {
last = e;
Log.w(TAG, "Скачивание найденной ссылки расписания не удалось: " + e.getMessage());
}
}
try {
return downloadToCache(url, Constants.CACHE_FILE_NAME);
} catch (IOException e) {
last = e;
}
if (last == null) last = new IOException("Не удалось скачать расписание");
File cache = getCachedFile();
if (cache != null) {
// Есть ранее скачанный файл — ничего не подменяем, отдаём ошибку:
// БД сохранит текущие данные, UI покажет понятное сообщение.
throw last;
}
Log.w(TAG, "Сети нет и кэша нет — беру офлайн-копию расписания из assets");
File f = copyFromAssets(Constants.CACHE_FILE_NAME);
if (f != null) {
lastFetchFromAssets = true;
return f;
}
throw last;
}
/**
* Переносы: ссылка ищется по странице. Если поиск не удался и кэш
* переносов есть — остаёмся на ранее загруженных (резервная прямая
* ссылка может указывать на файл ПРОШЛОГО года и затереть актуальные
* переносы, поэтому она используется только при полностью пустом кэше).
*/
public File downloadTransfersSync() throws IOException {
String resolved = null;
try {
resolved = discoverUrl(Constants.TRANSFERS_PAGE_URL, Constants.TRANSFERS_FILENAME_HINT);
} catch (Exception e) {
Log.w(TAG, "Не удалось найти ссылку на файл переносов на странице: " + e.getMessage());
}
if (resolved == null) {
File cache = getCachedTransfersFile();
if (cache != null) {
throw new IOException("Ссылка на файл переносов не найдена на странице — остаюсь на ранее загруженных переносах");
}
Log.w(TAG, "Использую запасную прямую ссылку на файл переносов");
resolved = Constants.TRANSFERS_FALLBACK_URL;
}
return downloadToCache(resolved, Constants.TRANSFERS_CACHE_FILE_NAME);
}
/**
* Скачивает HTML-страницу и ищет первую ссылку на .xlsx, чьё имя содержит
* hint (без учёта регистра). Относительные ссылки достраиваются до
* абсолютных на основе адреса страницы.
*/
private String discoverUrl(String pageUrl, String hint) throws IOException {
Request request = new Request.Builder()
.url(pageUrl)
.header("User-Agent", "Mozilla/5.0 (Linux; Android 13) UniSchedule/1.0")
.build();
try (Response response = client.newCall(request).execute()) {
if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
ResponseBody body = response.body();
if (body == null) throw new IOException("Пустой ответ от страницы " + pageUrl);
String html = body.string();
String lowHint = hint.toLowerCase();
Matcher m = XLSX_LINK_PATTERN.matcher(html);
while (m.find()) {
String link = m.group(1);
if (link.toLowerCase().contains(lowHint)) {
String resolved = resolveUrl(pageUrl, link);
Log.d(TAG, "Найдена ссылка на файл (" + hint + "): " + resolved);
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
private File downloadToCache(String url, String cacheFileName) throws IOException {
Log.d(TAG, "Загрузка: " + url);
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