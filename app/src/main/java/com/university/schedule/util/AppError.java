package com.university.schedule.util;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Классификация ошибок загрузки расписания/переносов для показа пользователю.
 *
 * ПРОБЛЕМА, которую это решает: раньше UI просто показывал
 * e.getMessage() из пойманного исключения — то есть пользователь видел
 * что-то вроде "UnknownHostException" или "HTTP 404" вместо понятного
 * "Нет интернет-соединения" / "Сайт вуза временно недоступен". Эта логика
 * классификации специально вынесена в отдельный класс (а не размазана по
 * catch-блокам в ScheduleRepository/NetworkClient), чтобы:
 *  - и фоновая загрузка, и UI на экране Настроек/главном экране показывали
 *    ОДИНАКОВЫЕ сообщения для одной и той же причины;
 *  - классификацию можно было покрыть юнит-тестами независимо от сети.
 */
public final class AppError {

    /** Категория ошибки — определяет иконку и текст в UI. */
    public enum Category {
        /** Нет соединения с интернетом вообще (самолётный режим, нет сети). */
        NO_INTERNET,
        /** Соединение есть, но сервер вуза не отвечает или недоступен. */
        SERVER_UNAVAILABLE,
        /** Запрос выполнялся слишком долго и был прерван по таймауту. */
        TIMEOUT,
        /** Сервер ответил, но содержимое не похоже на .xlsx (сайт изменил формат/разметку). */
        BAD_FILE_FORMAT,
        /** Файл больше разумного предела — вероятно, ошибка на сервере, а не настоящее расписание. */
        FILE_TOO_LARGE,
        /** Любая другая ошибка, которую не удалось классифицировать точнее. */
        UNKNOWN
    }

    public final Category category;
    public final String userMessage;
    /** Техническая деталь для логов/отладки — НЕ показывается пользователю напрямую. */
    public final String technicalDetail;

    private AppError(Category category, String userMessage, String technicalDetail) {
        this.category = category;
        this.userMessage = userMessage;
        this.technicalDetail = technicalDetail;
    }

    /** Разбирает исключение (включая цепочку причин) и подбирает понятное сообщение. */
    public static AppError from(Throwable t) {
        String detail = t == null ? "" : String.valueOf(t.getMessage());
        Throwable cause = t;
        while (cause != null) {
            Category cat = classifyOne(cause);
            if (cat != null) {
                return new AppError(cat, messageFor(cat), detail);
            }
            cause = cause.getCause();
        }
        return new AppError(Category.UNKNOWN, messageFor(Category.UNKNOWN), detail);
    }

    private static Category classifyOne(Throwable t) {
        if (t instanceof UnknownHostException) return Category.NO_INTERNET;
        if (t instanceof ConnectException) return Category.SERVER_UNAVAILABLE;
        if (t instanceof SocketTimeoutException) return Category.TIMEOUT;

        String msg = t.getMessage();
        if (msg == null) return null;
        String low = msg.toLowerCase();

        if (low.contains("файл слишком большой") || low.contains("превышает допустимый размер")) {
            return Category.FILE_TOO_LARGE;
        }
        if (low.startsWith("http ")) {
            return Category.SERVER_UNAVAILABLE;
        }
        String className = t.getClass().getName();
        if (className.startsWith("org.apache.poi")
                || low.contains("not a valid") || low.contains("zip") || low.contains("ooxml")) {
            return Category.BAD_FILE_FORMAT;
        }
        if (t instanceof IOException && (low.contains("пустой ответ") || low.contains("сохранить"))) {
            return Category.SERVER_UNAVAILABLE;
        }
        return null;
    }

    private static String messageFor(Category c) {
        switch (c) {
            case NO_INTERNET:
                return "Нет интернет-соединения. Проверьте Wi-Fi или мобильные данные и попробуйте снова.";
            case SERVER_UNAVAILABLE:
                return "Сайт университета сейчас недоступен. Показано последнее сохранённое расписание.";
            case TIMEOUT:
                return "Сервер вуза отвечает слишком долго. Попробуйте ещё раз чуть позже.";
            case BAD_FILE_FORMAT:
                return "Файл расписания на сайте изменил формат. Мы уже знаем о проблеме — попробуйте обновить позже.";
            case FILE_TOO_LARGE:
                return "Файл с сайта вуза оказался подозрительно большим и не был загружен.";
            case UNKNOWN:
            default:
                return "Не удалось обновить расписание. Попробуйте ещё раз позже.";
        }
    }
}
