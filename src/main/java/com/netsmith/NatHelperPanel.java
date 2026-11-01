package com.netsmith;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * NAT 配置辅助面板：输入内网 CIDR 与公网出口（单 IP 或 CIDR 地址池），
 * 生成 NAT 转换表建议与参考配置片段。
 */
public class NatHelperPanel extends BaseOperationPanel {
    private final ResultView resultView;

    public NatHelperPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("NAT 配置辅助"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        HistoryComboBox txtInternal = new HistoryComboBox("nat_internal_cidr", 200);
        HistoryComboBox txtEgress = new HistoryComboBox("nat_public_egress", 200);
        JButton btnGenerate = new JButton("生成建议");
        JButton exampleBtn = new JButton("示例");
        JButton clearBtn = new JButton("清空");

        ErrorIndicator internalError = new ErrorIndicator();
        ErrorIndicator egressError = new ErrorIndicator();

        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("内网 CIDR:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        add(txtInternal, gbc);
        gbc.gridx = 3; gbc.weightx = 0; gbc.gridwidth = 1;
        add(internalError, gbc);

        gbc.gridy = 1; gbc.gridx = 0;
        add(new JLabel("公网出口:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        add(txtEgress, gbc);
        gbc.gridx = 3; gbc.weightx = 0; gbc.gridwidth = 1;
        add(egressError, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnGenerate);
        buttonPanel.add(exampleBtn);
        buttonPanel.add(clearBtn);
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 4; gbc.weightx = 1.0;
        add(buttonPanel, gbc);

        JLabel hintLabel = new JLabel("公网地址充足时生成静态一对一映射表；不足时生成 NAPT/PAT 均分与端口块建议");
        hintLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        gbc.gridy = 3; gbc.gridwidth = 4;
        add(hintLabel, gbc);

        exampleBtn.addActionListener(e -> {
            txtInternal.setText("192.168.1.0/24");
            txtInternal.save();
            txtEgress.setText("203.0.113.5");
            txtEgress.save();
        });

        clearBtn.addActionListener(e -> {
            txtInternal.setText("");
            txtEgress.setText("");
            clearErrors(internalError, egressError);
        });

        btnGenerate.addActionListener(e -> {
            txtInternal.save();
            txtEgress.save();
            String internalCidr = txtInternal.getText().trim();
            String egress = txtEgress.getText().trim();
            CalculationWorker.executeWithProgress(this, "正在生成 NAT 建议...", btnGenerate,
                new CalculationWorker.CallableWithProgress<String>() {
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { }
                    @Override
                    public String call() {
                        return SubnetController.natReportSync(internalCidr, egress);
                    }
                },
                new CalculationWorker.CalculationCallback<String>() {
                    @Override public void onProgress(int p, String m) {}
                    @Override
                    public void onComplete(String report) {
                        resultView.showText(report);
                        clearErrors(internalError, egressError);
                    }
                    @Override
                    public void onError(Exception ex) {
                        // CIDR→内网输入框，IP_START（公网出口相关错误）→出口输入框
                        handleValidationError(ex, internalError, egressError, txtInternal, txtEgress,
                                ValidationError.Field.IP_START);
                    }
                }
            );
        });

        bindEnterKey((JTextField) txtEgress.getEditor().getEditorComponent(), btnGenerate);
    }
}
