package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.function.Consumer;

public class IPParsePanel extends BaseOperationPanel {
    private final ResultView resultView;

    public IPParsePanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("输入单个 IP 地址 (不带掩码)");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        HistoryComboBox txtIP = new HistoryComboBox("ip_parse");
        txtIP.setToolTipText("例如: 10.0.0.1  或  192.168.1.100");
        JButton btnParse = new JButton("解析");

        ErrorIndicator errorIndicator = new ErrorIndicator();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(new JLabel("IP 地址:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        add(txtIP, gbc);
        gbc.gridx = 5;
        gbc.weightx = 0;
        add(errorIndicator, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnParse);
        JButton example = new JButton("示例");
        example.addActionListener(e -> {
            txtIP.setText("192.168.1.100");
            txtIP.save();
        });
        buttonPanel.add(example);
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> {
            txtIP.setText("");
            errorIndicator.clearError();
        });
        buttonPanel.add(clearBtn);
        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 6;
        add(buttonPanel, gbc);

        btnParse.addActionListener(e -> {
            txtIP.save();
            String ip = txtIP.getText().trim();
            CalculationWorker.executeWithProgress(this, "正在解析 IP 地址...", btnParse,
                new CalculationWorker.CallableWithProgress<String>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public String call() throws Exception {
                        CidrValidator.validateIp(ip);
                        return SubnetCalculator.parseIPDetails(ip);
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
                        handleError(ex, errorIndicator, txtIP);
                    }
                }
            );
        });

        bindEnterKey((JTextField) txtIP.getEditor().getEditorComponent(), btnParse);
    }
}