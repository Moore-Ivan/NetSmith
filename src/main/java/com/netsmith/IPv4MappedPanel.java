package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.math.BigInteger;
import java.util.function.Consumer;

public class IPv4MappedPanel extends BaseOperationPanel {
    
    private final ResultView resultView;

    public IPv4MappedPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }
    
    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("IPv4 映射 IPv6 地址转换");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        HistoryComboBox txtAddress = new HistoryComboBox("ipv4_mapped", 320);
        txtAddress.setToolTipText("输入 IPv4 地址（如 192.0.2.1）或 IPv6 地址（如 ::ffff:192.0.2.1）");
        
        ErrorIndicator errorIndicator = new ErrorIndicator();
        
        JButton btnConvert = new JButton("转换");
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(new JLabel("地址:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        add(txtAddress, gbc);
        gbc.gridx = 4;
        gbc.weightx = 0;
        add(errorIndicator, gbc);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnConvert);
        
        JButton exampleBtn = new JButton("示例");
        exampleBtn.addActionListener(e -> {
            txtAddress.setText("192.0.2.1");
            txtAddress.save();
        });
        buttonPanel.add(exampleBtn);
        
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> {
            txtAddress.setText("");
            errorIndicator.clearError();
            resultView.showText("");
        });
        buttonPanel.add(clearBtn);
        
        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 5;
        add(buttonPanel, gbc);
        
        btnConvert.addActionListener(e -> {
            txtAddress.save();
            String address = txtAddress.getText().trim();
            
            CalculationWorker.executeWithProgress(this, "正在转换地址...", btnConvert,
                new CalculationWorker.CallableWithProgress<String>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public String call() throws Exception {
                        StringBuilder sb = new StringBuilder();
                        
                        if (address.contains(".") && !address.contains(":")) {
                            IPv6Validator.validateIPv4Address(address);
                            
                            String mapped = IPv6Calculator.formatIPv4MappedAddress(address);
                            BigInteger ip = IPv6Calculator.parseIPv6(mapped);
                            
                            sb.append("输入地址       : ").append(address).append("\n");
                            sb.append("IPv6 映射地址  : ").append(mapped).append("\n");
                            sb.append("完整展开       : ").append(IPv6Calculator.formatFull(ip)).append("\n");
                            sb.append("地址类型       : IPv4 映射 IPv6 地址 (::ffff:0:0/96)\n");
                            sb.append("说明           : 用于 IPv6 网络中表示 IPv4 地址\n");
                        } else {
                            BigInteger ip = IPv6Calculator.parseIPv6(address);
                            
                            sb.append("输入地址       : ").append(address).append("\n");
                            sb.append("完整展开       : ").append(IPv6Calculator.formatFull(ip)).append("\n");
                            
                            if (IPv6Calculator.isIPv4MappedAddress(ip)) {
                                String ipv4 = IPv6Calculator.getIPv4FromMappedAddress(ip);
                                sb.append("地址类型       : IPv4 映射 IPv6 地址 (::ffff:0:0/96)\n");
                                sb.append("映射的 IPv4    : ").append(ipv4).append("\n");
                                sb.append("标准表示       : ::ffff:").append(ipv4).append("\n");
                            } else if (IPv6Calculator.isIPv4CompatibleAddress(ip)) {
                                String ipv4 = IPv6Calculator.getIPv4FromMappedAddress(ip);
                                sb.append("地址类型       : IPv4 兼容 IPv6 地址 (已废弃)\n");
                                sb.append("映射的 IPv4    : ").append(ipv4).append("\n");
                                sb.append("说明           : 此格式已废弃，推荐使用 ::ffff: 格式\n");
                            } else {
                                sb.append("地址类型       : 普通 IPv6 地址\n");
                                sb.append("说明           : 此地址不是 IPv4 映射或兼容的 IPv6 地址\n");
                            }
                        }
                        
                        return sb.toString();
                    }
                },
                new CalculationWorker.CalculationCallback<String>() {
                    @Override
                    public void onStart() {
                        resultView.showText("");
                        errorIndicator.clearError();
                    }
                    @Override public void onProgress(int p, String m) {}
                    @Override
                    public void onComplete(String result) {
                        resultView.showText(result);
                        errorIndicator.clearError();
                        statusCallback.accept("转换完成");
                    }
                    @Override
                    public void onError(Exception ex) {
                        errorIndicator.setError(txtAddress, ex.getMessage());
                        statusCallback.accept("错误: " + ex.getMessage());
                    }
                }
            );
        });
        
        bindEnterKey((JTextField) txtAddress.getEditor().getEditorComponent(), btnConvert);
    }
}
