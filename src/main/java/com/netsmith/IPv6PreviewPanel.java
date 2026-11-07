package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.math.BigInteger;

/**
 * IPv6 实时预览面板，显示地址详情
 */
public class IPv6PreviewPanel extends JPanel {

    private JLabel networkLabel;
    private JLabel prefixLabel;
    private JLabel maskLabel;
    private JLabel firstAddrLabel;
    private JLabel lastAddrLabel;
    private JLabel hostCountLabel;
    private JLabel addrTypeLabel;
    private JLabel anycastTypeLabel;
    private JPanel previewArea;
    private Border normalBorder;
    private Border errorBorder;
    private boolean showingError = false;
    private JTextField boundTextField;
    private KeyAdapter boundKeyListener;
    private FocusAdapter boundFocusListener;
    private Timer debounceTimer;
    private static final int DEBOUNCE_DELAY = 150;
    /** 值列统一使用等宽字体：IPv6 地址分组对齐，避免长短不一造成的参差 */
    private static final Font VALUE_FONT = ThemeManager.monoFont();

    public IPv6PreviewPanel() {
        setLayout(new BorderLayout());

        previewArea = new JPanel(new GridBagLayout());
        previewArea.setOpaque(true);
        buildPreviewBorders();
        previewArea.setBorder(normalBorder);

        networkLabel = createValueLabel();
        prefixLabel = createValueLabel();
        maskLabel = createValueLabel();
        firstAddrLabel = createValueLabel();
        lastAddrLabel = createValueLabel();
        hostCountLabel = createValueLabel();
        addrTypeLabel = createValueLabel();
        anycastTypeLabel = createValueLabel();

        // 2 列 × 4 行成对排布：横向利用空间、纵向高度减半，
        // 避免预览区过高而把下方"输出结果"区在 BorderLayout 中被挤压到只剩一行
        addPreviewPair(0, "网络地址:", networkLabel, "前缀长度:", prefixLabel);
        addPreviewPair(1, "首个可用地址:", firstAddrLabel, "最后可用地址:", lastAddrLabel);
        addPreviewPair(2, "子网掩码:", maskLabel, "可用主机数:", hostCountLabel);
        addPreviewPair(3, "地址类型:", addrTypeLabel, "任播类型:", anycastTypeLabel);

        // 顶部对齐、按内容紧凑排布，避免行被垂直拉伸造成大片空白
        add(previewArea, BorderLayout.NORTH);

        debounceTimer = new Timer(DEBOUNCE_DELAY, e -> {
            if (boundTextField != null) {
                updatePreview(boundTextField.getText());
            }
        });
        debounceTimer.setRepeats(false);
    }

    private JLabel createValueLabel() {
        // “地址类型”“任播类型”可能返回中文描述，等宽字体无中文字形，需按内容回退界面字体
        JLabel label = new JLabel("---") {
            @Override
            public void setText(String text) {
                super.setText(text);
                setFont(ThemeManager.monoOrUiFont(text));
            }
        };
        label.setFont(VALUE_FONT);
        return label;
    }
    
    /** 重建边框：TitledBorder 的字体与错误色依赖当前主题，切换主题后需要重新生成 */
    private void buildPreviewBorders() {
        normalBorder = BorderFactory.createTitledBorder("实时预览");
        errorBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.error(), 1),
            "实时预览",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            ThemeManager.boldFont(),
            ThemeManager.error()
        );
    }
    
    @Override
    public void updateUI() {
        super.updateUI();
        if (previewArea != null) {
            buildPreviewBorders();
            previewArea.setBorder(showingError ? errorBorder : normalBorder);
            previewArea.setBackground(showingError
                    ? ThemeManager.errorTint()
                    : UIManager.getColor("Panel.background"));
        }
    }

    /** 添加一行两对"标签: 值"：标签列右对齐、值列左对齐并占满各自列宽 */
    private void addPreviewPair(int row, String label1, JLabel value1, String label2, JLabel value2) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(2, 8, 2, 4);

        c.gridx = 0;
        c.weightx = 0;
        previewArea.add(new JLabel(label1), c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 16);
        previewArea.add(value1, c);

        c.gridx = 2;
        c.weightx = 0;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(2, 8, 2, 4);
        previewArea.add(new JLabel(label2), c);

        c.gridx = 3;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 8);
        previewArea.add(value2, c);
    }

    /** 绑定到输入框：仅在此处注册监听器一次，避免重复绑定造成监听器累积 */
    public void bindToTextField(JTextField textField) {
        if (boundTextField != null && boundKeyListener != null) {
            boundTextField.removeKeyListener(boundKeyListener);
            boundTextField.removeFocusListener(boundFocusListener);
        }

        this.boundTextField = textField;

        boundKeyListener = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                debounceTimer.restart();
            }
        };
        boundFocusListener = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                debounceTimer.stop();
                updatePreview(textField.getText());
            }
        };

        textField.addKeyListener(boundKeyListener);
        textField.addFocusListener(boundFocusListener);
    }

    private void updatePreview(String cidr) {
        if (cidr == null || cidr.trim().isEmpty()) {
            clearPreview();
            return;
        }

        try {
            String[] parts = cidr.trim().split("/");
            if (parts.length != 2) {
                showError("格式错误，应为 x:x:x:x:x:x:x:x/yy");
                return;
            }

            String ip = parts[0];
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                showError("前缀必须是数字");
                return;
            }

            if (prefix < 0 || prefix > 128) {
                showError("前缀必须在 0-128 之间");
                return;
            }

            BigInteger addr = IPv6Calculator.parseIPv6(ip);
            BigInteger network = IPv6Calculator.getNetworkAddress(addr, prefix);
            BigInteger mask = IPv6Calculator.getSubnetMask(prefix);
            BigInteger last = IPv6Calculator.getLastAddress(addr, prefix);
            BigInteger hosts = IPv6Calculator.getUsableHostCount(prefix);
            String addrType = IPv6Calculator.getAddressType(addr);
            String anycastType = IPv6Calculator.getAnycastType(addr, prefix);

            networkLabel.setText(IPv6Calculator.formatCompressed(network) + "/" + prefix);
            prefixLabel.setText("/" + prefix);
            maskLabel.setText(IPv6Calculator.formatFull(mask));
            firstAddrLabel.setText(prefix < 128 ? IPv6Calculator.formatCompressed(network.add(BigInteger.ONE)) : IPv6Calculator.formatCompressed(network));
            lastAddrLabel.setText(prefix < 128 ? IPv6Calculator.formatCompressed(last) : "N/A");
            hostCountLabel.setText(truncateNumber(hosts));
            addrTypeLabel.setText(addrType);
            anycastTypeLabel.setText(anycastType != null ? anycastType : "---");

            showingError = false;
            previewArea.setBorder(normalBorder);
            previewArea.setBackground(UIManager.getColor("Panel.background"));
            networkLabel.setForeground(UIManager.getColor("Label.foreground"));
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private String truncateNumber(BigInteger num) {
        String s = num.toString();
        if (s.length() <= 12) return s;
        // 对于大数字，使用科学计数法（保留6位有效数字），避免 doubleValue() 丢失精度
        String mantissa = s.substring(0, 6);
        String formatted = mantissa.charAt(0) + "." + mantissa.substring(1);
        return formatted + "×10^" + (s.length() - 1);
    }

    private void showError(String message) {
        showingError = true;
        Color error = ThemeManager.error();
        networkLabel.setForeground(error);
        networkLabel.setText("<html><span style='color:#"
                + String.format("%06x", error.getRGB() & 0xFFFFFF) + ";'>"
                + message + "</span></html>");
        prefixLabel.setText("---");
        maskLabel.setText("---");
        firstAddrLabel.setText("---");
        lastAddrLabel.setText("---");
        hostCountLabel.setText("---");
        addrTypeLabel.setText("---");
        anycastTypeLabel.setText("---");
        previewArea.setBorder(errorBorder);
        previewArea.setBackground(ThemeManager.errorTint());
    }

    public void refresh() {
        if (boundTextField != null) {
            debounceTimer.stop();
            updatePreview(boundTextField.getText());
        }
    }

    public void clearPreview() {
        showingError = false;
        networkLabel.setForeground(UIManager.getColor("Label.foreground"));
        networkLabel.setText("---");
        prefixLabel.setText("---");
        maskLabel.setText("---");
        firstAddrLabel.setText("---");
        lastAddrLabel.setText("---");
        hostCountLabel.setText("---");
        addrTypeLabel.setText("---");
        anycastTypeLabel.setText("---");
        previewArea.setBorder(normalBorder);
        previewArea.setBackground(UIManager.getColor("Panel.background"));
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        // 面板离开显示层级时停止防抖定时器；
        // 保留定时器与监听器实例，避免下次显示时重复注册监听器或出现定时器为 null 的空指针
        if (debounceTimer != null) {
            debounceTimer.stop();
        }
    }
}
