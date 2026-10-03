package com.xiaowu.game.starveil.infrastructure.logging;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class LoggerManager {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH-mm");
    private static final SimpleDateFormat DAY_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static AtomicInteger dailyLogCounter = new AtomicInteger(1);
    private static String currentDay = "";
    private static PrintWriter logWriter;
    private static String currentLogFile;
    private static PrintStream originalOut;
    private static PrintStream originalErr;

    private static Boolean initialized = false;
    private static volatile boolean isLogging = false;

    /* 新增：是否为 DEBUG 模式 */
    public static volatile boolean DEBUG = false;

    // ==================== 内存环形日志（供 DebugWindow 等实时读取） ====================
    private static final int MAX_MEMORY_LOGS = 5000;
    private static final Object memoryLogLock = new Object();
    private static final java.util.List<String> memoryLogs = new java.util.ArrayList<>();

    private static void appendMemoryLog(String formattedLine) {
        synchronized (memoryLogLock) {
            memoryLogs.add(formattedLine);
            if (memoryLogs.size() > MAX_MEMORY_LOGS) {
                memoryLogs.remove(0);
            }
        }
    }

    /** 获取当前内存中的全部日志行快照（含历史，最新在末尾）。 */
    public static java.util.List<String> getRecentLogLines() {
        synchronized (memoryLogLock) {
            return new java.util.ArrayList<>(memoryLogs);
        }
    }

    /** 内存日志当前行数（可用于增量追尾）。 */
    public static int getLogCount() {
        synchronized (memoryLogLock) {
            return memoryLogs.size();
        }
    }

    static {
        initializeLogger();
    }
    public static void initializeLogger() {
        if (initialized) {
            return;
        }
        try {
             
            currentDay = DAY_FORMAT.format(new Date());
            resetDailyCounter();

             
            File logDir = new File("logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

             
            createNewLogFile();
            redirectSystemStreams();
            initialized = true;

             
            if (originalOut != null) {
                originalOut.println("日志管理器初始化完成: " + currentLogFile);
            }

        } catch (IOException e) {
            System.err.println("初始化日志管理器失败: " + e.getMessage());
        }
    }

    private static void redirectSystemStreams() {
         
        originalOut = System.out;
        originalErr = System.err;

         
        PrintStream loggerOut = new PrintStream(new OutputStream() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public void write(int b) throws IOException {
                if (b == '\n') {
                    flush();
                } else {
                    buffer.write(b);
                }
            }

            @Override
            public void flush() throws IOException {
                if (buffer.size() > 0) {
                    String line = buffer.toString(StandardCharsets.UTF_8);
                    buffer.reset();
                    if (!line.trim().isEmpty()) {
                        directLog("INFO", line);
                    }
                }
            }
        }, true, StandardCharsets.UTF_8);

        System.setOut(loggerOut);

         
        PrintStream loggerErr = new PrintStream(new OutputStream() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public void write(int b) throws IOException {
                if (b == '\n') {
                    flush();
                } else {
                    buffer.write(b);
                }
            }

            @Override
            public void flush() throws IOException {
                if (buffer.size() > 0) {
                    String line = buffer.toString(StandardCharsets.UTF_8.name());
                    buffer.reset();
                    if (!line.trim().isEmpty()) {
                        directLog("ERROR", line);
                    }
                }
            }
        }, true, StandardCharsets.UTF_8);

        System.setErr(loggerErr);
    }

     
    private static void directLog(String type, String message) {
        if (isLogging) {
            return;
        }

        isLogging = true;
        try {
            checkDateChange();
            if (logWriter != null) {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                String[] lines = message.split("\n");

                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;

                    String formattedLine = "[" + timestamp + "] " + type + ": " + line;

                    // 写入文件
                    logWriter.println(formattedLine);

                    // 记录到内存缓冲（实时展示用）
                    appendMemoryLog(formattedLine);

                    if (type.equals("ERROR") && originalErr != null) {
                         
                        PrintWriter errWriter = new PrintWriter(new OutputStreamWriter(originalErr, StandardCharsets.UTF_8), true);
                        errWriter.println(formattedLine);
                    } else if (originalOut != null) {
                        PrintWriter outWriter = new PrintWriter(new OutputStreamWriter(originalOut, StandardCharsets.UTF_8), true);
                        outWriter.println(formattedLine);
                    }
                }
                logWriter.flush();
            }
        } catch (Exception e) {
            if (originalErr != null) {
                 
                try {
                    originalErr.write(("日志记录错误: " + e.getMessage() + "\n").getBytes(StandardCharsets.UTF_8));
                } catch (IOException ioException) {
                     
                    originalErr.println("日志记录错误: " + e.getMessage());
                }
            }
        } finally {
            isLogging = false;
        }
    }

    private static void resetDailyCounter() {
        File logDir = new File("logs");
        if (logDir.exists()) {
            File[] logFiles = logDir.listFiles((dir, name) -> name.startsWith(currentDay) && name.endsWith(".log"));
            if (logFiles != null && logFiles.length > 0) {
                dailyLogCounter.set(logFiles.length + 1);
            } else {
                dailyLogCounter.set(1);
            }
        }
    }

    private static void createNewLogFile() throws IOException {
        if (logWriter != null) {
            logWriter.close();
        }

        String timestamp = DATE_FORMAT.format(new Date());
        int logNumber = dailyLogCounter.getAndIncrement();
        currentLogFile = String.format("logs/%s %d.log", timestamp, logNumber);

         
        FileOutputStream fos = new FileOutputStream(currentLogFile, true);
        OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        logWriter = new PrintWriter(new BufferedWriter(osw), true);  

         
        String initMessage = "日志文件创建: " + currentLogFile;
        if (originalOut != null) {
            try {
                originalOut.write((initMessage + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                originalOut.println(initMessage);
            }
        }

         
        String fileHeader = "=== 日志文件开始于 " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " ===";
        logWriter.println(fileHeader);
        logWriter.flush();
    }

     
    private static void checkDateChange() {
        String today = DAY_FORMAT.format(new Date());
        if (!today.equals(currentDay)) {
            currentDay = today;
            resetDailyCounter();
            try {
                createNewLogFile();
            } catch (IOException e) {
                if (originalErr != null) {
                    try {
                        originalErr.write(("创建新日志文件失败: " + e.getMessage() + "\n").getBytes(StandardCharsets.UTF_8));
                    } catch (IOException ioException) {
                        originalErr.println("创建新日志文件失败: " + e.getMessage());
                    }
                }
            }
        }
    }

     
    public static void Logger(String Type, String Message) {
        if (isLogging) {
            return;
        }

        switch (Type.toUpperCase()) {
            case "INFO":
                logInfo(Message);
                break;
            case "ERROR":
                logError(Message);
                break;
            case "WARNING":
            case "WARN":
                logWarning(Message);
                break;
            case "DEBUG":
                if (!DEBUG) return;          // 非 DEBUG 直接忽略
                writeLog("DEBUG", Message, originalOut);
                break;
            default:
                logWarning("程序报告了不存在的日志类型: " + Type + "\n" + Message);
                break;
        }
    }

    public static void logInfo(String message) {
        writeLog("INFO", message, originalOut);
    }

    public static void logError(String message) {
        writeLog("ERROR", message, originalErr);
    }

    public static void logWarning(String message) {
        writeLog("WARNING", message, originalOut);
    }

    private static void writeLog(String type, String message, PrintStream consoleStream) {
        if (isLogging) return;
        isLogging = true;
        try {
            checkDateChange();
            if (logWriter != null) {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                String[] lines = message.split("\n");

                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;

                    String formattedLine = "[" + timestamp + "] " + type + ": " + line;
                    logWriter.println(formattedLine);

                    // 记录到内存缓冲（实时展示用）
                    appendMemoryLog(formattedLine);

                    if (consoleStream != null) {
                        try {
                            consoleStream.write((formattedLine + "\n").getBytes(StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            consoleStream.println(formattedLine);
                        }
                    }
                }
                logWriter.flush();
            }
        } finally {
            isLogging = false;
        }
    }

    public static String getCurrentLogFileName() {
        return currentLogFile;
    }

    public static void close() {
        if (logWriter != null) {
            String shutdownMessage = "=== 日志文件结束于 " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " ===";
            logWriter.println(shutdownMessage);
            logWriter.close();
        }

         
        if (originalOut != null) {
            System.setOut(originalOut);
        }
        if (originalErr != null) {
            System.setErr(originalErr);
        }
    }
}