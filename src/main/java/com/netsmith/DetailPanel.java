package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.function.Consumer;

public class DetailPanel extends BaseOperationPanel {
    private final ResultView resultView;

    public DetailPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        Border titledBorder = BorderFactory.createTitledBorder("输入 CIDR 格式网段");
        setBorder(titledBorder);

        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        HistoryComboBox txtCidr = new HistoryComboBox("cidr_detail");
        txtCidr.setToolTipText("例如: 192.168.1.0/24  或  10.0.0.1/31");

        JButton btnQuery = new JButton("查询详情");
        btnQuery.setToolTipText("显示该网段的网络地址、掩码、主机范围等 (Enter)");

        ErrorIndicator errorIndicator = new ErrorIndicator();

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

        CidrPreviewPanel previewPanel = new CidrPreviewPanel();

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnQuery);
        JButton exampleBtn = new JButton("示例");
        exampleBtn.addActionListener(e -> {
            txtCidr.setText("192.168.1.0/24");
            txtCidr.save();
            previewPanel.refresh();
        });
        buttonPanel.add(exampleBtn);
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

        JTextField cidrEditor = (JTextField) txtCidr.getEditor().getEditorComponent();
        previewPanel.bindToTextField(cidrEditor);
        txtCidr.addActionListener(evt -> previewPanel.refresh());

        add(inputPanel, BorderLayout.NORTH);
        add(previewPanel, BorderLayout.CENTER);

        btnQuery.addActionListener(e -> {
            txtCidr.save();
            String cidr = txtCidr.getText().trim();
            CalculationWorker.executeWithProgress(this, "正在查询网段详情...", btnQuery,
                new CalculationWorker.CallableWithProgress<String>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public String call() throws Exception {
                        CidrValidator.validateCidr(cidr);
                        return SubnetCalculator.getSubnetDetails(cidr);
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