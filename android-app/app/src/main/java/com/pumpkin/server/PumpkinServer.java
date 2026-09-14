package com.pumpkin.server;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 持有 Pumpkin 原生进程的状态与输出。
 *
 * APK 里的 libpumpkin.so 就是交叉编译出来的 aarch64-linux-android 可执行文件，
 * 安装时由系统解压到 nativeLibraryDir，该目录在 Android 10+ 上仍允许执行
 * （app 私有数据目录被 W^X 禁止执行，nativeLibraryDir 是例外）。
 */
public final class PumpkinServer {

    private static final PumpkinServer INSTANCE = new PumpkinServer();
    private static final int MAX_LOG_CHARS = 200_000;
    private static final int TAIL_CHARS = 60_000;

    public static PumpkinServer get() {
        return INSTANCE;
    }

    private final StringBuilder log = new StringBuilder();
    private Process process;
    private File workDir;
    private volatile boolean running;
    private volatile int exitCode = Integer.MIN_VALUE;
    private volatile long startedAt;

    private PumpkinServer() {
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized File getWorkDir() {
        return workDir;
    }

    public synchronized int getExitCode() {
        return exitCode;
    }

    public synchronized long getStartedAt() {
        return startedAt;
    }

    public synchronized String tailLog() {
        int len = log.length();
        if (len <= TAIL_CHARS) {
            return log.toString();
        }
        return log.substring(len - TAIL_CHARS);
    }

    public synchronized void clearLog() {
        log.setLength(0);
    }

    /** 启动原生进程。工作目录设为 filesDir，这样 config/ world/ logs/ 都写在可写私有目录里。 */
    public synchronized void start(Context context) {
        if (running) {
            appendLine("[app] 服务器已在运行");
            return;
        }

        File bin = new File(context.getApplicationInfo().nativeLibraryDir, "libpumpkin.so");
        if (!bin.isFile()) {
            appendLine("[app] 找不到可执行文件: " + bin.getAbsolutePath());
            return;
        }

        workDir = context.getFilesDir();
        if (workDir == null) {
            appendLine("[app] filesDir 不可用");
            return;
        }
        if (!workDir.isDirectory() && !workDir.mkdirs()) {
            appendLine("[app] 无法创建工作目录: " + workDir.getAbsolutePath());
            return;
        }

        appendLine("[app] 可执行文件: " + bin.getAbsolutePath());
        appendLine("[app] 工作目录: " + workDir.getAbsolutePath());

        try {
            ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath());
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            // 服务端在非 TTY 下会退化到简单控制台读取；接到 /dev/null 后读到 EOF 会安全退出该线程
            pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
            process = pb.start();
            running = true;
            exitCode = Integer.MIN_VALUE;
            startedAt = System.currentTimeMillis();
            pumpOutput(process);
            appendLine("[app] 已启动");
        } catch (IOException e) {
            appendLine("[app] 启动失败: " + e);
            running = false;
        }
    }

    private void pumpOutput(final Process p) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream in = p.getInputStream();
                byte[] buf = new byte[8192];
                try {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        append(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                } catch (IOException ignored) {
                    // 进程退出时流会关闭
                }
                int code;
                try {
                    code = p.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    code = -1;
                }
                synchronized (PumpkinServer.this) {
                    running = false;
                    exitCode = code;
                    log.append("\n[app] 进程已退出，退出码 ").append(code).append('\n');
                    trim();
                }
            }
        }, "pumpkin-stdout");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        if (process == null || !running) {
            appendLine("[app] 服务器未在运行");
            return;
        }
        appendLine("[app] 正在停止服务器...");
        process.destroy();
        process = null;
    }

    private void appendLine(String s) {
        synchronized (this) {
            log.append(s).append('\n');
            trim();
        }
    }

    private void append(String s) {
        synchronized (this) {
            log.append(s);
            trim();
        }
    }

    private void trim() {
        int len = log.length();
        if (len > MAX_LOG_CHARS) {
            log.delete(0, len - MAX_LOG_CHARS);
        }
    }
}
