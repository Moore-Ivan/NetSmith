package com.netsmith;

import javax.swing.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class HistoryComboBox extends JComboBox<String> {
    
    private String fieldName;
    private String savedValue = "";
    private ActionListener actionListener;
    private FocusListener focusListener;
    // 使用 AtomicBoolean 确保多线程安全
    private final AtomicBoolean programmaticallyUpdating = new AtomicBoolean(false);
    
    public HistoryComboBox(String fieldName) {
        this(fieldName, 240);
    }
    
    public HistoryComboBox(String fieldName, int preferredWidth) {
        this.fieldName = fieldName;
        setEditable(true);
        // 输入框高度与全局圆角匹配，避免与相邻按钮高度参差
        setMinimumSize(new java.awt.Dimension(preferredWidth, 30));
        setPreferredSize(new java.awt.Dimension(preferredWidth, 30));
        setMaximumRowCount(10);
        // 一键清空当前输入，减少反复全选删除的操作
        JTextField editor = (JTextField) getEditor().getEditorComponent();
        editor.putClientProperty("JTextField.showClearButton", true);
        loadHistory();
        savedValue = getText();
        registerListeners();
        // 注册"全部历史已清除"通知，保证清除历史后已存在的实例立即刷新下拉列表
        ConfigStore.addHistoryClearListener(this::onHistoryCleared);
    }

    /** 清除全部历史后的回调：清空下拉项，保留编辑器中用户正在输入的文本 */
    private void onHistoryCleared() {
        removeAllItems();
    }
    
    private void loadHistory() {
        removeAllItems();
        List<String> history = ConfigStore.getHistory(fieldName);
        for (String item : history) {
            addItem(item);
        }
    }
    
    /**
     * 注册动作/焦点监听器（幂等）。
     * 在构造和 addNotify（组件重新加入显示层级）时调用，
     * 修复 removeNotify 移除监听器后组件重新显示时监听器永久丢失的问题。
     */
    private void registerListeners() {
        if (actionListener != null && focusListener != null) {
            return; // 已注册，避免重复累积
        }
        final WeakReference<HistoryComboBox> weakThis = new WeakReference<>(this);

        if (actionListener == null) {
            actionListener = e -> {
                HistoryComboBox comboBox = weakThis.get();
                if (comboBox != null && !comboBox.programmaticallyUpdating.get()) {
                    Object selected = comboBox.getSelectedItem();
                    if (selected != null) {
                        String selectedText = selected.toString();
                        comboBox.savedValue = selectedText.trim();
                        if (!selectedText.trim().isEmpty()) {
                            comboBox.programmaticallyUpdating.set(true);
                            try {
                                ConfigStore.addHistory(comboBox.fieldName, selectedText.trim());
                                comboBox.addToDropdown(selectedText.trim());
                            } finally {
                                comboBox.programmaticallyUpdating.set(false);
                            }
                        }
                    }
                }
            };
            addActionListener(actionListener);
        }

        if (focusListener == null) {
            JTextField editor = (JTextField) getEditor().getEditorComponent();
            focusListener = new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    HistoryComboBox comboBox = weakThis.get();
                    if (comboBox != null) {
                        // 保存用户当前输入的文本
                        String currentText = editor.getText();
                        comboBox.saveIfChanged();
                        // 恢复用户输入的文本，防止模型变更导致输入丢失
                        editor.setText(currentText);
                    }
                }
            };
            editor.addFocusListener(focusListener);
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // 组件重新加入显示层级时恢复监听器（与 removeNotify 配对）
        registerListeners();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (actionListener != null) {
            removeActionListener(actionListener);
            actionListener = null;
        }
        if (focusListener != null) {
            JTextField editor = (JTextField) getEditor().getEditorComponent();
            editor.removeFocusListener(focusListener);
            focusListener = null;
        }
    }
    
    private void saveIfChanged() {
        String text = getText().trim();
        if (!text.isEmpty() && !text.equals(savedValue)) {
            ConfigStore.addHistory(fieldName, text);
            savedValue = text;
            addToDropdown(text);
        }
    }
    
    public void save() {
        String text = getText().trim();
        if (!text.isEmpty()) {
            programmaticallyUpdating.set(true);
            try {
                ConfigStore.addHistory(fieldName, text);
                savedValue = text;
                addToDropdown(text);
            } finally {
                programmaticallyUpdating.set(false);
            }
        }
    }
    
    private void addToDropdown(String item) {
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) getModel();
        
        // 保存当前编辑器的文本，防止模型变更导致输入丢失
        String editorText = getText();
        Object selectedBefore = getSelectedItem();
        
        int index = -1;
        for (int i = 0; i < model.getSize(); i++) {
            if (model.getElementAt(i).equals(item)) {
                index = i;
                break;
            }
        }
        
        if (index == 0) {
            // 如果已经在第一位，不需要移动，但需要确保编辑器文本保持不变
            if (!editorText.equals(item)) {
                // 用户正在输入不同于列表中第一项的内容，保持用户输入
                JTextField editor = (JTextField) getEditor().getEditorComponent();
                editor.setText(editorText);
            }
            return;
        }
        
        programmaticallyUpdating.set(true);
        try {
            if (index > 0) {
                model.removeElementAt(index);
            }
            
            model.insertElementAt(item, 0);
            
            while (model.getSize() > 10) {
                model.removeElementAt(model.getSize() - 1);
            }
            
            // 只有当之前选中的就是这个项目时才重新设置选中项
            if (selectedBefore != null && selectedBefore.toString().equals(item)) {
                setSelectedItem(item);
            } else {
                // 用户正在输入新内容，保持编辑器文本不变
                JTextField editor = (JTextField) getEditor().getEditorComponent();
                editor.setText(editorText);
            }
        } finally {
            programmaticallyUpdating.set(false);
        }
    }
    
    public String getText() {
        // 防御性检查：编辑器组件理论上不为 null，但避免异常 L&F 下的 NPE
        if (getEditor() != null && getEditor().getEditorComponent() instanceof JTextField editor) {
            return editor.getText();
        }
        Object selected = getSelectedItem();
        return selected != null ? selected.toString() : "";
    }
    
    public void setText(String text) {
        programmaticallyUpdating.set(true);
        try {
            setSelectedItem(text);
            savedValue = text == null ? "" : text.trim();
        } finally {
            programmaticallyUpdating.set(false);
        }
    }
    
    public void clearHistory() {
        ConfigStore.clearHistory(fieldName);
        removeAllItems();
        savedValue = "";
    }
    
    public String getFieldName() {
        return fieldName;
    }
}