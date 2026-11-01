package com.netsmith;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MaskConverter extends JDialog {

    private JTextField decimalMaskField;
    private JTextField binaryMaskField;
    private JTextField prefixField;
    private JLabel errorLabel;
    private boolean isUpdating = false;
    private javax.swing.Timer debounceTimer;
    private int pendingSource = -1;

    private static final int SOURCE_DECIMAL = 0;
    private static final int SOURCE_BINARY = 1;
    private static final int SOURCE_PREFIX = 2;

    public MaskConverter(Frame parent) {
        super(parent, "掩码转换", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(450, 280);
        setMinimumSize(new Dimension(450, 280));
        setLocationRelativeTo(parent);

        initComponents();
        loadSavedValue();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // 标题
        JLabel titleLabel = new JLabel("IPv4 掩码转换器");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // 输入区域
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 点分十进制掩码
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("点分十进制:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        decimalMaskField = new JTextField(15);
        decimalMaskField.setToolTipText("例如: 255.255.255.0");
        decimalMaskField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { scheduleUpdate(SOURCE_DECIMAL); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { scheduleUpdate(SOURCE_DECIMAL); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { scheduleUpdate(SOURCE_DECIMAL); }
        });
        inputPanel.add(decimalMaskField, gbc);

        // 二进制掩码
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("二进制掩码:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        binaryMaskField = new JTextField(15);
        binaryMaskField.setToolTipText("例如: 11111111.11111111.11111111.00000000");
        binaryMaskField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { scheduleUpdate(SOURCE_BINARY); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { scheduleUpdate(SOURCE_BINARY); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { scheduleUpdate(SOURCE_BINARY); }
        });
        inputPanel.add(binaryMaskField, gbc);

        // 前缀长度
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("前缀长度 (/):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        prefixField = new JTextField(5);
        prefixField.setToolTipText("例如: 24");
        prefixField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { scheduleUpdate(SOURCE_PREFIX); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { scheduleUpdate(SOURCE_PREFIX); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { scheduleUpdate(SOURCE_PREFIX); }
        });
        inputPanel.add(prefixField, gbc);

        mainPanel.add(inputPanel, BorderLayout.CENTER);

        // 错误提示（错误红字使用 FlatLaf 语义错误色，暗色主题下自动调亮）
        errorLabel = new JLabel("");
        errorLabel.setForeground(getThemeErrorColor());
        errorLabel.setFont(errorLabel.getFont().deriveFont(12f));
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(errorLabel, BorderLayout.SOUTH);

        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton copyDecimalBtn = new JButton("复制十进制");
        copyDecimalBtn.setToolTipText("复制点分十进制掩码");
        copyDecimalBtn.addActionListener(e -> copyToClipboard(decimalMaskField.getText()));

        JButton copyBinaryBtn = new JButton("复制二进制");
        copyBinaryBtn.setToolTipText("复制二进制掩码");
        copyBinaryBtn.addActionListener(e -> copyToClipboard(binaryMaskField.getText()));

        JButton clearBtn = new JButton("清空");
        clearBtn.setToolTipText("清空所有输入");
        clearBtn.addActionListener(e -> clearAll());

        buttonPanel.add(copyDecimalBtn);
        buttonPanel.add(copyBinaryBtn);
        buttonPanel.add(clearBtn);

        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // 窗口关闭时先刷新未处理的防抖更新，再保存输入值
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                flushPendingUpdate();
                stopDebounce();
                saveValue();
            }
        });

        // 防抖定时器：用户停止输入 300ms 后执行转换
        debounceTimer = new javax.swing.Timer(300, e -> flushPendingUpdate());
        debounceTimer.setRepeats(false);
    }

    /**
     * 防抖调度：每次输入变化时调用，空输入即时处理，否则等待 300ms
     */
    private void scheduleUpdate(int source) {
        if (isUpdating) return;

        String text = getSourceText(source);
        if (text.isEmpty()) {
            // 输入被清空 → 立即处理，清除关联字段
            pendingSource = -1;
            debounceTimer.stop();
            doUpdate(source);
            return;
        }

        pendingSource = source;
        debounceTimer.restart();
    }

    private String getSourceText(int source) {
        switch (source) {
            case SOURCE_DECIMAL: return decimalMaskField.getText().trim();
            case SOURCE_BINARY:  return binaryMaskField.getText().trim();
            case SOURCE_PREFIX:  return prefixField.getText().trim();
        }
        return "";
    }

    private void flushPendingUpdate() {
        if (pendingSource < 0) return;
        int source = pendingSource;
        pendingSource = -1;
        doUpdate(source);
    }

    private void doUpdate(int source) {
        switch (source) {
            case SOURCE_DECIMAL: updateFromDecimal(); break;
            case SOURCE_BINARY:  updateFromBinary();  break;
            case SOURCE_PREFIX:  updateFromPrefix();  break;
        }
    }

    private void stopDebounce() {
        if (debounceTimer != null) {
            debounceTimer.stop();
        }
        pendingSource = -1;
    }

    private void loadSavedValue() {
        // 从配置恢复上次使用的所有字段值
        String savedDecimal = ConfigStore.getSetting("mask_converter.decimal", "");
        String savedBinary = ConfigStore.getSetting("mask_converter.binary", "");
        String savedPrefix = ConfigStore.getSetting("mask_converter.prefix", "");

        if (savedDecimal.isEmpty() && savedBinary.isEmpty() && savedPrefix.isEmpty()) {
            return; // 首次使用，无历史记录
        }

        isUpdating = true;
        try {
            decimalMaskField.setText(savedDecimal);
            binaryMaskField.setText(savedBinary);
            prefixField.setText(savedPrefix);
        } finally {
            isUpdating = false;
        }
    }

    private void saveValue() {
        String decimal = decimalMaskField.getText().trim();
        String binary = binaryMaskField.getText().trim();
        String prefix = prefixField.getText().trim();

        ConfigStore.setSetting("mask_converter.decimal", decimal);
        ConfigStore.setSetting("mask_converter.binary", binary);
        ConfigStore.setSetting("mask_converter.prefix", prefix);
    }

    private void updateFromDecimal() {
        if (isUpdating) return;
        String decimal = decimalMaskField.getText().trim();
        if (decimal.isEmpty()) {
            setFields(SOURCE_DECIMAL, null, null, "");
            return;
        }

        try {
            int prefix = decimalToPrefix(decimal);
            String binary = decimalToBinary(decimal);
            setFields(SOURCE_DECIMAL, binary, String.valueOf(prefix), null);
        } catch (IllegalArgumentException e) {
            setFields(SOURCE_DECIMAL, null, null, e.getMessage());
        }
    }

    private void updateFromBinary() {
        if (isUpdating) return;
        String binary = binaryMaskField.getText().trim();
        if (binary.isEmpty()) {
            setFields(SOURCE_BINARY, null, null, "");
            return;
        }

        try {
            int prefix = binaryToPrefix(binary);
            String decimal = binaryToDecimal(binary);
            setFields(SOURCE_BINARY, decimal, String.valueOf(prefix), null);
        } catch (IllegalArgumentException e) {
            setFields(SOURCE_BINARY, null, null, e.getMessage());
        }
    }

    private void updateFromPrefix() {
        if (isUpdating) return;
        String prefixStr = prefixField.getText().trim();
        if (prefixStr.isEmpty()) {
            setFields(SOURCE_PREFIX, null, null, "");
            return;
        }

        try {
            int prefix = Integer.parseInt(prefixStr);
            if (prefix < 0 || prefix > 32) {
                throw new IllegalArgumentException("前缀长度必须在 0-32 之间");
            }
            String decimal = prefixToDecimal(prefix);
            String binary = prefixToBinary(prefix);
            setFields(SOURCE_PREFIX, decimal, binary, null);
        } catch (NumberFormatException e) {
            setFields(SOURCE_PREFIX, null, null, "请输入有效的数字");
        } catch (IllegalArgumentException e) {
            setFields(SOURCE_PREFIX, null, null, e.getMessage());
        }
    }

    /** 主题感知的错误色：优先 FlatLaf 语义错误色，回退红色 */
    private static Color getThemeErrorColor() {
        Color c = UIManager.getColor("Component.error.focusedBorderColor");
        return c != null ? c : Color.RED;
    }

    /** 统一设置提示消息：错误用错误色，成功/清空用正常前景色（修复成功消息显示为红色的问题） */
    private void setMessage(String msg, boolean isError) {
        errorLabel.setText(msg);
        if (isError) {
            errorLabel.setForeground(getThemeErrorColor());
        } else {
            Color normal = UIManager.getColor("Label.foreground");
            errorLabel.setForeground(normal != null ? normal : Color.BLACK);
        }
    }

    /**
     * 统一设置字段值
     * @param source 触发来源 (SOURCE_DECIMAL / SOURCE_BINARY / SOURCE_PREFIX)
     * @param val1 写入第二个字段的值（来源decimal→binary,来源binary→decimal,来源prefix→decimal）
     * @param val2 写入第三个字段的值（来源decimal→prefix,来源binary→prefix,来源prefix→binary）
     * @param error 错误信息；null表示成功，非空时显示错误
     */
    private void setFields(int source, String val1, String val2, String error) {
        isUpdating = true;
        try {
            if (error != null && !error.isEmpty()) {
                // 错误：清空关联字段，保留触发来源的输入
                switch (source) {
                    case SOURCE_DECIMAL:
                        binaryMaskField.setText("");
                        prefixField.setText("");
                        break;
                    case SOURCE_BINARY:
                        decimalMaskField.setText("");
                        prefixField.setText("");
                        break;
                    case SOURCE_PREFIX:
                        decimalMaskField.setText("");
                        binaryMaskField.setText("");
                        break;
                }
                setMessage(error, true);
                return;
            }

            // 成功或清空
            if (val1 == null && val2 == null && error != null && error.isEmpty()) {
                // 输入被清空 → 所有字段清空
                decimalMaskField.setText("");
                binaryMaskField.setText("");
                prefixField.setText("");
                setMessage("", false);
                return;
            }

            switch (source) {
                case SOURCE_DECIMAL:
                    binaryMaskField.setText(val1);
                    prefixField.setText(val2);
                    break;
                case SOURCE_BINARY:
                    decimalMaskField.setText(val1);
                    prefixField.setText(val2);
                    break;
                case SOURCE_PREFIX:
                    decimalMaskField.setText(val1);
                    binaryMaskField.setText(val2);
                    break;
            }
            setMessage("", false);
        } finally {
            isUpdating = false;
        }
    }

    private int decimalToPrefix(String decimal) {
        String[] parts = decimal.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("无效的点分十进制格式");
        }

        long mask = 0;
        for (String part : parts) {
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("无效的掩码数值: \"" + part + "\"");
            }
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("每个部分必须在 0-255 之间");
            }
            mask = (mask << 8) | octet;
        }
        mask &= 0xFFFFFFFFL;

        int prefix = Long.bitCount(mask);
        // 校验掩码连续性：数值必须等于该前缀对应的标准掩码。
        // 仅逐段校验 octet 会漏过 255.0.255.0、0.255.0.0 等非连续掩码
        long canonical = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        if (mask != canonical) {
            throw new IllegalArgumentException("不是有效的子网掩码");
        }
        return prefix;
    }

    private String decimalToBinary(String decimal) {
        String[] parts = decimal.split("\\.");
        StringBuilder binary = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(parts[i]);
            binary.append(String.format("%8s", Integer.toBinaryString(octet)).replace(' ', '0'));
            if (i < 3) {
                binary.append(".");
            }
        }
        return binary.toString();
    }

    private int binaryToPrefix(String binary) {
        String cleanBinary = binary.replace(".", "");
        if (cleanBinary.length() != 32) {
            throw new IllegalArgumentException("二进制掩码必须为 32 位");
        }
        if (!cleanBinary.matches("[01]+")) {
            throw new IllegalArgumentException("二进制掩码只能包含 0 和 1");
        }
        if (!isValidBinaryMask(cleanBinary)) {
            throw new IllegalArgumentException("不是有效的子网掩码");
        }
        return (int) cleanBinary.chars().filter(c -> c == '1').count();
    }

    private String binaryToDecimal(String binary) {
        String cleanBinary = binary.replace(".", "");
        StringBuilder decimal = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int start = i * 8;
            int end = start + 8;
            String octetStr = cleanBinary.substring(start, end);
            int octet = Integer.parseInt(octetStr, 2);
            decimal.append(octet);
            if (i < 3) {
                decimal.append(".");
            }
        }
        return decimal.toString();
    }

    private String prefixToDecimal(int prefix) {
        StringBuilder decimal = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int bitsInOctet = Math.min(8, prefix - i * 8);
            int octet = bitsInOctet > 0 ? (0xFF << (8 - bitsInOctet)) & 0xFF : 0;
            decimal.append(octet);
            if (i < 3) {
                decimal.append(".");
            }
        }
        return decimal.toString();
    }

    private String prefixToBinary(int prefix) {
        StringBuilder binary = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            binary.append(i < prefix ? '1' : '0');
            if ((i + 1) % 8 == 0 && i < 31) {
                binary.append(".");
            }
        }
        return binary.toString();
    }

    private boolean isValidBinaryMask(String binary) {
        int firstZero = binary.indexOf('0');
        if (firstZero == -1) {
            return true;
        }
        return binary.substring(firstZero).indexOf('1') == -1;
    }

    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) {
            setMessage("没有可复制的内容", true);
            return;
        }
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new java.awt.datatransfer.StringSelection(text), null);
        setMessage("已复制到剪贴板", false);
    }

    private void clearAll() {
        stopDebounce();
        isUpdating = true;
        try {
            decimalMaskField.setText("");
            binaryMaskField.setText("");
            prefixField.setText("");
            errorLabel.setText("");
            decimalMaskField.requestFocus();
        } finally {
            isUpdating = false;
        }
    }

    public static void showConverter(Frame parent) {
        MaskConverter converter = new MaskConverter(parent);
        converter.setVisible(true);
    }
}