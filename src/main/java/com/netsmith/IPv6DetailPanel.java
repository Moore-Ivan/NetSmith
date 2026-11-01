package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.function.Consumer;

public class IPv6DetailPanel extends BaseOperationPanel {
    private final ResultView resultView;
    private HistoryComboBox txtCidr;
    private ErrorIndicator errorIndicator;
    private IPv6PreviewPanel previewPanel;

    public IPv6DetailPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        JPanel inputPanel = new JPanel(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("输入 IPv6 CIDR 格式网段");
        inputPanel.setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        txtCidr = new HistoryComboBox("ipv6_cidr_detail", 320);
        txtCidr.setToolTipText("例如: 2001:db8::/32  或  fe80::1/10");
        JButton btnQuery = new JButton("查询详情");

        errorIndicator = new ErrorIndicator();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("CIDR:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        inputPanel.add(txtCidr, gbc);
        gbc.gridx = 5;
        gbc.weightx = 0;
        inputPanel.add(errorIndicator, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnQuery);
        JButton example = new JButton("示例");
        example.addActionListener(e -> {
            txtCidr.setText("2001:db8::/32");
            txtCidr.save();
            SwingUtilities.invokeLater(() -> previewPanel.refresh());
        });
        buttonPanel.add(example);
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> {
            txtCidr.setText("");
            errorIndicator.clearError();
            previewPanel.clearPreview();
        });
        buttonPanel.add(clearBtn);
        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 6;
        inputPanel.add(buttonPanel, gbc);

        add(inputPanel, BorderLayout.NORTH);

        previewPanel = new IPv6PreviewPanel();
        previewPanel.bindToTextField((JTextField) txtCidr.getEditor().getEditorComponent());
        add(previewPanel, BorderLayout.CENTER);

        btnQuery.addActionListener(e -> {
            txtCidr.save();
            String cidr = txtCidr.getText().trim();
            CalculationWorker.executeWithProgress(this, "正在查询 IPv6 网段详情...", btnQuery,
                new CalculationWorker.CallableWithProgress<String>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public String call() throws Exception {
                        IPv6Validator.validateCidr(cidr);
                        return IPv6Calculator.getSubnetDetails(cidr);
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
                        handleError(ex, errorIndicator, txtCidr);
                    }
                }
            );
        });

        bindEnterKey((JTextField) txtCidr.getEditor().getEditorComponent(), btnQuery);
    }
}