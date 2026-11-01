package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.function.Consumer;

public class IPv6MulticastPanel extends BaseOperationPanel {
    
    private final ResultView resultView;

    public IPv6MulticastPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }
    
    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("解析 IPv6 多播地址");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        HistoryComboBox txtAddr = new HistoryComboBox("ipv6_multicast", 320);
        txtAddr.setToolTipText("例如: ff02::1（链路本地所有节点组）");
        
        ErrorIndicator errorIndicator = new ErrorIndicator();
        
        JButton btnParse = new JButton("解析");
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(new JLabel("多播地址:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        add(txtAddr, gbc);
        gbc.gridx = 4;
        gbc.weightx = 0;
        add(errorIndicator, gbc);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnParse);
        
        JButton exampleBtn = new JButton("示例");
        exampleBtn.addActionListener(e -> {
            txtAddr.setText("ff02::1");
            txtAddr.save();
        });
        buttonPanel.add(exampleBtn);
        
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> {
            txtAddr.setText("");
            errorIndicator.clearError();
            resultView.showText("");
        });
        buttonPanel.add(clearBtn);
        
        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 5;
        add(buttonPanel, gbc);
        
        btnParse.addActionListener(e -> {
            txtAddr.save();
            String addr = txtAddr.getText().trim();
            
            CalculationWorker.executeWithProgress(this, "正在解析多播地址...", btnParse,
                new CalculationWorker.CallableWithProgress<String>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public String call() throws Exception {
                        return IPv6Calculator.parseMulticastAddress(addr);
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
                        statusCallback.accept("解析完成");
                    }
                    @Override
                    public void onError(Exception ex) {
                        errorIndicator.setError(txtAddr, ex.getMessage());
                        statusCallback.accept("错误: " + ex.getMessage());
                    }
                }
            );
        });
        
        bindEnterKey((JTextField) txtAddr.getEditor().getEditorComponent(), btnParse);
    }
}
