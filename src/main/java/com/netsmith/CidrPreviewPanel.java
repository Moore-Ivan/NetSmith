package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class CidrPreviewPanel extends JPanel {
    
    private JLabel networkLabel;
    private JLabel maskLabel;
    private JLabel wildcardLabel;
    private JLabel hostCountLabel;
    private JPanel previewArea;
    private Border normalBorder;
    private Border errorBorder;
    private JTextField boundTextField;
    private KeyAdapter boundKeyListener;
    private java.awt.event.FocusAdapter boundFocusListener;
    private Timer debounceTimer;
    private static final int DEBOUNCE_DELAY = 100;
    
    public CidrPreviewPanel() {
        setLayout(new BorderLayout());
        
        previewArea = new JPanel(new GridLayout(4, 2, 8, 4));
        previewArea.setBorder(BorderFactory.createTitledBorder("实时预览"));
        previewArea.setEnabled(false);
        previewArea.setOpaque(true);
        
        normalBorder = previewArea.getBorder();
        errorBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(220, 53, 69), 2), 
            "实时预览", 
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            null,
            new Color(220, 53, 69)
        );
        
        networkLabel = new JLabel("---");
        maskLabel = new JLabel("---");
        wildcardLabel = new JLabel("---");
        hostCountLabel = new JLabel("---");
        
        previewArea.add(new JLabel("网络地址:"));
        previewArea.add(networkLabel);
        previewArea.add(new JLabel("子网掩码:"));
        previewArea.add(maskLabel);
        previewArea.add(new JLabel("反掩码:"));
        previewArea.add(wildcardLabel);
        previewArea.add(new JLabel("可用主机数:"));
        previewArea.add(hostCountLabel);
        
        add(previewArea, BorderLayout.CENTER);
        
        debounceTimer = new Timer(DEBOUNCE_DELAY, e -> {
            if (boundTextField != null) {
                updatePreview(boundTextField.getText());
            }
        });
        debounceTimer.setRepeats(false);
    }
    
    public void bindToTextField(JTextField textField) {
        if (boundTextField != null && boundKeyListener != null) {
            boundTextField.removeKeyListener(boundKeyListener);
            boundTextField.removeFocusListener(boundFocusListener);
        }
        
        this.boundTextField = textField;
        
        boundKeyListener = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                debounceTimer.restart();
            }
        };
        boundFocusListener = new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                debounceTimer.stop();
                updatePreview(textField.getText());
            }
        };
        
        textField.addKeyListener(boundKeyListener);
        textField.addFocusListener(boundFocusListener);
    }
    
    private void updatePreview(String cidr) {
        if (cidr == null || cidr.trim().isEmpty()) {
            clearPreview();
            return;
        }
        
        try {
            String[] parts = cidr.trim().split("/");
            if (parts.length != 2) {
                showError("格式错误，应为 x.x.x.x/yy");
                return;
            }

            String ip = parts[0];
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                // 单独捕获并给出中文提示，避免直接显示 "For input string: \"xx\"" 这类英文消息
                showError("前缀长度必须是数字");
                return;
            }

            if (prefix < 0 || prefix > 32) {
                showError("前缀必须在 0-32 之间");
                return;
            }
            
            long ipLong = SubnetCalculator.ipToLong(ip);
            long network = SubnetCalculator.getNetworkAddress(ipLong, prefix);
            long mask = SubnetCalculator.getSubnetMask(prefix);
            long wildcard = SubnetCalculator.getWildcardMask(prefix);
            long hosts = SubnetCalculator.getUsableHostCount(prefix);
            
            networkLabel.setText(SubnetCalculator.longToIp(network) + "/" + prefix);
            maskLabel.setText(SubnetCalculator.longToIp(mask));
            wildcardLabel.setText(SubnetCalculator.longToIp(wildcard));
            hostCountLabel.setText(String.valueOf(hosts));
            
            previewArea.setBorder(normalBorder);
            previewArea.setBackground(UIManager.getColor("Panel.background"));
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }
    
    private void showError(String message) {
        networkLabel.setText("<html><span style='color:red;'>" + message + "</span></html>");
        maskLabel.setText("---");
        wildcardLabel.setText("---");
        hostCountLabel.setText("---");
        previewArea.setBorder(errorBorder);
        previewArea.setBackground(getThemeErrorBackground());
    }

    /**
     * 主题感知的错误背景：以 FlatLaf 语义错误色（Component.error.focusedBorderColor）
     * 与面板背景按 1:3 混合派生，暗色主题下自动偏暗不再刺眼；
     * 取不到主题色时回退浅红。
     */
    private static Color getThemeErrorBackground() {
        Color error = UIManager.getColor("Component.error.focusedBorderColor");
        Color base = UIManager.getColor("Panel.background");
        if (error == null || base == null) {
            return new Color(255, 240, 240);
        }
        return new Color(
                (error.getRed() + base.getRed() * 3) / 4,
                (error.getGreen() + base.getGreen() * 3) / 4,
                (error.getBlue() + base.getBlue() * 3) / 4);
    }
    
    public void refresh() {
        if (boundTextField != null) {
            debounceTimer.stop();
            updatePreview(boundTextField.getText());
        }
    }

    public void clearPreview() {
        networkLabel.setText("---");
        maskLabel.setText("---");
        wildcardLabel.setText("---");
        hostCountLabel.setText("---");
        previewArea.setBorder(normalBorder);
        previewArea.setBackground(UIManager.getColor("Panel.background"));
    }
    
    @Override
    public void removeNotify() {
        super.removeNotify();
        // 面板离开显示层级时停止防抖定时器；
        // 保留定时器与监听器实例，避免下次显示时重复注册监听器或出现定时器为 null 的空指针
        if (debounceTimer != null) {
            debounceTimer.stop();
        }
    }
}
