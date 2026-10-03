package com.xiaowu.game.starveil.platform.windows;

import com.xiaowu.game.starveil.launcher.Launcher;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.Native;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

public class WindowsPermissionHandler {

    public interface Shell32X extends Shell32 {
        Shell32X INSTANCE = Native.loadLibrary("shell32", Shell32X.class, W32APIOptions.UNICODE_OPTIONS);

        int SEE_MASK_NOCLOSEPROCESS = 0x00000040;
        int SW_SHOWDEFAULT = 10;

        boolean ShellExecuteEx(SHELLEXECUTEINFO lpExecInfo);

        class SHELLEXECUTEINFO extends Structure {
            public int cbSize = size();
            public int fMask;
            public HWND hwnd;
            public WString lpVerb;
            public WString lpFile;
            public WString lpParameters;
            public WString lpDirectory;
            public int nShow;
            public HINSTANCE hInstApp;
            public Pointer lpIDList;
            public WString lpClass;
            public HKEY hKeyClass;
            public int dwHotKey;
            public HANDLE hMonitor;
            public HANDLE hProcess;

            protected List<String> getFieldOrder() {
                return Arrays.asList(
                        "cbSize", "fMask", "hwnd", "lpVerb", "lpFile", "lpParameters",
                        "lpDirectory", "nShow", "hInstApp", "lpIDList", "lpClass",
                        "hKeyClass", "dwHotKey", "hMonitor", "hProcess"
                );
            }
        }
    }

    public static final String ARG_ILLEGAL_SHUTDOWN = "--illegal-shutdown";
    public static final String ARG_ADMIN_RESTART   = "--admin-restart";
    public static final String ARG_SYSTEM_RESTART  = "--system-restart";

    public static boolean isAdmin() {
        try {
            String command = "reg query \"HKU\\S-1-5-19\"";
            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();
            return 0 == p.exitValue();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isRunningFromExe() {
        try {
            String classPath = System.getProperty("java.class.path");
            boolean classPathHasJar = classPath != null && classPath.toLowerCase().contains(".jar");

            String sunJavaCommand = System.getProperty("sun.java.command");
            boolean commandHasJar = sunJavaCommand != null && sunJavaCommand.toLowerCase().contains(".jar");

            List<String> inputArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            boolean hasLaunch4jSpecificArg = inputArgs.stream().anyMatch(arg -> arg.contains("-launch4j"));

            return !classPathHasJar || !commandHasJar || hasLaunch4jSpecificArg;
        } catch (Exception e) {
            String javaHome = System.getProperty("java.home");
            return !javaHome.contains("jre") && !javaHome.contains("JDK");
        }
    }

    public static String getExePath() {
        try {
            return new File(WindowsPermissionHandler.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getPath();
        } catch (Exception e) {
            return System.getProperty("user.dir") + "\\" + getExeName();
        }
    }

    public static String getExeName() {
        String jarPath = System.getProperty("java.class.path");
        String jarName = new File(jarPath).getName();
        return jarName.replace(".jar", ".exe");
    }

    public static boolean restartAsAdmin(boolean illegalShutdown) {
        if (!isRunningFromExe()) {
            Logger("INFO", "并不是使用exe运行的，无法提权");
            return false;
        }
        if (isAdmin()) {
            Logger("INFO", "已经是管理员权限，无需提权");
            return true;
        }
        try {
            String exePath = getExePath();
            File exeFile = new File(exePath);
            if (!exeFile.exists()) return false;

            notifyLauncherToCleanupLock();

            Shell32X.SHELLEXECUTEINFO execInfo = new Shell32X.SHELLEXECUTEINFO();
            execInfo.lpFile   = new WString(exePath);
            execInfo.lpParameters = new WString(getCommandLineArgs(illegalShutdown));
            execInfo.nShow    = Shell32X.SW_SHOWDEFAULT;
            execInfo.fMask    = Shell32X.SEE_MASK_NOCLOSEPROCESS;
            execInfo.lpVerb   = new WString("runas");

            boolean result = Shell32X.INSTANCE.ShellExecuteEx(execInfo);
            if (result) {
                Logger("INFO", "尝试以管理员权限重启" + (illegalShutdown ? "（携带异常关闭标记）" : ""));
                Thread.sleep(500);
                System.exit(0);
                return true;
            } else {
                int lastError = Kernel32.INSTANCE.GetLastError();
                String errorMessage = Kernel32Util.formatMessageFromLastErrorCode(lastError);
                Logger("Error", "提升权限时发生错误" + errorMessage);
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean hasCommandLineArg(String arg) {
        try {
            String sunJavaCommand = System.getProperty("sun.java.command", "");
            if (sunJavaCommand.contains(arg)) return true;
            List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
            return inputArguments.contains(arg);
        } catch (Exception e) {
            Logger("WARNING", "检查命令行参数时出错: " + e.getMessage());
            return false;
        }
    }

    public static boolean isAdminRestart() {
        return hasCommandLineArg(ARG_ADMIN_RESTART);
    }

    /**
     * 检查是否有非法关机标记（供 SystemTaskRunner 使用）
     */
    public static boolean hasIllegalShutdownFlag() {
        return hasCommandLineArg(ARG_ILLEGAL_SHUTDOWN);
    }

    public static boolean isSystem() {
        final String testKey = "HKLM\\SAM\\SAM\\__ccraft_system_test__";
        try {
            Process p1 = Runtime.getRuntime().exec("reg add \"" + testKey + "\" /ve /t REG_SZ /d \"1\" /f");
            p1.waitFor(3, TimeUnit.SECONDS);
            if (p1.exitValue() != 0) return false;

            Process p2 = Runtime.getRuntime().exec("reg delete \"" + testKey + "\" /f");
            p2.waitFor(3, TimeUnit.SECONDS);
            return p2.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean tryElevateToSystem() {
        if (!isAdmin()) {
            Logger("INFO", "当前非管理员，无法尝试 SYSTEM 提权");
            return false;
        }
        if (isSystem()) {
            Logger("INFO", "当前已是 SYSTEM");
            return true;
        }
        if (!isRunningFromExe()) {
            Logger("INFO", "非 exe 运行，无法继续 SYSTEM 提权");
            return false;
        }

        String exePath = getExePath();
        if (!exePath.toLowerCase().endsWith(".exe")) {
            Logger("ERROR", "程序路径非 exe 后缀:" + exePath);
            return false;
        }

        String existingArgs = getCommandLineArgs(hasIllegalShutdownFlag());
        String fullCmdLine = '\''
                + (exePath + " " + existingArgs).replace("'", "''")
                + '\'';

        String psCmd = String.join("\r\n",
                "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8",
                "$ErrorActionPreference = 'Stop'",
                "$mod = Get-Module -ListAvailable -Name NtObjectManager",
                "if (-not $mod) {",
                "    $pp = Get-PackageProvider -ListAvailable | Where-Object {$_.Name -eq 'NuGet'}",
                "    if (-not $pp) { Install-PackageProvider -Name NuGet -Force -Scope CurrentUser }",
                "    $repo = Get-PSRepository -Name PSGallery -ErrorAction SilentlyContinue",
                "    if ($repo -and $repo.InstallationPolicy -ne 'Trusted') {",
                "        Set-PSRepository -Name PSGallery -InstallationPolicy Trusted",
                "    }",
                "    Install-Module -Name NtObjectManager -Force -Scope CurrentUser",
                "    if(-not $?) { Write-Output 'false'; exit }",
                "}",
                "Start-Win32ChildProcess '" + (exePath + " " + existingArgs).replace("'","''") + "' | Out-Null",
                "if(-not $?) { Write-Output 'false'; exit }",
                "Write-Output 'true'"
        );

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command", psCmd
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            String output;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line.trim());
                output = sb.toString();
            }

            boolean ok = p.waitFor(10, TimeUnit.SECONDS) && "true".equalsIgnoreCase(output);
            if (ok) {
                Logger("INFO", "SYSTEM 提权成功，准备退出旧进程");
                Thread.sleep(500);
                System.exit(0);
                return true;
            } else {
                Logger("WARNING", "SYSTEM 提权失败，保持管理员运行");
                Logger("WARNING", "命令行程序报告的命令输出: " + output);
                System.exit(1);
            }
        } catch (Exception e) {
            Logger("ERROR", "SYSTEM 提权异常: " + e.getMessage());
            System.exit(1);
        }
        return false;
    }

    /**
     * 请求管理员权限（如果需要）
     * 注意：游戏主进程保持在管理员权限，不再 Elevate 到 SYSTEM
     * 这样可以保证主题监听、输入法等功能正常工作
     * 需要 SYSTEM 权限的操作使用 SystemTaskRunner 临时执行
     */
    public static boolean requestAdminIfNeeded(boolean illegalShutdown) {
        if (!isAdmin() && isRunningFromExe()) {
            Logger("INFO", "尝试获取管理员权限中");
            if (!restartAsAdmin(illegalShutdown)) return false;
        }
        if (!isAdmin()) return false;

        // 不再 Elevate 到 SYSTEM，保持在管理员权限
        Logger("INFO", "游戏运行在管理员权限下（非 SYSTEM），主题监听和输入法正常工作");
        return true;
    }

    /**
     * 获取命令行参数（供 SystemTaskRunner 使用）
     */
    public static String getCommandLineArgs(boolean illegalShutdown) {
        try {
            StringBuilder args = new StringBuilder();

            args.append(ARG_ADMIN_RESTART).append(" ");

            if (illegalShutdown) {
                args.append(ARG_ILLEGAL_SHUTDOWN).append(" ");
            }

            List<String> originalArgs = getAllOriginalArguments();

            List<String> filteredArgs = originalArgs.stream()
                    .filter(arg -> {
                        if (arg.equals(ARG_ADMIN_RESTART) ||
                                arg.equals(ARG_ILLEGAL_SHUTDOWN) ||
                                arg.equals(ARG_SYSTEM_RESTART)) {
                            return false;
                        }
                        if (arg.endsWith(".exe") || arg.endsWith(".jar") || arg.contains("java")) {
                            return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            for (String arg : filteredArgs) {
                if (arg.contains(" ")) {
                    args.append("\"").append(arg).append("\" ");
                } else {
                    args.append(arg).append(" ");
                }
            }

            String result = args.toString().trim();
            Logger("DEBUG", "生成的命令行参数: " + result);
            return result;

        } catch (Exception e) {
            Logger("WARNING", "构建命令行参数时出错: " + e.getMessage());
            return illegalShutdown ?
                    ARG_ADMIN_RESTART + " " + ARG_ILLEGAL_SHUTDOWN :
                    ARG_ADMIN_RESTART;
        }
    }

    private static List<String> getAllOriginalArguments() {
        List<String> allArgs = new ArrayList<>();

        try {
            List<String> runtimeArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            allArgs.addAll(runtimeArgs);

            String sunJavaCommand = System.getProperty("sun.java.command", "");
            if (!sunJavaCommand.isEmpty()) {
                String[] parts = sunJavaCommand.split("\\s+", 2);
                if (parts.length > 1) {
                    List<String> commandArgs = parseCommandLine(parts[1]);
                    allArgs.addAll(commandArgs);
                }
            }

        } catch (Exception e) {
            Logger("WARNING", "获取原始参数时出错: " + e.getMessage());
        }

        return allArgs;
    }

    private static List<String> parseCommandLine(String commandLine) {
        List<String> args = new ArrayList<>();
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return args;
        }

        boolean inQuotes = false;
        StringBuilder currentArg = new StringBuilder();

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                if (i == 0 || commandLine.charAt(i-1) != '\\') {
                    continue;
                }
            }

            if (Character.isWhitespace(c) && !inQuotes) {
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0);
                }
            } else {
                currentArg.append(c);
            }
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        return args;
    }

    private static void notifyLauncherToCleanupLock() {
        try {
            Launcher.cleanupLock();
        } catch (Exception e) {
            Logger("WARNING", "通知清理锁文件时出错: " + e.getMessage());
        }
    }
}
