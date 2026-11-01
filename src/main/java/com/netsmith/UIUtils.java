package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class UIUtils {
    
    public static HistoryComboBox createCidrField(String fieldName) {
        return createCidrField(fieldName, 260);
    }
    
    public static HistoryComboBox createCidrField(String fieldName, int width) {
        HistoryComboBox field = new HistoryComboBox(fieldName, width);
        field.setToolTipText("例如: 192.168.1.0/24");
        return field;
    }
    
    public static HistoryComboBox createIPv6CidrField(String fieldName) {
        HistoryComboBox field = new HistoryComboBox(fieldName, 260);
        field.setToolTipText("例如: 2001:db8::/32");
        return field;
    }
    
    public static HistoryComboBox createNumberField(String fieldName) {
        return createNumberField(fieldName, 80);
    }
    
    public static HistoryComboBox createNumberField(String fieldName, int width) {
        return new HistoryComboBox(fieldName, width);
    }
    
    public static JPanel createInputRow(GridBagConstraints gbc, JLabel label, JComponent input, 
                                        ErrorIndicator errorIndicator) {
        JPanel row = new JPanel(new GridBagLayout());
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        row.add(label, gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        row.add(input, gbc);
        
        if (errorIndicator != null) {
            gbc.gridx = 5;
            gbc.weightx = 0;
            row.add(errorIndicator, gbc);
        }
        
        return row;
    }
    
    public static JButton createButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        return button;
    }
    
    public static JPanel createButtonPanel(JButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        for (JButton button : buttons) {
            panel.add(button);
        }
        return panel;
    }
    
    public static Border createTitledBorder(String title) {
        return BorderFactory.createTitledBorder(title);
    }
    
    public static GridBagConstraints createGridBagConstraints(int insets) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(insets, insets, insets, insets);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;
        return gbc;
    }
    
    public static GridBagConstraints createGridBagConstraints() {
        return createGridBagConstraints(6);
    }
    
    public static void addInputRow(JPanel parent, GridBagConstraints gbc, int rowIndex,
                                    JLabel label, JComponent input, ErrorIndicator errorIndicator) {
        gbc.gridx = 0;
        gbc.gridy = rowIndex;
        gbc.weightx = 0;
        parent.add(label, gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        parent.add(input, gbc);
        
        if (errorIndicator != null) {
            gbc.gridx = 5;
            gbc.weightx = 0;
            parent.add(errorIndicator, gbc);
        }
    }
    
    public static void addButtonRow(JPanel parent, GridBagConstraints gbc, int rowIndex, JButton... buttons) {
        JPanel buttonPanel = createButtonPanel(buttons);
        gbc.gridx = 0;
        gbc.gridy = rowIndex;
        gbc.gridwidth = 6;
        gbc.weightx = 1.0;
        parent.add(buttonPanel, gbc);
    }
    
    public static void bindEnterKey(JTextField field, JButton button) {
        field.addActionListener(e -> button.doClick());
    }
}