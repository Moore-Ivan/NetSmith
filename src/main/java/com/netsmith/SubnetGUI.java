package com.netsmith;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubnetGUI extends JFrame implements ResultView {

    // 输出组件
    private ResultTablePanel resultTablePanel;
    private SubnetTablePanel tablePanel;
    private CardLayout cardLayout;
    private JPanel outputPanel;
    private JTabbedPane tabbedPane;
    private JLabel statusLabel;
    private JLabel memoryLabel;
    private javax.swing.Timer memoryTimer;
    /** 内存显示刷新间隔：内存展示无实时性要求，2s 间隔足以感知趋势并降低定时开销 */
    private static final int MEMORY_REFRESH_INTERVAL_MS = 2000;
    /** 堆内存监控 Bean（供底部状态栏显示） */
    private final java.lang.management.MemoryMXBean memoryMXBean =
            java.lang.management.ManagementFactory.getMemoryMXBean();
    private boolean isFullscreen = false;
    // 当前主题模式: "system"(跟随系统) / "light" / "dark"，volatile 供后台检测线程读取
    private volatile String themeMode = "system";
    private JButton themeBtn;
    private GraphicsDevice graphicsDevice;
    private volatile boolean isClosing = false;
    private Rectangle savedBounds;
    /** 系统深浅色检测线程（仅跟随系统模式下轮询） */
    private Thread systemThemeWatcher;
    /** 主题检测间隔（毫秒）：reg query 为外部进程调用（约 50-100ms/次），30s 兼顾跟随延迟与开销 */
    private static final int THEME_CHECK_INTERVAL_MS = 30_000;

    public SubnetGUI() {
        // 先从配置读取主题模式并应用（main 中已设置过一次，此处确保字段与配置同步）
        loadTheme();
        setTitle("网匠 " + VersionInfo.getDisplayVersion() + " (IPv4 + IPv6)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        loadAppIcon();
        setSize(800, 580);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        // 启动系统深浅色变化检测（跟随系统模式下自动切换主题）
        startSystemThemeWatcher();

        graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                isClosing = true;
                CalculationWorker.cancelAllTasks();
                cleanup();
            }
        });

        // 【启动优化】标签页懒加载：仅构建首屏可见面板，其余面板在首次切换到对应标签时才创建，
        // 避免启动时一次性构建 19+ 业务面板（及其历史记录下拉加载）造成的窗口延迟
        tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        installLazyTabLoading(tabbedPane);
        addLazyTab(tabbedPane, "IPv4 基础", this::buildIpv4BasicTab);
        addLazyTab(tabbedPane, "IPv4 子网划分", this::buildIpv4SubnetTab);
        addLazyTab(tabbedPane, "IPv4 高级工具", this::buildIpv4AdvancedTab);
        addLazyTab(tabbedPane, "IPv6 基础", this::buildIpv6BasicTab);
        addLazyTab(tabbedPane, "IPv6 子网划分", this::buildIpv6SubnetTab);
        addLazyTab(tabbedPane, "IPv6 高级工具", this::buildIpv6AdvancedTab);

        // 输出区域
        resultTablePanel = new ResultTablePanel();
        
        tablePanel = new SubnetTablePanel();
        tablePanel.setStatusCallback(this::setStatus);

        cardLayout = new CardLayout();
        outputPanel = new JPanel(cardLayout);
        outputPanel.add(resultTablePanel, "TEXT");
        outputPanel.add(tablePanel, "TABLE");

        // 底部按钮栏 + 状态栏
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearBtn = new JButton("清空结果");
        clearBtn.setToolTipText("清空结果区域 (Ctrl+L)");
        clearBtn.setMnemonic(KeyEvent.VK_L);
        clearBtn.addActionListener(e -> {
            resultTablePanel.clearData();
            tablePanel.clearData();
        });

        JButton copyBtn = new JButton("复制结果");
        copyBtn.setToolTipText("复制结果到剪贴板 (Ctrl+C)");
        copyBtn.setMnemonic(KeyEvent.VK_C);
        copyBtn.addActionListener(e -> copyResults());
        
        JButton contactBtn = new JButton("联系作者");
        contactBtn.setToolTipText("点击查看作者联系方式");
        contactBtn.addActionListener(e -> {
            boolean opened = ContactAuthor.showContactInfo();
            if (opened) {
                setStatus("已打开联系作者页面");
            } else {
                setStatus("无法打开联系作者页面", true);
            }
        });

        JButton shortcutBtn = new JButton("快捷键说明");
        shortcutBtn.setToolTipText("查看所有快捷键说明 (F1)");
        shortcutBtn.addActionListener(e -> ShortcutHelp.showShortcuts(this));

        JButton clearHistoryBtn = new JButton("清除历史");
        clearHistoryBtn.setToolTipText("清除所有输入框历史记录");
        clearHistoryBtn.addActionListener(e -> {
            ConfigStore.clearAllInputHistory();
            // 各 HistoryComboBox 通过历史清除监听器即时刷新下拉列表
            setStatus("已清除所有输入历史");
        });

        JButton baseConvertBtn = new JButton("进制转换");
        baseConvertBtn.setToolTipText("打开进制转换器 (F3)");
        baseConvertBtn.addActionListener(e -> {
            setStatus("已打开进制转换器");
            NumberBaseConverter.showConverter(this);
        });

        JButton maskConvertBtn = new JButton("掩码转换");
        maskConvertBtn.setToolTipText("打开掩码转换器 (F4)");
        maskConvertBtn.addActionListener(e -> {
            setStatus("已打开掩码转换器");
            MaskConverter.showConverter(this);
        });

        themeBtn = new JButton("切换到亮色");
        themeBtn.setToolTipText("切换主题模式：跟随系统 → 亮色 → 暗色（F2）");
        themeBtn.addActionListener(e -> toggleTheme());

        JButton updateBtn = new JButton("检查更新");
        updateBtn.setToolTipText("检查是否有新版本 (F9)");
        updateBtn.setMnemonic(KeyEvent.VK_U);
        updateBtn.addActionListener(e -> {
            // 若更新下载已转入后台，点击"检查更新"恢复显示下载进度窗口
            if (UpdateChecker.restoreBackgroundDownload()) {
                setStatus("已恢复下载进度窗口");
                return;
            }
            setStatus("正在检查更新...");
            UpdateChecker.checkAsync(SubnetGUI.this, true, SubnetGUI.this::setStatus);
        });

        buttonPanel.add(contactBtn);
        buttonPanel.add(shortcutBtn);
        buttonPanel.add(themeBtn);
        buttonPanel.add(updateBtn);
        buttonPanel.add(clearHistoryBtn);
        buttonPanel.add(baseConvertBtn);
        buttonPanel.add(maskConvertBtn);
        buttonPanel.add(clearBtn);
        buttonPanel.add(copyBtn);

        statusLabel = new JLabel(" 就绪");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        
        memoryLabel = new JLabel();
        memoryLabel.setBorder(BorderFactory.createEtchedBorder());
        updateMemoryUsage();
        
        JPanel statusPanel = new JPanel(new BorderLayout(5, 0));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(memoryLabel, BorderLayout.EAST);
        
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        bottomPanel.add(statusPanel, BorderLayout.SOUTH);
        
        memoryTimer = new javax.swing.Timer(MEMORY_REFRESH_INTERVAL_MS, e -> updateMemoryUsage());
        memoryTimer.start();

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 5, 10));
        mainPanel.add(tabbedPane, BorderLayout.NORTH);
        mainPanel.add(outputPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        setupGlobalShortcuts();
        updateThemeButtonText();
        cardLayout.show(outputPanel, "TEXT");
    }

    // ========== 标签页懒加载 ==========

    /** 占位面板 clientProperty 键：存储真实面板的延迟构建工厂 */
    private static final Object LAZY_TAB_FACTORY = new Object();

    /** 向标签页注册一个懒加载标签：先放占位面板，首次选中时才调用工厂构建真实面板 */
    private void addLazyTab(JTabbedPane pane, String title, Supplier<? extends Component> factory) {
        JPanel placeholder = new JPanel(new BorderLayout());
        placeholder.putClientProperty(LAZY_TAB_FACTORY, factory);
        pane.addTab(title, placeholder);
    }

    /** 监听标签切换：选中的若是占位面板则立即替换为真实面板（支持嵌套标签页） */
    private void installLazyTabLoading(JTabbedPane pane) {
        pane.addChangeListener(e -> buildSelectedTab(pane));
    }

    private void buildSelectedTab(JTabbedPane pane) {
        Component selected = pane.getSelectedComponent();
        if (!(selected instanceof JComponent)) {
            return;
        }
        Object factory = ((JComponent) selected).getClientProperty(LAZY_TAB_FACTORY);
        if (factory instanceof Supplier) {
            int index = pane.getSelectedIndex();
            @SuppressWarnings("unchecked")
            Component real = ((Supplier<Component>) factory).get();
            // setComponentAt 保留标签标题与选中状态，替换过程无闪烁
            pane.setComponentAt(index, real);
            // 嵌套场景：新构建的分组容器本身是标签页时，确保其选中子标签也已构建
            if (real instanceof JTabbedPane) {
                buildSelectedTab((JTabbedPane) real);
            }
        }
    }

    private JTabbedPane createSubTabbedPane() {
        JTabbedPane inner = new JTabbedPane();
        // 滚动布局：窄窗口下标签自动收纳为滚动箭头，避免换行堆叠挤压输出区
        inner.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        installLazyTabLoading(inner);
        return inner;
    }

    private JTabbedPane buildIpv4BasicTab() {
        JTabbedPane inner = createSubTabbedPane();
        addLazyTab(inner, "网段详情", () -> new DetailPanel(this, this::setStatus));
        addLazyTab(inner, "IP 地址解析", () -> new IPParsePanel(this, this::setStatus));
        addLazyTab(inner, "IP 范围→CIDR", () -> new RangeToCidrPanel(this, this::setStatus));
        buildSelectedTab(inner);
        return inner;
    }

    private JTabbedPane buildIpv4SubnetTab() {
        JTabbedPane inner = createSubTabbedPane();
        addLazyTab(inner, "等长子网", () -> new EqualSubnetPanel(this, this::setStatus));
        addLazyTab(inner, "VLSM 变长子网", () -> new VlsmPanel(this, this::setStatus));
        addLazyTab(inner, "超网拆分", () -> new SupernetSplitPanel(this, this::setStatus));
        buildSelectedTab(inner);
        return inner;
    }

    private JTabbedPane buildIpv4AdvancedTab() {
        JTabbedPane inner = createSubTabbedPane();
        addLazyTab(inner, "路由汇总", () -> new SummaryPanel(this, this::setStatus));
        addLazyTab(inner, "冲突检测", () -> new OverlapPanel(this, this::setStatus));
        addLazyTab(inner, "网络规划向导", () -> new PlanPanel(this, this::setStatus));
        addLazyTab(inner, "随机 IP 地址池", () -> new RandomIpPoolPanel(this, this::setStatus));
        addLazyTab(inner, "ACL 策略检查", () -> new AclCheckPanel(this, this::setStatus));
        addLazyTab(inner, "NAT 配置辅助", () -> new NatHelperPanel(this, this::setStatus));
        buildSelectedTab(inner);
        return inner;
    }

    private JTabbedPane buildIpv6BasicTab() {
        JTabbedPane inner = createSubTabbedPane();
        addLazyTab(inner, "网段详情", () -> new IPv6DetailPanel(this, this::setStatus));
        addLazyTab(inner, "地址解析", () -> new IPv6ParsePanel(this, this::setStatus));
        addLazyTab(inner, "地址范围→CIDR", () -> new IPv6RangePanel(this, this::setStatus));
        buildSelectedTab(inner);
        return inner;
    }

    private JTabbedPane buildIpv6SubnetTab() {
        JTabbedPane inner = createSubTabbedPane();
        addLazyTab(inner, "子网划分", () -> new IPv6SubnetPanel(this, this::setStatus));
        addLazyTab(inner, "VLSM 变长子网", () -> new IPv6VlsmPanel(this, this::setStatus));
        buildSelectedTab(inner);
        return inner;
    }

    private JTabbedPane buildIpv6AdvancedTab() {
        JTabbedPane inner = createSubTabbedPane();
        addLazyTab(inner, "路由汇总", () -> new IPv6SummaryPanel(this, this::setStatus));
        addLazyTab(inner, "EUI-64 地址生成", () -> new IPv6EUI64Panel(this, this::setStatus));
        addLazyTab(inner, "多播地址解析", () -> new IPv6MulticastPanel(this, this::setStatus));
        addLazyTab(inner, "DHCPv6 前缀委派", () -> new IPv6PrefixDelegationPanel(this, this::setStatus));
        addLazyTab(inner, "IPv4 映射 IPv6", () -> new IPv4MappedPanel(this, this::setStatus));
        buildSelectedTab(inner);
        return inner;
    }

    // ========== 全局快捷键 ==========

    private void setupGlobalShortcuts() {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();

        KeyStroke ctrlL = KeyStroke.getKeyStroke(KeyEvent.VK_L, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        inputMap.put(ctrlL, "clearResults");
        actionMap.put("clearResults", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resultTablePanel.clearData();
                tablePanel.clearData();
            }
        });

        // Ctrl+Shift+L：一键清空全部历史与结果（含输入历史，结果区切回文本卡）
        KeyStroke ctrlShiftL = KeyStroke.getKeyStroke(KeyEvent.VK_L,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK);
        inputMap.put(ctrlShiftL, "clearAllHistoryAndResults");
        actionMap.put("clearAllHistoryAndResults", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resultTablePanel.clearData();
                tablePanel.clearData();
                cardLayout.show(outputPanel, "TEXT");
                ConfigStore.clearAllHistory();
                setStatus("已清空全部历史与结果");
            }
        });

        KeyStroke ctrlC = KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        inputMap.put(ctrlC, "copyResults");
        actionMap.put("copyResults", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyResults();
            }
        });

        KeyStroke f11 = KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0);
        inputMap.put(f11, "toggleFullscreen");
        actionMap.put("toggleFullscreen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleFullscreen();
            }
        });

        KeyStroke f1 = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0);
        inputMap.put(f1, "showShortcuts");
        actionMap.put("showShortcuts", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ShortcutHelp.showShortcuts(SubnetGUI.this);
            }
        });

        KeyStroke f2Key = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0);
        inputMap.put(f2Key, "toggleTheme");
        actionMap.put("toggleTheme", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleTheme();
            }
        });

        KeyStroke f3Key = KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0);
        inputMap.put(f3Key, "showBaseConverter");
        actionMap.put("showBaseConverter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setStatus("已打开进制转换器");
                NumberBaseConverter.showConverter(SubnetGUI.this);
            }
        });

        KeyStroke f4Key = KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0);
        inputMap.put(f4Key, "showMaskConverter");
        actionMap.put("showMaskConverter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setStatus("已打开掩码转换器");
                MaskConverter.showConverter(SubnetGUI.this);
            }
        });

        KeyStroke f10Key = KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0);
        inputMap.put(f10Key, "showContactAuthor");
        actionMap.put("showContactAuthor", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean opened = ContactAuthor.showContactInfo();
                if (opened) {
                    setStatus("已打开联系作者页面");
                } else {
                    setStatus("无法打开联系作者页面", true);
                }
            }
        });

        KeyStroke f9Key = KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0);
        inputMap.put(f9Key, "checkUpdate");
        actionMap.put("checkUpdate", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setStatus("正在检查更新...");
                UpdateChecker.checkAsync(SubnetGUI.this, true, SubnetGUI.this::setStatus);
            }
        });

        // Ctrl+PageDown / Ctrl+PageUp：在六大功能分组间快速切换（与浏览器/编辑器标签切换习惯一致）
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, menuMask), "nextTabGroup");
        actionMap.put("nextTabGroup", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int count = tabbedPane.getTabCount();
                if (count > 1) {
                    tabbedPane.setSelectedIndex((tabbedPane.getSelectedIndex() + 1) % count);
                }
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, menuMask), "prevTabGroup");
        actionMap.put("prevTabGroup", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int count = tabbedPane.getTabCount();
                if (count > 1) {
                    tabbedPane.setSelectedIndex((tabbedPane.getSelectedIndex() - 1 + count) % count);
                }
            }
        });
    }

    /**
     * 从 classpath 加载应用图标（/NetSmith.ico）。
     * Temurin/OpenJDK 21 未内置 ICO ImageIO 读取器，因此手工解析 ICO 容器：
     * 每帧为内嵌 PNG 或 BMP(DIB)，解码出全部尺寸后交给 setIconImages，
     * 窗口标题栏、任务栏、Alt-Tab 切换界面会自动选用合适分辨率。
     */
    private void loadAppIcon() {
        try (InputStream is = getClass().getResourceAsStream("/NetSmith.ico")) {
            if (is == null) {
                System.err.println("未找到图标资源: /NetSmith.ico");
                return;
            }
            List<Image> icons = parseIco(is.readAllBytes());
            if (!icons.isEmpty()) {
                setIconImages(icons);
            } else {
                System.err.println("图标文件中未解析出任何帧: /NetSmith.ico");
            }
        } catch (Exception e) {
            System.err.println("加载应用图标失败: " + e.getMessage());
        }
    }

    /** 解析 ICO 容器，返回全部帧图像 */
    private static List<Image> parseIco(byte[] data) {
        List<Image> icons = new ArrayList<>();
        // ICONDIR: reserved(2)=0, type(2)=1, count(2)
        if (data.length < 6 || u16le(data, 0) != 0 || u16le(data, 2) != 1) return icons;
        int count = u16le(data, 4);
        for (int i = 0; i < count; i++) {
            int e = 6 + i * 16; // ICONDIRENTRY 每项 16 字节
            if (e + 16 > data.length) break;
            long size = u32le(data, e + 8);
            long offset = u32le(data, e + 12);
            if (size <= 0 || offset < 0 || offset + size > data.length) continue;
            Image img = decodeIcoFrame(data, (int) offset, (int) size);
            if (img != null) icons.add(img);
        }
        return icons;
    }

    /** 解码单个 ICO 帧：内嵌 PNG 直接读；BMP DIB 补 14 字节 BITMAPFILEHEADER 后读 */
    private static Image decodeIcoFrame(byte[] data, int offset, int size) {
        try {
            // PNG 签名: 89 50 4E 47 0D 0A 1A 0A
            if (size >= 8 && (data[offset] & 0xFF) == 0x89
                    && data[offset + 1] == 'P' && data[offset + 2] == 'N'
                    && data[offset + 3] == 'G') {
                return ImageIO.read(new java.io.ByteArrayInputStream(data, offset, size));
            }
            // BITMAPINFOHEADER：biSize(4) biWidth(4) biHeight(4) biPlanes(2) biBitCount(2)
            // biCompression(4) biSizeImage(4) ... biClrUsed(4 @32)
            if (size < 40) return null;
            int headerSize = (int) u32le(data, offset);
            int width = (int) u32le(data, offset + 4);
            int dibHeight = (int) u32le(data, offset + 8);
            int bitCount = u16le(data, offset + 14);
            long clrUsed = u32le(data, offset + 32);
            // ICO 的 BMP 帧 biHeight 通常是实际高度的 2 倍（上半像素数据 + 下半 AND 掩码）
            int height = dibHeight >= 2 * width ? dibHeight / 2 : dibHeight;
            if (width <= 0 || height <= 0 || bitCount <= 0) return null;

            long paletteColors = clrUsed > 0 ? clrUsed : (bitCount <= 8 ? 1L << bitCount : 0);
            int paletteSize = (int) paletteColors * 4;
            int pixelOffset = headerSize + paletteSize;
            int rowSize = ((width * bitCount + 31) / 32) * 4; // 每行按 4 字节对齐
            int pixelSize = rowSize * height; // 只取颜色数据，丢弃尾部 AND 掩码
            int totalSize = 14 + pixelOffset + pixelSize;

            byte[] bmp = new byte[totalSize];
            bmp[0] = 'B';
            bmp[1] = 'M';
            putU32le(bmp, 2, totalSize);
            putU32le(bmp, 6, 0);
            putU32le(bmp, 10, 14 + pixelOffset);
            int copy = Math.min(size, totalSize - 14);
            System.arraycopy(data, offset, bmp, 14, copy);
            // 将 DIB 头中的 biHeight 改为实际高度（覆盖位于 14+8 的 4 字节）
            putU32le(bmp, 14 + 8, height);

            return ImageIO.read(new java.io.ByteArrayInputStream(bmp));
        } catch (Exception ex) {
            System.err.println("解析 ICO 帧失败: " + ex.getMessage());
            return null;
        }
    }

    private static int u16le(byte[] d, int o) {
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8);
    }

    private static long u32le(byte[] d, int o) {
        return (d[o] & 0xFFL) | ((d[o + 1] & 0xFFL) << 8)
                | ((d[o + 2] & 0xFFL) << 16) | ((d[o + 3] & 0xFFL) << 24);
    }

    private static void putU32le(byte[] d, int o, long v) {
        d[o] = (byte) v;
        d[o + 1] = (byte) (v >> 8);
        d[o + 2] = (byte) (v >> 16);
        d[o + 3] = (byte) (v >> 24);
    }

    private void toggleFullscreen() {
        // 注意：不能调用 setUndecorated()——该方法要求窗口不可显示（not displayable），
        // 对已显示的窗口调用会抛 IllegalComponentStateException，导致 F11 全屏完全失效。
        // 直接使用独占全屏模式（setFullScreenWindow），退出时恢复原窗口边界。
        if (isFullscreen) {
            // 退出全屏模式：恢复进入全屏前的窗口尺寸和位置
            graphicsDevice.setFullScreenWindow(null);
            if (savedBounds != null) {
                setBounds(savedBounds);
            } else {
                pack();
            }
        } else {
            // 进入全屏模式：保存当前窗口尺寸和位置
            savedBounds = getBounds();
            graphicsDevice.setFullScreenWindow(this);
        }
        isFullscreen = !isFullscreen;
    }

    private void cleanup() {
        if (memoryTimer != null) {
            memoryTimer.stop();
            memoryTimer = null;
        }
        if (systemThemeWatcher != null) {
            systemThemeWatcher.interrupt();
            systemThemeWatcher = null;
        }
    }

    // ========== 系统深浅色检测（跟随系统模式） ==========

    /**
     * 启动后台检测线程，每 30 秒检测一次系统深浅色；仅跟随系统模式下自动切换主题。
     * <p>为何轮询而非监听 WM_SETTINGCHANGE 广播：Swing/AWT 未提供接收该广播的原生通道，
     * 需要 JNI/JNA 挂钩或隐藏原生窗口，复杂度与崩溃风险远超收益；轮询是纯 AWT 可实现的
     * 最简可靠方案。每次 reg query 耗时约 50-100ms（外部进程），因此：
     * 1) 间隔取 30 秒（主题切换是低频操作，30s 内跟随无感知问题）；
     * 2) 窗口不可见时跳过检测，恢复可见后下一轮（≤30s）自动补检；
     * 3) 深浅色值与上轮相同（绝大多数轮次）时零 UI 操作。
     */
    private void startSystemThemeWatcher() {
        if (systemThemeWatcher != null) {
            return;
        }
        systemThemeWatcher = new Thread(() -> {
            boolean lastDark = isSystemDark();
            while (true) {
                try {
                    Thread.sleep(THEME_CHECK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                // 非跟随系统模式时不检测，避免无谓的系统调用开销
                if (!"system".equals(themeMode)) {
                    continue;
                }
                // 窗口最小化/不可见时跳过检测，恢复可见后下一轮（≤10s）自动补检
                if (!isShowing()) {
                    continue;
                }
                boolean dark = isSystemDark();
                if (dark == lastDark) {
                    continue;
                }
                lastDark = dark;
                SwingUtilities.invokeLater(() -> applySystemTheme(dark));
            }
        }, "system-theme-watcher");
        systemThemeWatcher.setDaemon(true);
        systemThemeWatcher.start();
    }

    /** 系统深浅色变化后，在 EDT 上自动应用对应主题 */
    private void applySystemTheme(boolean dark) {
        if (!"system".equals(themeMode)) {
            return;
        }
        try {
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
            SwingUtilities.updateComponentTreeUI(this);
            updateThemeButtonText();
            setStatus(dark ? "系统为深色模式，已自动切换到暗色主题" : "系统为浅色模式，已自动切换到亮色主题");
        } catch (Exception e) {
            System.err.println("跟随系统自动切换主题失败: " + e.getMessage());
        }
    }

    /** 检测操作系统当前是否为深色模式，检测失败时按浅色处理 */
    static boolean isSystemDark() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                return isWindowsDark();
            }
            if (os.contains("mac")) {
                // macOS: 桌面属性值形如 "NSAppearanceNameDarkAqua" / "NSAppearanceNameAqua"
                Object appearance = Toolkit.getDefaultToolkit()
                        .getDesktopProperty("apple.awt.application.appearance");
                return appearance instanceof String s && s.contains("Dark");
            }
            if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                // Linux/GNOME: 优先 color-scheme，回退 GTK 主题名
                if (execCommand("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
                        .contains("dark")) {
                    return true;
                }
                return execCommand("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")
                        .contains("dark");
            }
        } catch (Exception e) {
            System.err.println("检测系统主题失败: " + e.getMessage());
        }
        return false;
    }

    /** Windows 注册表主题值解析模式（预编译复用，避免每次轮询重复编译正则） */
    private static final Pattern WIN_THEME_PATTERN =
            Pattern.compile("AppsUseLightTheme\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)");

    /** 读取 Windows 注册表 AppsUseLightTheme：0=深色模式，1=浅色模式（Win10 1809+） */
    private static boolean isWindowsDark() {
        String output = execCommand("reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme");
        Matcher m = WIN_THEME_PATTERN.matcher(output);
        return m.find() && Integer.parseInt(m.group(1), 16) == 0;
    }

    /**
     * 执行外部命令并返回其标准输出（失败返回空串，最多等待 2 秒）。
     * 关键修复：显式关闭子进程的 stdin/stdout/stderr 流——否则管道句柄会随每次轮询不断堆积，
     * 造成操作系统句柄/内存持续增长（"跟随系统"模式下主题检测线程每 10 秒调用一次，窗口不可见时跳过）。
     */
    private static String execCommand(String... command) {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            p = pb.start();
            closeQuietly(p.getOutputStream()); // 关闭子进程标准输入，避免其等待输入而挂起
            String output;
            try (java.io.InputStream in = p.getInputStream()) {
                output = new String(in.readAllBytes());
            }
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
            return output;
        } catch (Exception e) {
            return "";
        } finally {
            if (p != null) {
                // 兜底：关闭所有流并确保进程结束，彻底释放 OS 句柄资源
                closeQuietly(p.getOutputStream());
                closeQuietly(p.getErrorStream());
                closeQuietly(p.getInputStream());
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
            }
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignore) {
            }
        }
    }

    private void loadTheme() {
        themeMode = ConfigStore.getThemeMode();
        applyThemeForMode(themeMode);
    }

    /** 按主题模式安装对应 LookAndFeel：system 模式下根据系统深浅色自动选择 */
    private static void applyThemeForMode(String mode) {
        try {
            boolean dark;
            switch (mode) {
                case "dark" -> dark = true;
                case "light" -> dark = false;
                default -> dark = isSystemDark(); // system 或未知值：跟随系统
            }
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
        } catch (Exception e) {
            System.err.println("加载主题失败: " + e.getMessage());
        }
    }

    private void toggleTheme() {
        // 三种主题模式循环切换：跟随系统 -> 亮色 -> 暗色 -> 跟随系统
        String oldMode = themeMode;
        String newMode = switch (themeMode) {
            case "light" -> "dark";
            case "dark" -> "system";
            default -> "light";
        };
        try {
            themeMode = newMode;
            ConfigStore.setThemeMode(newMode);
            applyThemeForMode(newMode);
            SwingUtilities.updateComponentTreeUI(this);
            updateThemeButtonText();
            setStatus(switch (newMode) {
                case "dark" -> "已切换到暗色主题";
                case "light" -> "已切换到亮色主题";
                default -> "已切换为跟随系统主题";
            });
        } catch (Exception e) {
            setStatus("切换主题失败: " + e.getMessage(), true);
            // 切换失败时恢复原模式，并按原模式重装外观
            themeMode = oldMode;
            ConfigStore.setThemeMode(oldMode);
            applyThemeForMode(oldMode);
            SwingUtilities.updateComponentTreeUI(this);
            updateThemeButtonText();
        }
    }

    private void updateThemeButtonText() {
        if (themeBtn != null) {
            // 按钮文字统一表示"点击后将切换到的目标模式"，语义一致：
            // system → 亮色，light → 暗色，dark → 跟随系统（切换循环见 toggleTheme）
            switch (themeMode) {
                case "dark":
                    themeBtn.setText("切换到跟随系统");
                    break;
                case "light":
                    themeBtn.setText("切换到暗色");
                    break;
                case "system":
                    themeBtn.setText("切换到亮色");
                    break;
                default:
                    themeBtn.setText("主题");
            }
        }
    }
    
    // ========== ResultView 实现：统一结果输出入口，集中调度 TEXT/TABLE 卡片 ==========

    @Override
    public void showText(String text) {
        SwingUtilities.invokeLater(() -> {
            if (isClosing || !isDisplayable()) return;
            resultTablePanel.setText(text);
            cardLayout.show(outputPanel, "TEXT");
            setStatus("计算完成");
        });
    }

    @Override
    public void showTable(List<SubnetTablePanel.SubnetRow> data) {
        SwingUtilities.invokeLater(() -> {
            if (isClosing || !isDisplayable()) return;
            tablePanel.setData(data);
            cardLayout.show(outputPanel, "TABLE");
            setStatus("计算完成");
        });
    }

    @Override
    public void showPagedTable(long totalCount, SubnetTablePanel.PagedDataProvider provider) {
        SwingUtilities.invokeLater(() -> {
            if (isClosing || !isDisplayable()) return;
            tablePanel.setPagedData(totalCount, provider);
            cardLayout.show(outputPanel, "TABLE");
            setStatus("计算完成");
        });
    }

    private void setStatus(String msg) {
        setStatus(msg, false);
    }

    private void setStatus(String msg, boolean isError) {
        if (isClosing || !isDisplayable()) return;
        statusLabel.setText(" " + msg);
        // 错误色跟随主题（FlatLaf 提供的语义错误色），暗色主题下自动调亮，避免硬编码红色对比度失衡
        Color errorColor = UIManager.getColor("Component.error.focusedBorderColor");
        if (errorColor == null) {
            errorColor = new Color(220, 53, 69);
        }
        statusLabel.setForeground(isError ? errorColor : UIManager.getColor("Label.foreground"));
    }
    
    private void copyResults() {
        Component visibleComponent = null;
        for (Component c : outputPanel.getComponents()) {
            if (c.isVisible()) {
                visibleComponent = c;
                break;
            }
        }
        if (visibleComponent == tablePanel) {
            String tableData = tablePanel.getAllDataAsText();
            if (tableData.isEmpty()) {
                setStatus("表格为空，无内容可复制", true);
                return;
            }
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(tableData), null);
            setStatus("表格数据已复制到剪贴板");
        } else {
            String tableData = resultTablePanel.getAllDataAsText();
            if (tableData.isEmpty()) {
                setStatus("结果为空，无内容可复制", true);
                return;
            }
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(tableData), null);
            setStatus("结果已复制到剪贴板");
        }
    }
    
    private void updateMemoryUsage() {
        // MemoryMXBean 与 Runtime 同为 JVM 内字段读取（无系统调用），属标准监控 API
        java.lang.management.MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        long maxMemory = heap.getMax();
        if (maxMemory < 0) {
            maxMemory = Runtime.getRuntime().maxMemory(); // max 未定义（-1）时回退
        }

        String usedStr = formatMemory(heap.getUsed());
        String maxStr = formatMemory(maxMemory);

        memoryLabel.setText(" 内存: " + usedStr + " / " + maxStr);
    }
    
    private String formatMemory(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    // ========== Main ==========

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 按"跟随系统/亮色/暗色"模式应用主题（system 模式下自动检测系统深浅色）。
                // Swing 规定 LookAndFeel 必须在 EDT 上设置，因此移入 invokeLater 内
                applyThemeForMode(ConfigStore.getThemeMode());
            } catch (Exception ex) {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {}
            }
            SubnetGUI gui = new SubnetGUI();
            gui.setVisible(true);
            // 启动后自动检查更新 (每 24 小时最多一次，仅在发现新版本时弹窗)
            if (UpdateChecker.shouldAutoCheck()) {
                UpdateChecker.checkAsync(gui, false, gui::setStatus);
            }
        });
    }
}
