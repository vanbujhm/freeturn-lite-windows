package com.freeturn.lite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class CoreProcessRunner {
    interface Listener {
        void onLine(String line);
        void onExit(int exitCode);
        void onError(String message);
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int INTERRUPT_TIMEOUT_SECONDS = 15;
    // Ядро завершается само не дольше чем за 5 секунд после сигнала.
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 7;
    // Process.destroy() на Windows убивает процесс сразу, а ядру нужно снять маршруты и
    // TURN-аллокации. Сигнал доходит только через консоль процесса, поэтому подключаемся к ней.
    // Нужен именно Ctrl+Break: Ctrl+C процесс может унаследовать игнорируемым от родителя.
    private static final String CONSOLE_INTERRUPT_SCRIPT = """
            Add-Type -Namespace FreeTurnLite -Name Kernel -MemberDefinition '
            private delegate bool Handler(uint ctrlType);
            private static readonly Handler ignore = delegate { return true; };
            [DllImport("kernel32.dll")] private static extern bool FreeConsole();
            [DllImport("kernel32.dll")] private static extern bool AttachConsole(uint processId);
            [DllImport("kernel32.dll")] private static extern bool SetConsoleCtrlHandler(Handler handler, bool add);
            [DllImport("kernel32.dll")] private static extern bool GenerateConsoleCtrlEvent(uint ctrlEvent, uint group);
            public static bool SendCtrlBreak(uint processId) {
                FreeConsole();
                return AttachConsole(processId) && SetConsoleCtrlHandler(ignore, true) && GenerateConsoleCtrlEvent(1, 0);
            }
            '
            if (-not [FreeTurnLite.Kernel]::SendCtrlBreak(%d)) { exit 1 }
            """;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "freeturn-core-reader");
        thread.setDaemon(true);
        return thread;
    });

    private Process process;
    private Process stopping;

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

    void stop() {
        Process running = claimForStop();
        if (running == null) {
            return;
        }
        Thread stopper = new Thread(() -> terminate(running), "freeturn-core-stop");
        stopper.setDaemon(true);
        stopper.start();
    }

    void shutdown() {
        Process running = claimForStop();
        if (running != null) {
            terminate(running);
        }
        executor.shutdownNow();
    }

    private synchronized Process claimForStop() {
        if (process == null || process == stopping) {
            return null;
        }
        stopping = process;
        return process;
    }

    private static void terminate(Process running) {
        try {
            if (!sendConsoleInterrupt(running.pid()) || !running.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                running.destroyForcibly();
            }
        } catch (InterruptedException e) {
            running.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static boolean sendConsoleInterrupt(long pid) throws InterruptedException {
        byte[] script = CONSOLE_INTERRUPT_SCRIPT.formatted(pid).getBytes(StandardCharsets.UTF_16LE);
        try {
            Process helper = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-EncodedCommand", Base64.getEncoder().encodeToString(script)
            ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!helper.waitFor(INTERRUPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                helper.destroyForcibly();
                return false;
            }
            return helper.exitValue() == 0;
        } catch (IOException e) {
            return false;
        }
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
