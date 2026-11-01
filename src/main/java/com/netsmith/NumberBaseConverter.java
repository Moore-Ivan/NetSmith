package com.netsmith;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigInteger;

public class NumberBaseConverter extends JDialog {

    private JTextField inputField;
    private JComboBox<String> fromBaseCombo;
    private JComboBox<String> toBaseCombo;
    private JTextField resultField;
    private JLabel errorLabel;

    private static final String[] BASES = {"二进制 (2)", "八进制 (8)", "十进制 (10)", "十六进制 (16)"};
    private static final int[] BASE_VALUES = {2, 8, 10, 16};

    public NumberBaseConverter(Frame parent) {
        super(parent, "进制转换", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(420, 280);
        setMinimumSize(new Dimension(420, 280));
        setLocationRelativeTo(parent);

        initComponents();
        loadSavedValues();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // 标题
        JLabel titleLabel = new JLabel("进制转换器");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // 输入区域
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("输入值:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        inputField = new JTextField(20);
        inputField.setToolTipText("输入要转换的数值");
        inputPanel.add(inputField, gbc);

        // 进制选择区域
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("源进制:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.5;
        fromBaseCombo = new JComboBox<>(BASES);
        inputPanel.add(fromBaseCombo, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("目标进制:"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        toBaseCombo = new JComboBox<>(BASES);
        inputPanel.add(toBaseCombo, gbc);

        mainPanel.add(inputPanel, BorderLayout.CENTER);

        // 结果区域
        JPanel resultPanel = new JPanel(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        resultPanel.add(new JLabel("转换结果:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        resultField = new JTextField(20);
        resultField.setEditable(false);
        resultPanel.add(resultField, gbc);

        // 错误提示
        errorLabel = new JLabel("");
        errorLabel.setForeground(Color.RED);
        errorLabel.setFont(errorLabel.getFont().deriveFont(12f));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        resultPanel.add(errorLabel, gbc);

        mainPanel.add(resultPanel, BorderLayout.SOUTH);

        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton convertBtn = new JButton("转换");
        convertBtn.setToolTipText("执行进制转换");
        convertBtn.addActionListener(e -> convert());

        JButton swapBtn = new JButton("交换");
        swapBtn.setToolTipText("交换源进制和目标进制");
        swapBtn.addActionListener(e -> swapBases());

        JButton copyBtn = new JButton("复制结果");
        copyBtn.setToolTipText("复制转换结果到剪贴板");
        copyBtn.addActionListener(e -> copyResult());

        JButton clearBtn = new JButton("清空");
        clearBtn.setToolTipText("清空输入和结果");
        clearBtn.addActionListener(e -> clearAll());

        buttonPanel.add(convertBtn);
        buttonPanel.add(swapBtn);
        buttonPanel.add(copyBtn);
        buttonPanel.add(clearBtn);

        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // 回车键触发转换
        inputField.addActionListener(e -> convert());

        // 窗口关闭时保存输入值
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                saveValues();
            }
        });
    }

    /** 默认下拉索引：十进制（BASES[2]）转二进制（BASES[0]） */
    private static final int DEFAULT_FROM_INDEX = 2;
    private static final int DEFAULT_TO_INDEX = 0;

    private void loadSavedValues() {
        String savedInput = ConfigStore.getSetting("base_converter.input", "");
        // 使用带默认值/边界检查的读取，防止配置损坏导致窗口无法打开
        int savedFromBase = ConfigStore.getSetting("base_converter.from_base", DEFAULT_FROM_INDEX);
        int savedToBase = ConfigStore.getSetting("base_converter.to_base", DEFAULT_TO_INDEX);
        if (savedFromBase < 0 || savedFromBase >= BASES.length) savedFromBase = DEFAULT_FROM_INDEX;
        if (savedToBase < 0 || savedToBase >= BASES.length) savedToBase = DEFAULT_TO_INDEX;

        inputField.setText(savedInput);
        fromBaseCombo.setSelectedIndex(savedFromBase);
        toBaseCombo.setSelectedIndex(savedToBase);

        // 如果有保存的输入，自动转换
        if (!savedInput.isEmpty()) {
            convert();
        }
    }

    private void saveValues() {
        ConfigStore.setSetting("base_converter.input", inputField.getText().trim());
        ConfigStore.setSetting("base_converter.from_base", String.valueOf(fromBaseCombo.getSelectedIndex()));
        ConfigStore.setSetting("base_converter.to_base", String.valueOf(toBaseCombo.getSelectedIndex()));
    }

    private void convert() {
        String input = inputField.getText().trim();
        if (input.isEmpty()) {
            errorLabel.setText("请输入要转换的数值");
            resultField.setText("");
            return;
        }

        int fromBase = BASE_VALUES[fromBaseCombo.getSelectedIndex()];
        int toBase = BASE_VALUES[toBaseCombo.getSelectedIndex()];

        // 拒绝带符号的输入：BigInteger 会接受 "+"/"-" 前缀，
        // 负数经 formatBinary/前缀拼接后会产生错乱结果（如 000-1010）
        if (input.startsWith("+") || input.startsWith("-")) {
            errorLabel.setText("不支持正负号，请输入非负数值");
            resultField.setText("");
            return;
        }

        try {
            BigInteger value = new BigInteger(input, fromBase);
            String result = value.toString(toBase).toUpperCase();

            if (toBase == 2) {
                result = formatBinary(result);
            } else if (toBase == 16) {
                result = "0x" + result;
            } else if (toBase == 8 && !result.equals("0")) {
                // 值为 0 时直接输出 "0"，避免加前缀后输出费解的 "00"
                result = "0" + result;
            }

            resultField.setText(result);
            errorLabel.setText("");

        } catch (NumberFormatException e) {
            errorLabel.setText("无效的" + getBaseName(fromBase) + "格式");
            resultField.setText("");
        }
    }

    private String formatBinary(String binary) {
        int padding = (4 - binary.length() % 4) % 4;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < padding; i++) {
            sb.append("0");
        }
        sb.append(binary);

        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < sb.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                formatted.append(" ");
            }
            formatted.append(sb.charAt(i));
        }
        return formatted.toString();
    }

    private String getBaseName(int base) {
        switch (base) {
            case 2: return "二进制";
            case 8: return "八进制";
            case 10: return "十进制";
            case 16: return "十六进制";
            default: return "未知进制";
        }
    }

    private void swapBases() {
        int fromIndex = fromBaseCombo.getSelectedIndex();
        int toIndex = toBaseCombo.getSelectedIndex();
        fromBaseCombo.setSelectedIndex(toIndex);
        toBaseCombo.setSelectedIndex(fromIndex);

        if (!resultField.getText().isEmpty()) {
            convert();
        }
    }

    private void copyResult() {
        String result = resultField.getText().trim();
        if (result.isEmpty()) {
            errorLabel.setText("没有可复制的结果");
            return;
        }
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new java.awt.datatransfer.StringSelection(result), null);
        errorLabel.setText("结果已复制到剪贴板");
    }

    private void clearAll() {
        inputField.setText("");
        resultField.setText("");
        errorLabel.setText("");
        inputField.requestFocus();
    }

    public static void showConverter(Frame parent) {
        NumberBaseConverter converter = new NumberBaseConverter(parent);
        converter.setVisible(true);
    }
}