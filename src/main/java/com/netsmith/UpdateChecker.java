package com.netsmith;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateChecker {

    private static final String REPO_OWNER = "Moore-Ivan";
    private static final String REPO_NAME = "NetSmith";
    private static final String API_URL =
            "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";
    private static final String RELEASES_PAGE_URL =
            "https://github.com/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";

    private static final String SETTING_LAST_CHECK = "update.lastCheckEpoch";
    private static final String SETTING_AUTO_CHECK = "update.autoCheck";
    private static final long AUTO_CHECK_INTERVAL_SECONDS = 24 * 60 * 60L;

    private static final String USER_AGENT =
            "NetSmith-Updater/" + VersionInfo.getVersion() + " (+" + RELEASES_PAGE_URL + ")";

    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "update-checker");
        t.setDaemon(true);
        return t;
    });

    /** 已转入后台的下载进度窗口：主窗口"检查更新"按钮据此恢复显示，避免后台下载后无入口查看进度 */
    private static volatile javax.swing.JDialog backgroundedDownloadDialog;
    /** 下载是否处于后台模式（downloadAndInstall 中的 backgrounded 即本对象的别名） */
    private static final java.util.concurrent.atomic.AtomicBoolean downloadBackgrounded =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private UpdateChecker() {
    }

    /**
     * 若存在已转入后台的下载进度窗口，则重新显示并返回 true；否则返回 false。
     * 主窗口"检查更新"按钮优先调用本方法，让用户能够重新查看后台下载进度。
     */
    public static boolean restoreBackgroundDownload() {
        javax.swing.JDialog d = backgroundedDownloadDialog;
        if (d == null || !d.isDisplayable()) {
            return false;
        }
        downloadBackgrounded.set(false);
        backgroundedDownloadDialog = null;
        d.setVisible(true);
        return true;
    }

    public static final class UpdateInfo {
        public final String version;
        public final String releaseDate;
        public final String downloadUrl;
        public final String releaseNotes;
        public final String htmlUrl;

        UpdateInfo(String version, String releaseDate, String downloadUrl,
                   String releaseNotes, String htmlUrl) {
            this.version = version;
            this.releaseDate = releaseDate;
            this.downloadUrl = downloadUrl;
            this.releaseNotes = releaseNotes;
            this.htmlUrl = htmlUrl;
        }
    }

    public static final class CheckResult {
        public final UpdateInfo update;
        public final String currentVersion;
        public final String latestVersion;
        public final String errorMessage;

        private CheckResult(UpdateInfo update, String currentVersion,
                            String latestVersion, String errorMessage) {
            this.update = update;
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.errorMessage = errorMessage;
        }

        public boolean hasUpdate() {
            return update != null;
        }

        public boolean hasError() {
            return errorMessage != null;
        }

        static CheckResult ok(UpdateInfo update, String current, String latest) {
            return new CheckResult(update, current, latest, null);
        }

        static CheckResult error(String current, String message) {
            return new CheckResult(null, current, null, message);
        }
    }

    public static boolean shouldAutoCheck() {
        if (!"true".equalsIgnoreCase(ConfigStore.getSetting(SETTING_AUTO_CHECK, "true"))) {
            return false;
        }
        long last;
        try {
            last = Long.parseLong(ConfigStore.getSetting(SETTING_LAST_CHECK, "0"));
        } catch (NumberFormatException e) {
            last = 0;
        }
        long now = System.currentTimeMillis() / 1000L;
        return (now - last) >= AUTO_CHECK_INTERVAL_SECONDS;
    }

    public static void checkAsync(Window owner, boolean manual, Consumer<String> statusCallback) {
        CompletableFuture.supplyAsync(UpdateChecker::checkSync, POOL)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> handleResult(owner, result, manual, statusCallback)))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        if (manual) {
                            showError(owner, "检查更新时发生意外错误: " + ex.getMessage());
                        } else {
                            System.err.println("UpdateChecker: 自动检查异常 - " + ex.getMessage());
                        }
                    });
                    return null;
                });
    }

    public static CheckResult checkSync() {
        String current = VersionInfo.getVersion();
        HttpURLConnection conn = null;
        try {
            conn = openConnection(API_URL);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code == 404) {
                return CheckResult.ok(null, current, current);
            }
            if (code != 200) {
                return CheckResult.error(current, "GitHub 接口返回状态码 " + code
                        + (code == 403 ? " (可能触发了速率限制，请稍后再试)" : ""));
            }

            String body = readResponse(conn);
            UpdateInfo info = parseRelease(body);
            if (info == null || info.downloadUrl == null) {
                return CheckResult.error(current, "未在最新 Release 中找到 .exe 安装包");
            }
            // 只有成功获取到有效 Release 信息才更新"上次检查时间"，
            // 避免网络抖动/限流后 24 小时内不再自动检查
            ConfigStore.setSetting(SETTING_LAST_CHECK, String.valueOf(System.currentTimeMillis() / 1000L));
            boolean newer = isNewer(info.version, current);
            return CheckResult.ok(newer ? info : null, current, info.version);
        } catch (javax.net.ssl.SSLException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("PKIX") || msg.contains("certificate") || msg.contains("CertPath")
                    || msg.contains("certificate_unknown")) {
                return CheckResult.error(current,
                        "无法安全连接到更新服务器：证书验证失败。\n\n"
                        + "可能原因：\n"
                        + "  • 系统证书库不完整或过期\n"
                        + "  • 企业网络代理 SSL 检查\n"
                        + "  • 系统时间不正确\n\n"
                        + "技术详情：" + msg);
            }
            return CheckResult.error(current, "网络请求失败: " + msg);
        } catch (Exception e) {
            return CheckResult.error(current, "网络请求失败: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void handleResult(Window owner, CheckResult result, boolean manual, Consumer<String> statusCallback) {
        if (result.hasError()) {
            if (manual) {
                showError(owner, result.errorMessage);
            } else {
                System.err.println("UpdateChecker: 自动检查失败 - " + result.errorMessage);
            }
            return;
        }
        if (result.hasUpdate()) {
            showUpdateDialog(owner, result.update, result.currentVersion, statusCallback);
        } else if (manual) {
            JOptionPane.showMessageDialog(owner,
                    "当前已是最新版本\n\n当前版本: v" + result.currentVersion
                            + (result.latestVersion != null ? "\n最新版本: v" + result.latestVersion : ""),
                    "已是最新版本", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void showError(Window owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "检查更新失败", JOptionPane.ERROR_MESSAGE);
    }

    private static void showUpdateDialog(Window owner, UpdateInfo info, String currentVersion, Consumer<String> statusCallback) {
        String date = formatDate(info.releaseDate);
        StringBuilder sb = new StringBuilder();
        sb.append("<html><div style='width:420px'>");
        sb.append("<b>发现新版本！</b><br><br>");
        sb.append("<b>当前版本:</b> v").append(escape(currentVersion)).append("<br>");
        sb.append("<b>最新版本:</b> v").append(escape(info.version)).append("<br>");
        if (!date.isEmpty()) {
            sb.append("<b>发布日期:</b> ").append(escape(date)).append("<br>");
        }
        sb.append("<br><b>更新内容:</b><br>");
        String notes = info.releaseNotes == null ? "" : info.releaseNotes.trim();
        if (notes.isEmpty()) {
            sb.append("<i>（作者未填写更新日志）</i>");
        } else {
            String html = notes
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\r\n", "\n").replace("\n", "<br>");
            sb.append(html);
        }
        sb.append("</div></html>");

        String[] options = {"立即更新", "稍后再说", "查看发布页"};
        int choice = JOptionPane.showOptionDialog(
                owner, sb.toString(), "发现新版本",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                options, options[0]);

        if (choice == 0) {
            downloadAndInstall(owner, info, statusCallback);
        } else if (choice == 2) {
            openInBrowser(info.htmlUrl);
        }
    }

    private static void downloadAndInstall(Window owner, UpdateInfo info, Consumer<String> statusCallback) {
        // 非模态对话框：不阻塞主窗口，用户可在下载过程中继续操作
        JDialog dialog = new JDialog((Frame) null, "正在下载更新", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("正在连接服务器...");

        JLabel statusLabel = new JLabel("下载新版本 v" + info.version + " 中...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        // 实时下载速率与剩余时间
        JLabel speedLabel = new JLabel("下载速度: 计算中...");
        speedLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        JLabel etaLabel = new JLabel("剩余时间: 计算中...");
        etaLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 5, 10));

        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 2, 2));
        infoPanel.add(statusLabel);
        infoPanel.add(speedLabel);
        infoPanel.add(etaLabel);

        // 后台下载 + 取消按钮
        JButton bgBtn = new JButton("后台下载");
        bgBtn.setToolTipText("隐藏下载窗口，在后台继续下载，可继续使用程序");
        JButton cancelBtn = new JButton("取消下载");
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnPanel.add(bgBtn);
        btnPanel.add(cancelBtn);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(infoPanel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setSize(460, 200);
        dialog.setLocationRelativeTo(owner);

        // 跨线程共享的下载状态: [0]=已下载字节 [1]=总字节
        final long[] dlState = new long[]{0, -1};
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        // 指向类级 downloadBackgrounded，restoreBackgroundDownload 恢复窗口时能同步清除后台状态
        final AtomicBoolean backgrounded = downloadBackgrounded;
        final HttpURLConnection[] connRef = new HttpURLConnection[1];

        // 速率采样定时器（EDT, 每 500ms）——独立于下载循环，保证恒定刷新频率
        final long[] lastSample = new long[]{0, 0}; // [nanoTime, bytes]
        final double[] smoothSpeed = new double[]{0};
        final javax.swing.Timer speedTimer = new javax.swing.Timer(500, e -> {
            long now = System.nanoTime();
            long current = dlState[0];
            long len = dlState[1];
            if (lastSample[0] == 0) { lastSample[0] = now; lastSample[1] = 0; return; }
            double elapsed = (now - lastSample[0]) / 1_000_000_000.0;
            if (elapsed < 0.1) return;
            double instant = (current - lastSample[1]) / elapsed;
            lastSample[0] = now; lastSample[1] = current;
            // 指数平滑，避免速率跳变
            smoothSpeed[0] = smoothSpeed[0] == 0 ? instant : smoothSpeed[0] * 0.6 + instant * 0.4;
            speedLabel.setText("下载速度: " + formatSpeed(smoothSpeed[0]));
            if (len > 0 && smoothSpeed[0] > 1) {
                long rem = len - current;
                etaLabel.setText("剩余时间: " + formatEta((long)(rem / smoothSpeed[0])));
            }
            // 后台模式：将进度推送到主窗口状态栏
            if (backgrounded.get() && len > 0) {
                int pct = (int) Math.min(100, current * 100 / len);
                statusCallback.accept("后台下载 " + pct + "%  " + formatSpeed(smoothSpeed[0]));
            }
        });
        speedTimer.start();

        // 后台下载（按钮或关闭窗口）→ 隐藏对话框，下载继续
        Runnable goBackground = () -> {
            backgrounded.set(true);
            backgroundedDownloadDialog = dialog; // 记录窗口，供"检查更新"恢复显示
            dialog.setVisible(false);
            statusCallback.accept("已转入后台下载，可继续使用程序（点击\"检查更新\"可恢复进度窗口）");
        };
        bgBtn.addActionListener(e -> goBackground.run());
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) { goBackground.run(); }
        });

        // 取消下载 → 中断连接，清理临时文件
        cancelBtn.addActionListener(e -> {
            cancelled.set(true);
            if (connRef[0] != null) { try { connRef[0].disconnect(); } catch (Exception ignored) {} }
            speedTimer.stop();
            backgroundedDownloadDialog = null;
            dialog.dispose();
            statusCallback.accept("已取消更新下载");
        });

        POOL.execute(() -> {
            Path tempFile = null;
            HttpURLConnection conn = null;
            try {
                tempFile = Files.createTempFile("ipcalc_update_", ".exe");
                final Path target = tempFile;

                conn = openConnection(info.downloadUrl);
                connRef[0] = conn;
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(60_000);
                conn.setInstanceFollowRedirects(true);
                conn.connect();
                if (cancelled.get()) return;

                int code = conn.getResponseCode();
                if (code != 200) {
                    throw new IOException("下载失败，HTTP 状态码: " + code);
                }

                String clHeader = conn.getHeaderField("Content-Length");
                final long contentLength;
                long tmpLen = -1L;
                if (clHeader != null) {
                    try {
                        tmpLen = Long.parseLong(clHeader.trim());
                    } catch (NumberFormatException ignored) {}
                }
                contentLength = tmpLen;
                dlState[1] = contentLength;

                try (InputStream in = conn.getInputStream();
                     OutputStream out = Files.newOutputStream(target,
                             java.nio.file.StandardOpenOption.CREATE,
                             java.nio.file.StandardOpenOption.WRITE,
                             java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buffer = new byte[64 * 1024];
                    long total = 0;
                    long lastUiTime = 0;
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (cancelled.get()) { in.close(); return; }
                        out.write(buffer, 0, n);
                        total += n;
                        dlState[0] = total;
                        // 节流 UI 更新：距上次刷新 ≥100ms 才 invokeLater，避免淹没 EDT
                        long now = System.nanoTime();
                        if (now - lastUiTime < 100_000_000L) continue;
                        lastUiTime = now;
                        final long done = total;
                        SwingUtilities.invokeLater(() -> {
                            if (contentLength > 0) {
                                int pct = (int) Math.min(100, done * 100 / contentLength);
                                progressBar.setValue(pct);
                                progressBar.setString(pct + "%  (" + formatSize(done) + " / " + formatSize(contentLength) + ")");
                            } else {
                                progressBar.setIndeterminate(true);
                                progressBar.setString("已下载 " + formatSize(done));
                            }
                        });
                    }
                }

                speedTimer.stop();
                backgroundedDownloadDialog = null; // 下载结束，"检查更新"不再恢复此窗口
                final Path installerPath = target;
                if (backgrounded.get()) {
                    // 后台完成 → 通知用户并询问是否立即安装
                    SwingUtilities.invokeLater(() -> {
                        statusCallback.accept("下载完成");
                        int choice = JOptionPane.showConfirmDialog(owner,
                                "<html>更新 v" + info.version + " 下载完成！<br><br>"
                                        + "点击\"确定\"立即安装并重启，\"取消\"稍后手动安装。",
                                "下载完成", JOptionPane.OK_CANCEL_OPTION,
                                JOptionPane.INFORMATION_MESSAGE);
                        if (choice == JOptionPane.OK_OPTION) {
                            try {
                                launchInstaller(installerPath);
                            } catch (IOException ex) {
                                showError(owner, "启动安装程序失败: " + ex.getMessage());
                                return;
                            }
                            exitApp();
                        } else {
                            statusCallback.accept("更新已下载到: " + installerPath);
                        }
                    });
                } else {
                    // 前台完成 → 自动安装
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(100);
                        progressBar.setString("下载完成，正在启动安装程序...");
                        statusLabel.setText("即将启动安装程序，请稍候...");
                        speedLabel.setText("");
                        etaLabel.setText("");
                    });
                    Thread.sleep(600);
                    launchInstaller(target);
                    SwingUtilities.invokeLater(() -> {
                        dialog.setVisible(false);
                        dialog.dispose();
                        exitApp();
                    });
                }
            } catch (Exception ex) {
                speedTimer.stop();
                backgroundedDownloadDialog = null;
                Path toDelete = tempFile;
                SwingUtilities.invokeLater(() -> {
                    if (!cancelled.get()) {
                        dialog.setVisible(false);
                        dialog.dispose();
                        showError(owner, "下载更新失败: " + ex.getMessage());
                    }
                });
                if (toDelete != null) {
                    try { Files.deleteIfExists(toDelete); } catch (IOException ignored) {}
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });

        dialog.setVisible(true);
    }

    private static void launchInstaller(Path installer) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        ProcessBuilder pb;
        if (os.contains("windows")) {
            pb = new ProcessBuilder(installer.toAbsolutePath().toString(), "/passive");
        } else {
            pb = new ProcessBuilder(installer.toAbsolutePath().toString());
        }
        pb.redirectErrorStream(true);
        pb.start();
    }

    private static void exitApp() {
        try {
            CalculationWorker.cancelAllTasks();
        } catch (Throwable ignored) {}
        System.exit(0);
    }

    private static HttpURLConnection openConnection(String urlStr) throws IOException {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);

        if (conn instanceof HttpsURLConnection) {
            try {
                // 组合信任管理器：JDK cacerts + Windows 系统根证书库
                // 解决企业代理 SSL 检查 / cacerts 不完整导致的 PKIX 验证失败，
                // 同时保持完整证书链校验（不会盲目信任所有证书）
                TrustManager[] tms = createCombinedTrustManagers();
                if (tms != null) {
                    SSLContext ctx = SSLContext.getInstance("TLS");
                    ctx.init(null, tms, null);
                    ((HttpsURLConnection) conn).setSSLSocketFactory(ctx.getSocketFactory());
                }
            } catch (GeneralSecurityException e) {
                System.err.println("UpdateChecker: SSL 初始化失败 - " + e.getMessage());
            }
        }
        return conn;
    }

    /**
     * 创建组合信任管理器：先尝试 JDK 默认信任库（cacerts），
     * 若失败再尝试 Windows 系统根证书库（SunMSCAPI）。
     * 任一通过即放行，不降低安全标准。
     */
    private static TrustManager[] createCombinedTrustManagers() {
        try {
            X509TrustManager defaultTm = null;
            try {
                TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                defaultTmf.init((KeyStore) null);
                defaultTm = findX509TrustManager(defaultTmf);
            } catch (Exception e) {
                System.err.println("UpdateChecker: JDK 默认信任库初始化失败 - " + e.getMessage());
            }

            X509TrustManager windowsTm = null;
            try {
                // Windows 系统根证书库：包含 OS 信任的所有根 CA 及企业自签名 CA
                Provider mscapi = Security.getProvider("SunMSCAPI");
                if (mscapi != null) {
                    KeyStore windowsRoot = KeyStore.getInstance("Windows-ROOT", mscapi);
                    windowsRoot.load(null, null);
                    TrustManagerFactory winTmf = TrustManagerFactory.getInstance("PKIX");
                    winTmf.init(windowsRoot);
                    windowsTm = findX509TrustManager(winTmf);
                }
            } catch (Exception e) {
                // SunMSCAPI 不可用（非 Windows 或模块缺失），静默忽略
            }

            if (defaultTm == null && windowsTm == null) return null;
            if (windowsTm == null) return new TrustManager[]{defaultTm};
            if (defaultTm == null) return new TrustManager[]{windowsTm};

            final X509TrustManager tm1 = defaultTm;
            final X509TrustManager tm2 = windowsTm;
            return new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    try {
                        tm1.checkClientTrusted(chain, authType);
                    } catch (CertificateException e1) {
                        tm2.checkClientTrusted(chain, authType);
                    }
                }
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    try {
                        tm1.checkServerTrusted(chain, authType);
                    } catch (CertificateException e1) {
                        tm2.checkServerTrusted(chain, authType);
                    }
                }
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return tm1.getAcceptedIssuers();
                }
            }};
        } catch (Exception e) {
            System.err.println("UpdateChecker: 无法创建组合信任管理器 - " + e.getMessage());
            return null;
        }
    }

    private static X509TrustManager findX509TrustManager(TrustManagerFactory tmf) {
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        return null;
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static UpdateInfo parseRelease(String body) {
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();

            if (obj.has("prerelease") && obj.get("prerelease").getAsBoolean()) return null;
            if (obj.has("draft") && obj.get("draft").getAsBoolean()) return null;

            String tag = getAsString(obj, "tag_name");
            if (tag == null) return null;
            String version = tag.startsWith("v") || tag.startsWith("V")
                    ? tag.substring(1) : tag;

            String publishedAt = getAsString(obj, "published_at");
            String htmlUrl = getAsString(obj, "html_url");
            String notes = getAsString(obj, "body");

            String downloadUrl = null;
            if (obj.has("assets") && obj.get("assets").isJsonArray()) {
                JsonArray assets = obj.getAsJsonArray("assets");
                String fallback = null;
                for (JsonElement e : assets) {
                    JsonObject a = e.getAsJsonObject();
                    String name = getAsString(a, "name");
                    if (name == null) continue;
                    String lower = name.toLowerCase();
                    if (!lower.endsWith(".exe")) continue;
                    String url = getAsString(a, "browser_download_url");
                    if (url == null) continue;
                    if (lower.contains("netsmith") || name.contains("网匠")) {
                        downloadUrl = url;
                        break;
                    }
                    if (fallback == null) {
                        fallback = url;
                    }
                }
                if (downloadUrl == null) {
                    downloadUrl = fallback;
                }
            }

            return new UpdateInfo(version, publishedAt == null ? "" : publishedAt,
                    downloadUrl, notes == null ? "" : notes,
                    htmlUrl == null ? RELEASES_PAGE_URL : htmlUrl);
        } catch (Exception e) {
            System.err.println("UpdateChecker: 解析 Release JSON 失败: " + e.getMessage());
            return null;
        }
    }

    private static String getAsString(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        JsonElement e = obj.get(key);
        return e.isJsonNull() ? null : e.getAsString();
    }

    public static boolean isNewer(String remote, String current) {
        try {
            String[] r = remote.trim().split("\\.");
            String[] c = current.trim().split("\\.");
            int len = Math.max(r.length, c.length);
            for (int i = 0; i < len; i++) {
                int ri = i < r.length ? safeParseInt(r[i]) : 0;
                int ci = i < c.length ? safeParseInt(c[i]) : 0;
                if (ri > ci) return true;
                if (ri < ci) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static int safeParseInt(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '0' && ch <= '9') {
                sb.append(ch);
            } else {
                break;
            }
        }
        if (sb.length() == 0) return 0;
        try {
            return Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String formatDate(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        int t = iso.indexOf('T');
        return t > 0 ? iso.substring(0, t) : iso;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** 格式化下载速率：自动在 B/s、KB/s、MB/s 之间切换 */
    private static String formatSpeed(double bytesPerSec) {
        if (bytesPerSec < 1024) return String.format("%.0f B/s", bytesPerSec);
        if (bytesPerSec < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSec / 1024.0);
        return String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024));
    }

    /** 格式化剩余时间 */
    private static String formatEta(long seconds) {
        if (seconds < 0 || seconds > 86400) return "计算中...";
        if (seconds < 60) return seconds + " 秒";
        return (seconds / 60) + " 分 " + (seconds % 60) + " 秒";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("windows")) {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (IOException ignored) {
        }
    }
}