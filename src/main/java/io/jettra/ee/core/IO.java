package io.jettra.ee.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utilitario de consola y logging con formato y colores ANSI para JettraEE.
 */
public class IO {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static final String RESET = "\u001B[0m";
    public static final String CYAN = "\u001B[36m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String RED = "\u001B[31m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String BOLD = "\u001B[1m";

    public static void println(Object msg) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        System.out.println(CYAN + "[" + timestamp + "]" + RESET + " " + CYAN + "[JettraEE]" + RESET + " " + msg);
    }

    public static void info(Object msg) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        System.out.println(CYAN + "[" + timestamp + "]" + RESET + " " + GREEN + "[INFO]" + RESET + " " + msg);
    }

    public static void success(Object msg) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        System.out.println(CYAN + "[" + timestamp + "]" + RESET + " " + GREEN + BOLD + "[SUCCESS]" + RESET + " " + msg);
    }

    public static void warn(Object msg) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        System.out.println(CYAN + "[" + timestamp + "]" + RESET + " " + YELLOW + "[WARN]" + RESET + " " + msg);
    }

    public static void error(Object msg) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        System.err.println(CYAN + "[" + timestamp + "]" + RESET + " " + RED + "[ERROR]" + RESET + " " + msg);
    }

    public static void error(Object msg, Throwable t) {
        error(msg);
        if (t != null) {
            t.printStackTrace(System.err);
        }
    }
}
