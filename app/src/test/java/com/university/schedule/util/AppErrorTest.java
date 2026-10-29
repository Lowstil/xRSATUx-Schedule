package com.university.schedule.util;

import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AppErrorTest {

    @Test
    public void unknownHostMeansNoInternet() {
        AppError e = AppError.from(new UnknownHostException("api.university.ru"));
        assertEquals(AppError.Category.NO_INTERNET, e.category);
    }

    @Test
    public void connectExceptionMeansServerUnavailable() {
        AppError e = AppError.from(new ConnectException("Connection refused"));
        assertEquals(AppError.Category.SERVER_UNAVAILABLE, e.category);
    }

    @Test
    public void socketTimeoutMeansTimeout() {
        AppError e = AppError.from(new SocketTimeoutException("timeout"));
        assertEquals(AppError.Category.TIMEOUT, e.category);
    }

    @Test
    public void http404MeansServerUnavailable() {
        AppError e = AppError.from(new IOException("HTTP 404"));
        assertEquals(AppError.Category.SERVER_UNAVAILABLE, e.category);
    }

    @Test
    public void http500MeansServerUnavailable() {
        AppError e = AppError.from(new IOException("HTTP 500"));
        assertEquals(AppError.Category.SERVER_UNAVAILABLE, e.category);
    }

    @Test
    public void fileTooLargeMessageIsClassified() {
        AppError e = AppError.from(new IOException("Файл слишком большой: 123456789 байт"));
        assertEquals(AppError.Category.FILE_TOO_LARGE, e.category);
    }

    @Test
    public void unrecognizedExceptionFallsBackToUnknown() {
        AppError e = AppError.from(new RuntimeException("что-то совсем неожиданное"));
        assertEquals(AppError.Category.UNKNOWN, e.category);
    }

    @Test
    public void nullExceptionDoesNotCrash() {
        AppError e = AppError.from(null);
        assertEquals(AppError.Category.UNKNOWN, e.category);
        assertNotNull(e.userMessage);
    }

    @Test
    public void causeChainIsWalkedToFindRealReason() {
        Exception wrapped = new IOException("Ошибка загрузки", new UnknownHostException("dns fail"));
        AppError e = AppError.from(wrapped);
        assertEquals(AppError.Category.NO_INTERNET, e.category);
    }

    @Test
    public void everyCategoryHasNonEmptyMessage() {
        Throwable[] samples = {
                new UnknownHostException("x"),
                new ConnectException("x"),
                new SocketTimeoutException("x"),
                new IOException("HTTP 404"),
                new IOException("Файл слишком большой: 1 байт"),
                new RuntimeException("совсем незнакомая ошибка"),
        };
        for (Throwable t : samples) {
            AppError e = AppError.from(t);
            assertNotNull(e.userMessage);
            org.junit.Assert.assertFalse("Сообщение не должно быть пустым для " + t,
                    e.userMessage.trim().isEmpty());
        }
    }
}
