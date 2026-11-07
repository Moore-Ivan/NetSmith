package com.netsmith;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class ErrorIndicator extends JLabel {
    
    private JComponent targetComponent;
    private String errorMessage;
    private boolean hasError = false;
    /** 记录设置错误前的轮廓样式，清除错误时原样恢复 */
    private static final Map<JComponent, Object> originalOutlines = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<JComponent, Integer> errorCounts = Collections.synchronizedMap(new WeakHashMap<>());
    
    public ErrorIndicator() {
        super();
        setIcon(createErrorIcon());
        setVisible(false);
        setToolTipText("");
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (hasError && errorMessage != null) {
                    showToolTip();
                }
            }
        });
    }
    
    @Override
    public void updateUI() {
        super.updateUI();
        // 主题切换后错误图标颜色同步更新
        setIcon(createErrorIcon());
    }
    
    private ImageIcon createErrorIcon() {
        int size = 16;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.setColor(ThemeManager.error());
        g2d.fillOval(1, 1, size - 2, size - 2);
        
        g2d.setColor(Color.WHITE);
        g2d.setFont(ThemeManager.boldFont().deriveFont(Font.BOLD, 11f));
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString("!", (size - fm.stringWidth("!")) / 2,
                (size - fm.getHeight()) / 2 + fm.getAscent());
        
        g2d.dispose();
        return new ImageIcon(img);
    }
    
    public void setError(JComponent target, String message) {
        clearError();
        
        this.targetComponent = target;
        this.errorMessage = message;
        this.hasError = true;
        setVisible(true);
        
        if (target != null) {
            synchronized (errorCounts) {
                int count = errorCounts.getOrDefault(target, 0);
                count++;
                errorCounts.put(target, count);
                if (count == 1) {
                    // 用 FlatLaf 轮廓而非替换边框：保留输入框圆角，且不改变组件尺寸
                    synchronized (originalOutlines) {
                        originalOutlines.put(target, target.getClientProperty("JComponent.outline"));
                    }
                    target.putClientProperty("JComponent.outline", "error");
                    target.repaint();
                }
            }
        }
        
        setToolTipText("<html><body style='padding:4px;'><b>错误:</b><br/>" + message + "</body></html>");
    }
    
    public void clearError() {
        this.hasError = false;
        this.errorMessage = null;
        setVisible(false);
        
        if (targetComponent != null) {
            synchronized (errorCounts) {
                int count = errorCounts.getOrDefault(targetComponent, 0);
                count--;
                if (count <= 0) {
                    errorCounts.remove(targetComponent);
                    Object originalOutline;
                    synchronized (originalOutlines) {
                        originalOutline = originalOutlines.remove(targetComponent);
                    }
                    targetComponent.putClientProperty("JComponent.outline", originalOutline);
                    targetComponent.repaint();
                } else {
                    errorCounts.put(targetComponent, count);
                }
            }
            targetComponent = null;
        }
    }
    
    private void showToolTip() {
    }
    
    public boolean hasError() {
        return hasError;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}