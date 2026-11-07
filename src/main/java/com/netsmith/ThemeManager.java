package com.netsmith;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Map;

/**
 * 全局外观管理器：集中安装 FlatLaf、字体与通用样式。
 * <p>所有面板在创建前都会经过这里，因此只需在此维护一套圆角、间距与语义色，
 * 即可让 19+ 业务面板保持一致，而不需要逐个改布局。</p>
 */
public final class ThemeManager {

    /** 强调色：亮暗主题共用一套主色，FlatLaf 会自动派生悬停/按下状态 */
    private static final String ACCENT_HEX = "#3B82F6";
    private static final Color ACCENT_FALLBACK = new Color(0x3B82F6);
    private static final Color ERROR_FALLBACK = new Color(0xDC3545);

    private static final int UI_FONT_SIZE = 13;
    private static final int MONO_FONT_SIZE = 13;

    private static final String[] UI_FONT_CANDIDATES = {
            "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
            "Noto Sans CJK SC", "Source Han Sans SC", "WenQuanYi Micro Hei", "Segoe UI"
    };
    private static final String[] MONO_FONT_CANDIDATES = {
            "Cascadia Mono", "Consolas", "JetBrains Mono", "SF Mono", "DejaVu Sans Mono", "Menlo"
    };

    private static final Font UI_FONT;
    private static final Font UI_FONT_SMALL;
    private static final Font UI_FONT_BOLD;
    private static final Font MONO_FONT;
    private static final Font MONO_FONT_BOLD;

    static {
        String uiFamily = pickFamily(UI_FONT_CANDIDATES, Font.SANS_SERIF);
        String monoFamily = pickFamily(MONO_FONT_CANDIDATES, Font.MONOSPACED);

        UI_FONT = new Font(uiFamily, Font.PLAIN, UI_FONT_SIZE);
        UI_FONT_SMALL = new Font(uiFamily, Font.PLAIN, UI_FONT_SIZE - 2);
        UI_FONT_BOLD = new Font(uiFamily, Font.BOLD, UI_FONT_SIZE);
        MONO_FONT = new Font(monoFamily, Font.PLAIN, MONO_FONT_SIZE);
        MONO_FONT_BOLD = new Font(monoFamily, Font.BOLD, MONO_FONT_SIZE);
    }

    private ThemeManager() {
    }

    /**
     * 安装指定深浅色主题，并重新应用全局默认值。
     * <p>字体必须在 setLookAndFeel 之前写入，FlatLaf 会在初始化时据此派生全部字体。</p>
     */
    public static void install(boolean dark) {
        String uiFamily = UI_FONT.getFamily();
        FlatLaf.setPreferredFontFamily(uiFamily);
        FlatLaf.setPreferredLightFontFamily(uiFamily);
        FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", ACCENT_HEX));

        UIManager.put("defaultFont", new FontUIResource(UI_FONT));
        try {
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
        } catch (Exception e) {
            System.err.println("加载主题失败: " + e.getMessage());
        }
        applyDefaults();
    }

    /** 应用全局通用样式：圆角、焦点环、滚动条、表格与分割线等 */
    public static void applyDefaults() {
        // 圆角与焦点
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("CheckBox.arc", 8);
        UIManager.put("ProgressBar.arc", 10);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.outlineWidth", 1);

        // 标签页：更矮的留白 + 无分隔线，弱化传统"文件夹"观感
        UIManager.put("TabbedPane.tabHeight", 36);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("TabbedPane.tabSelectionArc", 10);
        UIManager.put("TabbedPane.hasFullBorder", false);
        UIManager.put("TabbedPane.showContentSeparator", false);

        // 滚动条：细窄圆角，去掉两端箭头
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ScrollPane.smoothScrolling", true);

        // 表格：行高与斑马纹提升可读性
        UIManager.put("Table.rowHeight", 26);
        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("TableHeader.height", 34);
        UIManager.put("TableHeader.font", new FontUIResource(UI_FONT_BOLD));
        UIManager.put("Table.alternateRowColor", alternateRowColor());

        UIManager.put("SplitPane.dividerSize", 8);
        UIManager.put("ToolTip.font", new FontUIResource(UI_FONT_SMALL));
        UIManager.put("OptionPane.messageFont", new FontUIResource(UI_FONT));
        UIManager.put("OptionPane.buttonFont", new FontUIResource(UI_FONT));
        // 统一各业务面板的 TitledBorder 标题：加粗小字号，并确保暗色主题下对比度足够
        Color titledColor = UIManager.getColor("Label.foreground");
        if (titledColor != null) {
            UIManager.put("TitledBorder.titleColor", titledColor);
        }
        UIManager.put("TitledBorder.font", new FontUIResource(UI_FONT_BOLD.deriveFont(Font.BOLD, 12f)));
    }

    // ========== 字体 ==========

    public static Font uiFont() {
        return UI_FONT;
    }

    public static Font smallFont() {
        return UI_FONT_SMALL;
    }

    public static Font boldFont() {
        return UI_FONT_BOLD;
    }

    public static Font monoFont() {
        return MONO_FONT;
    }

    public static Font monoBoldFont() {
        return MONO_FONT_BOLD;
    }

    /**
     * 等宽字体只覆盖拉丁字符，中文等非 ASCII 内容会渲染成方块。
     * 按内容选择字体：纯 ASCII 用等宽以保证 IP/掩码对齐，其余回退界面字体。
     */
    public static Font monoOrUiFont(String text) {
        return hasNonAscii(text) ? UI_FONT : MONO_FONT;
    }

    /** 加粗等宽字体版本，规则同 {@link #monoOrUiFont} */
    public static Font monoBoldOrUiFont(String text) {
        return hasNonAscii(text) ? UI_FONT_BOLD : MONO_FONT_BOLD;
    }

    private static boolean hasNonAscii(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return true;
            }
        }
        return false;
    }

    // ========== 语义色 ==========

    /** 主题强调色（FlatLaf 由 @accentColor 派生） */
    public static Color accent() {
        Color color = UIManager.getColor("Component.accentColor");
        return color != null ? color : ACCENT_FALLBACK;
    }

    /** 次要文字色（说明文案、页脚、提示） */
    public static Color muted() {
        Color color = UIManager.getColor("Label.disabledForeground");
        return color != null ? color : blend(panelBackground(), Color.GRAY, 0.55f);
    }

    /** 错误色，随主题自动调整亮度 */
    public static Color error() {
        Color color = UIManager.getColor("Component.error.focusedBorderColor");
        return color != null ? color : ERROR_FALLBACK;
    }

    /** 分隔线颜色 */
    public static Color divider() {
        Color color = UIManager.getColor("Component.borderColor");
        if (color == null) {
            color = UIManager.getColor("Separator.foreground");
        }
        return color != null ? color : new Color(0xD3D8E0);
    }

    /** 面板背景色 */
    public static Color panelBackground() {
        Color color = UIManager.getColor("Panel.background");
        return color != null ? color : new Color(0xF5F7FA);
    }

    /** 错误提示用的低饱和底色 */
    public static Color errorTint() {
        return blend(panelBackground(), error(), 0.10f);
    }

    /** 斑马纹行背景：与面板底色保持极低对比，暗色主题下略强 */
    private static Color alternateRowColor() {
        float ratio = FlatLaf.isLafDark() ? 0.06f : 0.035f;
        return blend(panelBackground(), UIManager.getColor("Label.foreground"), ratio);
    }

    // ========== 通用组件工厂 ==========

    /** 工具栏风格按钮：无边框、悬停高亮，适合头部与结果栏 */
    public static JButton toolButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        return button;
    }

    /** 竖线分隔符，用于头部功能分组 */
    public static JComponent verticalSeparator() {
        return new JComponent() {
            {
                setPreferredSize(new Dimension(9, 22));
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(divider());
                int x = getWidth() / 2;
                g2.drawLine(x, 3, x, getHeight() - 4);
                g2.dispose();
            }
        };
    }

    /**
     * 次要文字标签：颜色在每次 updateUI 时重新从主题读取，
     * 这样切换深浅色后无需手工遍历组件也能保持正确对比度。
     */
    public static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text) {
            @Override
            public void updateUI() {
                super.updateUI();
                setForeground(muted());
            }
        };
        label.setForeground(muted());
        return label;
    }

    // ========== 颜色工具 ==========

    public static Color blend(Color base, Color overlay, float ratio) {
        if (base == null) base = panelBackground();
        if (overlay == null) overlay = Color.GRAY;
        float r = Math.max(0f, Math.min(1f, ratio));
        return new Color(
                Math.round(base.getRed() * (1 - r) + overlay.getRed() * r),
                Math.round(base.getGreen() * (1 - r) + overlay.getGreen() * r),
                Math.round(base.getBlue() * (1 - r) + overlay.getBlue() * r));
    }

    private static String pickFamily(String[] candidates, String fallback) {
        java.util.Set<String> available = new java.util.HashSet<>(
                java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames()));
        for (String candidate : candidates) {
            if (available.contains(candidate)) {
                return candidate;
            }
        }
        return fallback;
    }
}
