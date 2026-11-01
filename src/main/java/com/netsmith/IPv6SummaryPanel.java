package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.function.Consumer;

public class IPv6SummaryPanel extends BaseOperationPanel {
    private final ResultView resultView;

    public IPv6SummaryPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("IPv6 路由汇总（超网）");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        HistoryComboBox txtCidrs = new HistoryComboBox("ipv6_cidrs_summary", 420);
        txtCidrs.setToolTipText("例如: 2001:db8:1::/48,2001:db8:2::/48,2001:db8:3::/48");
        JButton btnSummary = new JButton("汇总");

        ErrorIndicator errorIndicator = new ErrorIndicator();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(new JLabel("IPv6 CIDR 列表 (逗号分隔):"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        add(txtCidrs, gbc);
        gbc.gridx = 5;
        gbc.weightx = 0;
        add(errorIndicator, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnSummary);
        JButton example = new JButton("示例");
        example.addActionListener(e -> {
            txtCidrs.setText("2001:db8:1::/48,2001:db8:2::/48,2001:db8:3::/48");
            txtCidrs.save();
        });
        buttonPanel.add(example);
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> {
            txtCidrs.setText("");
            errorIndicator.clearError();
        });
        buttonPanel.add(clearBtn);
        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 6;
        add(buttonPanel, gbc);

        btnSummary.addActionListener(e -> {
            txtCidrs.save();
            String input = txtCidrs.getText().trim();
            CalculationWorker.executeWithProgress(this, "正在汇总 IPv6 路由...", btnSummary,
                new CalculationWorker.CallableWithProgress<String>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public String call() throws Exception {
                        String[] cidrs = IPv6Validator.validateCidrList(input, 1);
                        String summary = IPv6Calculator.summarizeIPv6(cidrs);

                        StringBuilder sb = new StringBuilder();
                        sb.append("═".repeat(55)).append("\n");
                        sb.append("   IPv6 路由汇总结果\n");
                        sb.append("═".repeat(55)).append("\n");
                        sb.append("  输入网段:\n");
                        for (String c : cidrs) {
                            sb.append(String.format("    • %s\n", c));
                        }
                        sb.append("─".repeat(55)).append("\n");
                        sb.append(String.format("  汇总路由: %s\n", summary));
                        sb.append("═".repeat(55)).append("\n");
                        return sb.toString();
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
                        handleError(ex, errorIndicator, txtCidrs);
                    }
                }
            );
        });

        bindEnterKey((JTextField) txtCidrs.getEditor().getEditorComponent(), btnSummary);
    }
}