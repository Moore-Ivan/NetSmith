package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.math.BigInteger;
import java.util.function.Consumer;

public class IPv6EUI64Panel extends BaseOperationPanel {
    
    private final ResultView resultView;

    public IPv6EUI64Panel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }
    
    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("根据 MAC 地址生成 EUI-64 接口标识和完整 IPv6 地址");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        HistoryComboBox txtMac = new HistoryComboBox("mac_address", 200);
        txtMac.setToolTipText("例如: 00-11-22-33-44-55 或 00:11:22:33:44:55");
        
        HistoryComboBox txtPrefix = new HistoryComboBox("ipv6_prefix", 200);
        txtPrefix.setToolTipText("例如: 2001:db8::/64（前缀长度必须 <= 64）");
        
        ErrorIndicator macError = new ErrorIndicator();
        ErrorIndicator prefixError = new ErrorIndicator();
        
        JButton btnGenerate = new JButton("生成 EUI-64");
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(new JLabel("MAC 地址:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(txtMac, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        add(macError, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(new JLabel("IPv6 前缀:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(txtPrefix, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        add(prefixError, gbc);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnGenerate);
        
        JButton exampleBtn = new JButton("示例");
        exampleBtn.addActionListener(e -> {
            txtMac.setText("00-11-22-33-44-55");
            txtPrefix.setText("2001:db8::/64");
            txtMac.save();
            txtPrefix.save();
        });
        buttonPanel.add(exampleBtn);
        
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> {
            txtMac.setText("");
            txtPrefix.setText("");
            macError.clearError();
            prefixError.clearError();
            resultView.showText("");
        });
        buttonPanel.add(clearBtn);
        
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        add(buttonPanel, gbc);
        
        btnGenerate.addActionListener(e -> {
            txtMac.save();
            txtPrefix.save();
            String mac = txtMac.getText().trim();
            String prefix = txtPrefix.getText().trim();
            
            CalculationWorker.executeWithProgress(this, "正在生成 EUI-64 地址...", btnGenerate,
                new CalculationWorker.CallableWithProgress<String>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public String call() throws Exception {
                        // 使用验证后返回的标准化 MAC 地址，避免重复解析
                        String cleanMac = IPv6Validator.validateMacAddress(mac);
                        
                        StringBuilder sb = new StringBuilder();
                        
                        String eui64 = IPv6Calculator.generateEUI64(cleanMac);
                        sb.append("MAC 地址       : ").append(mac).append("\n");
                        sb.append("EUI-64 接口标识: ").append(eui64).append("\n");
                        
                        if (!prefix.isEmpty()) {
                            String ipv6 = IPv6Calculator.generateIPv6FromMAC(prefix, cleanMac);
                            sb.append("完整 IPv6 地址 : ").append(ipv6).append("\n");
                            
                            String[] prefixParts = prefix.split("/");
                            int prefixLen = Integer.parseInt(prefixParts[1]);
                            BigInteger network = IPv6Calculator.parseIPv6(prefixParts[0]);
                            network = IPv6Calculator.getNetworkAddress(network, prefixLen);
                            sb.append("网络前缀       : ").append(IPv6Calculator.formatCompressed(network))
                              .append("/").append(prefixLen).append("\n");
                            sb.append("接口 ID       : ").append(eui64).append("\n");
                        }
                        
                        return sb.toString();
                    }
                },
                new CalculationWorker.CalculationCallback<String>() {
                    @Override
                    public void onStart() {
                        resultView.showText("");
                        macError.clearError();
                        prefixError.clearError();
                    }
                    @Override public void onProgress(int p, String m) {}
                    @Override
                    public void onComplete(String result) {
                        resultView.showText(result);
                        macError.clearError();
                        prefixError.clearError();
                        statusCallback.accept("生成完成");
                    }
                    @Override
                    public void onError(Exception ex) {
                        if (ex instanceof ValidationError) {
                            ValidationError ve = (ValidationError) ex;
                            if (ve.getField() == ValidationError.Field.MAC_ADDRESS) {
                                macError.setError(txtMac, ve.getMessage());
                            } else {
                                prefixError.setError(txtPrefix, ve.getMessage());
                            }
                        } else {
                            macError.setError(txtMac, ex.getMessage());
                        }
                        statusCallback.accept("错误: " + ex.getMessage());
                    }
                }
            );
        });
        
        bindEnterKey((JTextField) txtMac.getEditor().getEditorComponent(), btnGenerate);
        bindEnterKey((JTextField) txtPrefix.getEditor().getEditorComponent(), btnGenerate);
    }
}
