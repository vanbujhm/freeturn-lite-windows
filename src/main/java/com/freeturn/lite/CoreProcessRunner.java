package com.freeturn.lite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CoreProcessRunner {
    interface Listener {
        void onLine(String line);
        void onExit(int exitCode);
        void onError(String message);
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "freeturn-core-reader");
        thread.setDaemon(true);
        return thread;
    });

    private Process process;

    synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    synchronized void start(CoreConfig config, Listener listener) {
        if (isRunning()) {
            listener.onError("Процесс уже запущен.");
            return;
        }
        List<String> command = config.command();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            listener.onLine(stamp("Запуск: " + config.redactedCommand()));
            Process started = process;
            executor.submit(() -> readProcess(started, listener));
        } catch (IOException e) {
            process = null;
            listener.onError("Не удалось запустить ядро: " + e.getMessage());
        }
    }

    synchronized void stop() {
        if (process == null) {
            return;
        }
        process.destroy();
        if (process.isAlive()) {
            executor.submit(() -> {
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                synchronized (CoreProcessRunner.this) {
                    if (process != null && process.isAlive()) {
                        process.destroyForcibly();
                    }
                }
            });
        }
    }

    void shutdown() {
        stop();
        executor.shutdownNow();
    }

    private void readProcess(Process started, Listener listener) {
        int exitCode = -1;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(started.getInputStream(), Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                listener.onLine(stamp(line));
            }
            exitCode = started.waitFor();
        } catch (IOException e) {
            listener.onError("Ошибка чтения лога ядра: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (this) {
                if (process == started) {
                    process = null;
                }
            }
            listener.onExit(exitCode);
        }
    }

    private static String stamp(String message) {
        return "[" + LocalDateTime.now().format(TIME) + "] " + message;
    }
}
