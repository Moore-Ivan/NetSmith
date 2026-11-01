package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
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
    private static final Map<JComponent, Border> originalBorders = Collections.synchronizedMap(new WeakHashMap<>());
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
    
    private ImageIcon createErrorIcon() {
        int size = 16;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        
        g2d.setColor(new Color(220, 53, 69));
        g2d.fillOval(1, 1, size - 2, size - 2);
        
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.drawString("!", 5, 13);
        
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
            synchronized (originalBorders) {
                originalBorders.putIfAbsent(target, target.getBorder());
            }
            synchronized (errorCounts) {
                int count = errorCounts.getOrDefault(target, 0);
                count++;
                errorCounts.put(target, count);
                if (count == 1) {
                    target.setBorder(BorderFactory.createLineBorder(new Color(220, 53, 69), 2));
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
                    Border orig;
                    synchronized (originalBorders) {
                        orig = originalBorders.remove(targetComponent);
                    }
                    if (orig != null) {
                        targetComponent.setBorder(orig);
                    }
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