package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.function.Consumer;

public class IPv6ParsePanel extends BaseOperationPanel {
    private final ResultView resultView;

    public IPv6ParsePanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("输入单个 IPv6 地址");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        HistoryComboBox txtAddr = new HistoryComboBox("ipv6_addr_parse", 320);
        txtAddr.setToolTipText("例如: 2001:db8::1  或  fe80::1  或  ::1");
        JButton btnParse = new JButton("解析");

        ErrorIndicator errorIndicator = new ErrorIndicator();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(new JLabel("IPv6 地址:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        add(txtAddr, gbc);
        gbc.gridx = 5;
        gbc.weightx = 0;
        add(errorIndicator, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnParse);
        JButton example = new JButton("示例");
        example.addActionListener(e -> {
            txtAddr.setText("2001:db8::1");
            txtAddr.save();
        });
        buttonPanel.add(example);
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> {
            txtAddr.setText("");
            errorIndicator.clearError();
        });
        buttonPanel.add(clearBtn);
        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 6;
        add(buttonPanel, gbc);

        btnParse.addActionListener(e -> {
            txtAddr.save();
            String addr = txtAddr.getText().trim();
            CalculationWorker.executeWithProgress(this, "正在解析 IPv6 地址...", btnParse,
                new CalculationWorker.CallableWithProgress<String>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public String call() throws Exception {
                        IPv6Validator.validateAddress(addr);
                        return IPv6Calculator.parseIPv6Details(addr);
                    }
                },
                new CalculationWorker.CalculationCallback<String>() {
                    @Override
                    public void onStart() {
                        resultView.showText("");
                    }
                    @Override public void onProgress(int p, String m) {}
                    @Override
                    public void onComplete(String result) {
                        resultView.showText(result);
                        errorIndicator.clearError();
                        statusCallback.accept("完成");
                    }
                    @Override
                    public void onError(Exception ex) {
                        handleError(ex, errorIndicator, txtAddr);
                    }
                }
            );
        });

        bindEnterKey((JTextField) txtAddr.getEditor().getEditorComponent(), btnParse);
    }
}