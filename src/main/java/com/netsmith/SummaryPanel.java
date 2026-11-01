package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.function.Consumer;

public class SummaryPanel extends BaseOperationPanel {
    private final ResultView resultView;

    public SummaryPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("路由汇总（超网）");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        HistoryComboBox txtCidrs = new HistoryComboBox("cidrs_summary", 420);
        txtCidrs.setToolTipText("例如: 192.168.0.0/24,192.168.1.0/24,192.168.2.0/24");
        JButton btnSummary = new JButton("汇总");

        ErrorIndicator errorIndicator = new ErrorIndicator();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(new JLabel("CIDR 列表 (逗号分隔):"), gbc);
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
            txtCidrs.setText("192.168.0.0/24,192.168.1.0/24,192.168.2.0/24");
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
            CalculationWorker.executeWithProgress(this, "正在汇总路由...", btnSummary,
                new CalculationWorker.CallableWithProgress<String>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public String call() throws Exception {
                        String[] cidrs = CidrValidator.validateCidrList(input, 1);
                        return SubnetCalculator.summarize(cidrs);
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