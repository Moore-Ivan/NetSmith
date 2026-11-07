package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
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
    private boolean showingError = false;
    private JTextField boundTextField;
    private KeyAdapter boundKeyListener;
    private java.awt.event.FocusAdapter boundFocusListener;
    private Timer debounceTimer;
    private static final int DEBOUNCE_DELAY = 100;
    
    public CidrPreviewPanel() {
        setLayout(new BorderLayout());
        
        previewArea = new JPanel(new GridBagLayout());
        previewArea.setEnabled(false);
        previewArea.setOpaque(true);
        buildPreviewBorders();
        previewArea.setBorder(normalBorder);
        
        networkLabel = createValueLabel();
        maskLabel = createValueLabel();
        wildcardLabel = createValueLabel();
        hostCountLabel = createValueLabel();
        
        addPreviewRow(0, "网络地址", networkLabel);
        addPreviewRow(1, "子网掩码", maskLabel);
        addPreviewRow(2, "反掩码", wildcardLabel);
        addPreviewRow(3, "可用主机数", hostCountLabel);
        
        add(previewArea, BorderLayout.CENTER);
        
        debounceTimer = new Timer(DEBOUNCE_DELAY, e -> {
            if (boundTextField != null) {
                updatePreview(boundTextField.getText());
            }
        });
        debounceTimer.setRepeats(false);
    }
    
    /** 紧凑的两列布局：标签紧邻取值，避免 GridLayout 把值推到面板中线 */
    private void addPreviewRow(int row, String label, JLabel valueLabel) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0;
        gbc.insets = new Insets(2, 0, 2, 14);
        gbc.weightx = 0;
        previewArea.add(createKeyLabel(label), gbc);
        
        gbc.gridx = 1;
        gbc.insets = new Insets(2, 0, 2, 0);
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        previewArea.add(valueLabel, gbc);
    }
    
    private JLabel createKeyLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ThemeManager.uiFont().deriveFont(Font.PLAIN, 12f));
        return label;
    }
    
    private JLabel createValueLabel() {
        JLabel label = new JLabel("---");
        label.setFont(ThemeManager.monoBoldFont());
        return label;
    }
    
    /** 重建边框：TitledBorder 的字体与错误色依赖当前主题，切换主题后需要重新生成 */
    private void buildPreviewBorders() {
        normalBorder = new CompoundBorder(
                BorderFactory.createTitledBorder(
                        null,
                        "实时预览",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        ThemeManager.boldFont().deriveFont(Font.BOLD, 12f),
                        UIManager.getColor("Label.foreground")),
                new EmptyBorder(4, 10, 8, 10));
        errorBorder = new CompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(ThemeManager.error(), 1),
                        "实时预览",
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        TitledBorder.DEFAULT_POSITION,
                        ThemeManager.boldFont(),
                        ThemeManager.error()),
                new EmptyBorder(6, 10, 10, 10));
    }
    
    @Override
    public void updateUI() {
        super.updateUI();
        if (previewArea != null) {
            buildPreviewBorders();
            previewArea.setBorder(showingError ? errorBorder : normalBorder);
            previewArea.setBackground(showingError
                    ? ThemeManager.errorTint()
                    : UIManager.getColor("Panel.background"));
        }
        if (networkLabel != null) {
            networkLabel.setForeground(showingError
                    ? ThemeManager.error()
                    : UIManager.getColor("Label.foreground"));
        }
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
            
            showingError = false;
            previewArea.setBorder(normalBorder);
            previewArea.setBackground(UIManager.getColor("Panel.background"));
            networkLabel.setForeground(UIManager.getColor("Label.foreground"));
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }
    
    private void showError(String message) {
        showingError = true;
        Color error = ThemeManager.error();
        networkLabel.setForeground(error);
        networkLabel.setText("<html><span style='color:#"
                + String.format("%06x", error.getRGB() & 0xFFFFFF) + ";'>"
                + message + "</span></html>");
        maskLabel.setText("---");
        wildcardLabel.setText("---");
        hostCountLabel.setText("---");
        previewArea.setBorder(errorBorder);
        previewArea.setBackground(ThemeManager.errorTint());
    }
    
    public void refresh() {
        if (boundTextField != null) {
            debounceTimer.stop();
            updatePreview(boundTextField.getText());
        }
    }

    public void clearPreview() {
        showingError = false;
        networkLabel.setForeground(UIManager.getColor("Label.foreground"));
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
