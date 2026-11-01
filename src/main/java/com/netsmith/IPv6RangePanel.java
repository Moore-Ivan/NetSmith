package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class IPv6RangePanel extends BaseOperationPanel {

    private final ResultView resultView;

    public IPv6RangePanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("IPv6 地址范围 → CIDR 列表");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        HistoryComboBox txtStart = new HistoryComboBox("ipv6_start_range", 200);
        HistoryComboBox txtEnd = new HistoryComboBox("ipv6_end_range", 200);
        JButton btnConvert = new JButton("转换");
        JButton exampleBtn = new JButton("示例");
        JButton clearBtn = new JButton("清空");

        ErrorIndicator startError = new ErrorIndicator();
        ErrorIndicator endError = new ErrorIndicator();

        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("起始地址:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        add(txtStart, gbc);
        gbc.gridx = 3; gbc.weightx = 0; gbc.gridwidth = 1;
        add(startError, gbc);

        gbc.gridy = 1; gbc.gridx = 0;
        add(new JLabel("结束地址:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        add(txtEnd, gbc);
        gbc.gridx = 3; gbc.weightx = 0; gbc.gridwidth = 1;
        add(endError, gbc);

        JLabel info = new JLabel("说明：输入 IPv6 地址范围，自动计算覆盖该范围的最小 CIDR 块");
        info.setForeground(Color.GRAY);
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 4; gbc.weightx = 0;
        add(info, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnConvert);
        buttonPanel.add(exampleBtn);
        buttonPanel.add(clearBtn);
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 4; gbc.weightx = 1.0;
        add(buttonPanel, gbc);

        exampleBtn.addActionListener(e -> {
            txtStart.setText("2001:db8::");
            txtStart.save();
            txtEnd.setText("2001:db8::ffff");
            txtEnd.save();
        });

        clearBtn.addActionListener(e -> {
            txtStart.setText("");
            txtEnd.setText("");
            clearErrors(startError, endError);
        });

        btnConvert.addActionListener(e -> {
            txtStart.save();
            txtEnd.save();
            String startIp = txtStart.getText().trim();
            String endIp = txtEnd.getText().trim();
            CalculationWorker.executeWithProgress(this, "正在转换为 CIDR...", btnConvert,
                new CalculationWorker.CallableWithProgress<List<SubnetTablePanel.SubnetRow>>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public List<SubnetTablePanel.SubnetRow> call() throws Exception {
                        if (handler != null) handler.onProgress(50, "计算中...");
                        return SubnetController.ipv6RangeToCidrSync(startIp, endIp);
                    }
                },
                new CalculationWorker.CalculationCallback<List<SubnetTablePanel.SubnetRow>>() {
                    @Override
                    public void onStart() {
                        resultView.showTable(java.util.Collections.emptyList());
                    }
                    @Override public void onProgress(int p, String m) {}
                    @Override
                    public void onComplete(List<SubnetTablePanel.SubnetRow> result) {
                        resultView.showTable(result);
                        clearErrors(startError, endError);
                        statusCallback.accept("转换完成，共 " + result.size() + " 个 CIDR 块");
                    }
                    @Override
                    public void onError(Exception ex) {
                        if (ex instanceof ValidationError) {
                            ValidationError ve = (ValidationError) ex;
                            if (ve.getField() == ValidationError.Field.IPV6_ADDRESS) {
                                startError.setError(txtStart, ve.getMessage());
                            } else {
                                endError.setError(txtEnd, ve.getMessage());
                            }
                        } else {
                            startError.setError(txtStart, ex.getMessage());
                        }
                        statusCallback.accept("错误: " + ex.getMessage());
                    }
                }
            );
        });

        bindEnterKey((JTextField) txtEnd.getEditor().getEditorComponent(), btnConvert);
    }
}