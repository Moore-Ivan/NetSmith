package com.netsmith;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ShortcutHelp {

    public static void showShortcuts(Component parent) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel titleLabel = new JLabel("快捷键说明");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(titleLabel, BorderLayout.NORTH);

        String[][] shortcuts = {
            {"F1", "打开快捷键说明"},
            {"F2", "循环切换主题：跟随系统 → 亮色 → 暗色"},
            {"F3", "打开进制转换器"},
            {"F4", "打开掩码转换器"},
            {"F9", "检查更新"},
            {"F10", "联系作者"},
            {"F11", "切换全屏模式"},
            {"Ctrl + L", "清空结果区域"},
            {"Ctrl + Shift + L", "一键清空全部历史与结果"},
            {"Ctrl + C", "复制结果到剪贴板"},
            {"Ctrl + PageUp / PageDown", "在六大功能分组间快速切换"},
            {"Enter", "在输入框中按回车执行计算"},
            {"Esc", "取消正在执行的计算"},
        };

        String[] columnNames = {"快捷键", "功能说明"};
        JTable table = new JTable(shortcuts, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(420, 260));
        panel.add(scrollPane, BorderLayout.CENTER);

        JLabel tipLabel = new JLabel("提示：更多功能持续更新中...");
        tipLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        tipLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(tipLabel, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(parent, panel, "快捷键说明", JOptionPane.INFORMATION_MESSAGE);
    }
}
