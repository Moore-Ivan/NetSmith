package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.function.Consumer;

public class IPv6PrefixDelegationPanel extends BaseOperationPanel {
    
    private final ResultView resultView;

    public IPv6PrefixDelegationPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }
    
    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("计算 DHCPv6 前缀委派信息");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        HistoryComboBox txtPrefix = new HistoryComboBox("dhcpv6_prefix", 250);
        txtPrefix.setToolTipText("例如: 2001:db8:100::/48（ISP 委派的前缀）");
        
        HistoryComboBox txtSubnetPrefix = new HistoryComboBox("dhcpv6_subnet_prefix", 80);
        txtSubnetPrefix.setToolTipText("例如: 64（每个子网的前缀长度）");
        txtSubnetPrefix.setText("64");
        
        ErrorIndicator prefixError = new ErrorIndicator();
        ErrorIndicator subnetError = new ErrorIndicator();
        
        JButton btnCalculate = new JButton("计算");
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(new JLabel("委派前缀:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(txtPrefix, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        add(prefixError, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(new JLabel("子网前缀长度:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.3;
        add(txtSubnetPrefix, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        add(subnetError, gbc);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnCalculate);
        
        JButton exampleBtn = new JButton("示例");
        exampleBtn.addActionListener(e -> {
            txtPrefix.setText("2001:db8:100::/48");
            txtSubnetPrefix.setText("64");
            txtPrefix.save();
            txtSubnetPrefix.save();
        });
        buttonPanel.add(exampleBtn);
        
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> {
            txtPrefix.setText("");
            txtSubnetPrefix.setText("64");
            prefixError.clearError();
            subnetError.clearError();
            resultView.showText("");
        });
        buttonPanel.add(clearBtn);
        
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        add(buttonPanel, gbc);
        
        btnCalculate.addActionListener(e -> {
            txtPrefix.save();
            txtSubnetPrefix.save();
            String prefix = txtPrefix.getText().trim();
            String subnetPrefixStr = txtSubnetPrefix.getText().trim();
            
            CalculationWorker.executeWithProgress(this, "正在计算前缀委派...", btnCalculate,
                new CalculationWorker.CallableWithProgress<String>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public String call() throws Exception {
                        IPv6Validator.validateCidr(prefix);
                        int subnetPrefix = IPv6Validator.validatePrefix(subnetPrefixStr, 0, 128);
                        return IPv6Calculator.calculatePrefixDelegation(prefix, subnetPrefix);
                    }
                },
                new CalculationWorker.CalculationCallback<String>() {
                    @Override
                    public void onStart() {
                        resultView.showText("");
                        prefixError.clearError();
                        subnetError.clearError();
                    }
                    @Override public void onProgress(int p, String m) {}
                    @Override
                    public void onComplete(String result) {
                        resultView.showText(result);
                        prefixError.clearError();
                        subnetError.clearError();
                        statusCallback.accept("计算完成");
                    }
                    @Override
                    public void onError(Exception ex) {
                        if (ex instanceof ValidationError) {
                            ValidationError ve = (ValidationError) ex;
                            if (ve.getField() == ValidationError.Field.PREFIX) {
                                subnetError.setError(txtSubnetPrefix, ve.getMessage());
                            } else {
                                prefixError.setError(txtPrefix, ve.getMessage());
                            }
                        } else {
                            prefixError.setError(txtPrefix, ex.getMessage());
                        }
                        statusCallback.accept("错误: " + ex.getMessage());
                    }
                }
            );
        });
        
        bindEnterKey((JTextField) txtPrefix.getEditor().getEditorComponent(), btnCalculate);
        bindEnterKey((JTextField) txtSubnetPrefix.getEditor().getEditorComponent(), btnCalculate);
    }
}
