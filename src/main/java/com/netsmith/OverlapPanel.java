package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.function.Consumer;

public class OverlapPanel extends BaseOperationPanel {
    private final ResultView resultView;

    public OverlapPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("输入多个 CIDR 网段检测冲突");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        HistoryComboBox txtCidrs = new HistoryComboBox("cidrs_overlap", 420);
        txtCidrs.setToolTipText("例如: 192.168.1.0/24,192.168.1.128/25,192.168.2.0/24");
        JButton btnDetect = new JButton("检测冲突");

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
        buttonPanel.add(btnDetect);
        JButton example = new JButton("示例");
        example.addActionListener(e -> {
            txtCidrs.setText("192.168.1.0/24,192.168.1.128/25,10.0.0.0/8");
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

        btnDetect.addActionListener(e -> {
            txtCidrs.save();
            String input = txtCidrs.getText().trim();
            CalculationWorker.executeWithProgress(this, "正在检测网段冲突...", btnDetect,
                new CalculationWorker.CallableWithProgress<String>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public String call() throws Exception {
                        String[] cidrs = CidrValidator.validateCidrList(input, 2);
                        return SubnetCalculator.detectOverlaps(cidrs);
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

        bindEnterKey((JTextField) txtCidrs.getEditor().getEditorComponent(), btnDetect);
    }
}