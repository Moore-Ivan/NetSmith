package com.netsmith;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 配置存储工具类
 * 使用 JSON 格式存储配置数据，保证版本兼容性
 * 配置文件存储在程序目录的 config 子目录中
 *
 * <p>并发模型：内存状态（settings/historyMap）用普通 HashMap，统一由单一 lock 保护
 * （此前 Properties 自带同步 + ConcurrentHashMap + historyLock 三层叠加纯属开销）；
 * 磁盘写入经 {@link #FLUSH_DELAY_MS} 防抖后由后台守护线程异步执行——
 * 快照在锁内完成、IO 在锁外进行，连续保存只在静默 200ms 后真正落盘一次；
 * JVM 退出时通过 shutdown hook 同步冲刷，防抖窗口内的改动不会丢失。</p>
 */
public class ConfigStore {

    private static final String HISTORY_FILE = "history.json";
    private static final String OLD_HISTORY_FILE = "history.dat";
    private static final String SETTINGS_FILE = "settings.json";
    private static final String OLD_SETTINGS_FILE = "settings.dat";
    private static final int MAX_HISTORY_SIZE = 10;
    /** 写盘防抖间隔：连续保存只在静默该时长后落盘一次，避免高频调用（如拖拽列宽）频繁 IO */
    private static final long FLUSH_DELAY_MS = 200;

    private static String configDirPath;

    // ========== 内存状态：统一由 lock 保护，调用方任意线程均可 ==========
    private static final Object lock = new Object();
    private static Map<String, String> settings = new HashMap<>();
    private static Map<String, List<String>> historyMap = new HashMap<>();

    // "历史已全部清除"通知监听器（如 HistoryComboBox 用于刷新自身下拉列表）
    private static final CopyOnWriteArrayList<Runnable> historyClearListeners = new CopyOnWriteArrayList<>();

    // ========== 防抖异步落盘：所有文件写入都串行在这条后台守护线程上 ==========
    private static final ScheduledExecutorService flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ConfigStore-flush");
        t.setDaemon(true);
        return t;
    });
    private static ScheduledFuture<?> settingsFlushTask;
    private static ScheduledFuture<?> historyFlushTask;
    private static boolean settingsDirty;
    private static boolean historyDirty;

    // Gson 实例，配置为美化输出便于人工阅读
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    static {
        initConfigDir();
        loadAll();
        // 退出时同步冲刷：防抖窗口内尚未落盘的改动在此兜底（等待后台线程写完，最多 5 秒）
        Runtime.getRuntime().addShutdownHook(new Thread(ConfigStore::flushAllNow, "ConfigStore-shutdown-flush"));
    }

    /**
     * 初始化配置目录，使用应用程序目录下的 config 文件夹
     */
    private static void initConfigDir() {
        configDirPath = "config";
        File dir = new File(configDirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private static String getConfigFile(String filename) {
        return configDirPath + File.separator + filename;
    }

    private static void loadAll() {
        loadSettings();
        loadHistory();
    }

    // 以下 load 系列仅在静态初始化中执行（单线程），无需加锁

    private static void loadSettings() {
        File jsonFile = new File(getConfigFile(SETTINGS_FILE));
        File oldFile = new File(getConfigFile(OLD_SETTINGS_FILE));

        // 优先尝试加载 JSON 文件
        if (jsonFile.exists()) {
            loadSettingsFromJson(jsonFile);
        } else if (oldFile.exists()) {
            // 如果没有 JSON 文件但有旧的 .dat 文件，尝试迁移
            loadSettingsFromOldFormat(oldFile);
            // 迁移后立即同步保存为 JSON 格式（静态初始化阶段，无并发）
            writeSettingsFile(new LinkedHashMap<>(settings));
            // 删除旧文件
            oldFile.delete();
        }
    }

    private static void loadSettingsFromJson(File file) {
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                settings.putAll(loaded);
            }
        } catch (Exception e) {
            // 捕获包含 JsonSyntaxException 等运行时异常：配置文件损坏时回退到默认配置，
            // 避免静态初始化失败导致应用启动即崩溃
            System.err.println("ConfigStore: 加载设置失败(已忽略, 使用默认配置): " + e.getMessage());
        }
    }

    private static void loadSettingsFromOldFormat(File file) {
        // 旧格式为 Properties 文本，读入临时对象后拷入统一管理的 settings
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(file)) {
            props.load(is);
            for (String name : props.stringPropertyNames()) {
                settings.put(name, props.getProperty(name));
            }
        } catch (IOException e) {
            System.err.println("ConfigStore: 加载旧格式设置失败: " + e.getMessage());
        }
    }

    private static void loadHistory() {
        File jsonFile = new File(getConfigFile(HISTORY_FILE));
        File oldFile = new File(getConfigFile(OLD_HISTORY_FILE));

        // 优先尝试加载 JSON 文件
        if (jsonFile.exists()) {
            loadHistoryFromJson(jsonFile);
        } else if (oldFile.exists()) {
            // 如果没有 JSON 文件但有旧的 .dat 文件，尝试迁移
            loadHistoryFromOldFormat(oldFile);
            // 迁移后立即同步保存为 JSON 格式（静态初始化阶段，无并发）
            writeHistoryFile(snapshotHistory());
            // 删除旧文件
            oldFile.delete();
        }
    }

    private static void loadHistoryFromJson(File file) {
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            Map<String, List<String>> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                historyMap.putAll(loaded);
            }
        } catch (Exception e) {
            // 捕获包含 JsonSyntaxException 等运行时异常：历史文件损坏时回退到空历史，
            // 避免静态初始化失败导致应用启动即崩溃
            System.err.println("ConfigStore: 加载历史失败(已忽略, 使用空历史): " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadHistoryFromOldFormat(File file) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, List<String>> loaded = (Map<String, List<String>>) ois.readObject();
            historyMap.putAll(loaded);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("ConfigStore: 加载旧格式历史失败: " + e.getMessage());
        }
    }

    // ========== 落盘：防抖调度（锁内） + IO（锁外） ==========

    /** 标记设置脏并调度防抖落盘；调用方必须已持有 lock */
    private static void scheduleSettingsFlush() {
        if (settingsFlushTask != null) {
            settingsFlushTask.cancel(false);
        }
        settingsDirty = true;
        settingsFlushTask = flushExecutor.schedule(ConfigStore::flushSettings, FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** 标记历史脏并调度防抖落盘；调用方必须已持有 lock */
    private static void scheduleHistoryFlush() {
        if (historyFlushTask != null) {
            historyFlushTask.cancel(false);
        }
        historyDirty = true;
        historyFlushTask = flushExecutor.schedule(ConfigStore::flushHistory, FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** 冲刷设置文件：锁内取快照并清脏标记，锁外写文件（由 flush 线程调用） */
    private static void flushSettings() {
        Map<String, String> snapshot;
        synchronized (lock) {
            if (!settingsDirty) {
                return;
            }
            settingsDirty = false;
            snapshot = new LinkedHashMap<>(settings);
        }
        writeSettingsFile(snapshot);
    }

    /** 冲刷历史文件：锁内取快照并清脏标记，锁外写文件（由 flush 线程调用） */
    private static void flushHistory() {
        Map<String, List<String>> snapshot;
        synchronized (lock) {
            if (!historyDirty) {
                return;
            }
            historyDirty = false;
            snapshot = snapshotHistory();
        }
        writeHistoryFile(snapshot);
    }

    /** 历史快照（锁内调用）：深拷贝为可序列化的普通集合 */
    private static Map<String, List<String>> snapshotHistory() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : historyMap.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    private static void writeSettingsFile(Map<String, String> settingsMap) {
        try (Writer writer = new FileWriter(getConfigFile(SETTINGS_FILE))) {
            gson.toJson(settingsMap, writer);
        } catch (IOException e) {
            System.err.println("ConfigStore: 保存设置失败: " + e.getMessage());
        }
    }

    private static void writeHistoryFile(Map<String, List<String>> history) {
        try (Writer writer = new FileWriter(getConfigFile(HISTORY_FILE))) {
            gson.toJson(history, writer);
        } catch (IOException e) {
            System.err.println("ConfigStore: 保存历史失败: " + e.getMessage());
        }
    }

    /**
     * 退出兜底：把所有未落盘的改动同步写盘。
     * 提交到同一条 flush 线程执行以串行化写入（避免与在途防抖任务并发写同一文件），
     * 最多等待 5 秒，超时则放弃（行为等同一次保存失败，仅打日志）。
     */
    private static void flushAllNow() {
        try {
            flushExecutor.submit(() -> {
                flushSettings();
                flushHistory();
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("ConfigStore: 退出冲刷配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取配置目录路径（用于调试）
     */
    public static String getConfigDirPath() {
        return configDirPath;
    }

    // ========== 主题模式操作 ==========

    public static void setThemeMode(String mode) {
        putSetting("theme.mode", mode);
    }

    public static String getThemeMode() {
        synchronized (lock) {
            return settings.getOrDefault("theme.mode", "system");
        }
    }

    // ========== 设置操作 ==========

    /** 统一写入入口：更新内存 + 调度防抖落盘（不阻塞调用线程） */
    private static void putSetting(String key, String value) {
        synchronized (lock) {
            settings.put(key, value);
            scheduleSettingsFlush();
        }
    }

    public static void setSetting(String key, String value) {
        putSetting(key, value);
    }

    public static String getSetting(String key, String defaultValue) {
        synchronized (lock) {
            return settings.getOrDefault(key, defaultValue);
        }
    }

    public static int getSetting(String key, int defaultValue) {
        synchronized (lock) {
            String value = settings.get(key);
            if (value != null) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    public static void setSetting(String key, int value) {
        putSetting(key, String.valueOf(value));
    }

    // ========== 历史记录操作 ==========

    public static void addHistory(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        value = value.trim();
        synchronized (lock) {
            List<String> history = historyMap.computeIfAbsent(fieldName, k -> new ArrayList<>());

            if (!history.isEmpty() && history.get(0).equals(value)) {
                return;
            }

            history.remove(value);
            history.add(0, value);
            if (history.size() > MAX_HISTORY_SIZE) {
                history.subList(MAX_HISTORY_SIZE, history.size()).clear();
            }
            scheduleHistoryFlush();
        }
    }

    /** 返回指定字段历史的快照副本，调用方可安全遍历，不受后续写入影响 */
    public static List<String> getHistory(String fieldName) {
        synchronized (lock) {
            List<String> list = historyMap.get(fieldName);
            return list != null ? new ArrayList<>(list) : new ArrayList<>();
        }
    }

    public static void clearHistory(String fieldName) {
        synchronized (lock) {
            if (historyMap.remove(fieldName) == null) {
                return; // 本来就没有该字段历史，无需落盘
            }
            scheduleHistoryFlush();
        }
    }

    public static void clearAllHistory() {
        synchronized (lock) {
            if (historyMap.isEmpty()) {
                return;
            }
            historyMap.clear();
            scheduleHistoryFlush();
        }
        fireHistoryCleared();
    }

    /**
     * 清除所有输入历史（不清除设置）
     */
    public static void clearAllInputHistory() {
        clearAllHistory();
    }

    // ========== 历史清除监听 ==========

    /** 注册"历史已全部清除"监听器，用于让已存在的输入框实例立即刷新下拉列表 */
    public static void addHistoryClearListener(Runnable listener) {
        if (listener != null) {
            historyClearListeners.addIfAbsent(listener);
        }
    }

    public static void removeHistoryClearListener(Runnable listener) {
        historyClearListeners.remove(listener);
    }

    private static void fireHistoryCleared() {
        for (Runnable listener : historyClearListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                System.err.println("ConfigStore: 历史清除监听器执行失败: " + e.getMessage());
            }
        }
    }

    // ========== 列宽保存 ==========

    public static void saveColumnWidths(String tableName, int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(widths[i]);
        }
        putSetting("columnWidths." + tableName, sb.toString());
    }

    public static int[] loadColumnWidths(String tableName, int[] defaults) {
        String value = getSetting("columnWidths." + tableName, null);
        if (value == null) {
            return defaults;
        }
        String[] parts = value.split(",");
        // 返回数组始终与 defaults 等长：配置项缺失或损坏时用默认值填充，
        // 避免调用方按 defaults 长度索引时出现 ArrayIndexOutOfBoundsException
        int[] widths = new int[defaults.length];
        for (int i = 0; i < defaults.length; i++) {
            if (i < parts.length) {
                try {
                    widths[i] = Integer.parseInt(parts[i]);
                    continue;
                } catch (NumberFormatException ignored) {
                }
            }
            widths[i] = defaults[i];
        }
        return widths;
    }
}
