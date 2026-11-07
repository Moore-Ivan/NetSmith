package com.netsmith;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class ShortcutHelp {

    public static void showShortcuts(Component parent) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(new EmptyBorder(18, 18, 16, 18));

        JPanel headerBox = new JPanel();
        headerBox.setLayout(new BoxLayout(headerBox, BoxLayout.Y_AXIS));
        headerBox.setOpaque(false);
        JLabel titleLabel = new JLabel("快捷键说明");
        titleLabel.setFont(ThemeManager.boldFont().deriveFont(18f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitleLabel = ThemeManager.mutedLabel("按以下组合键可更快完成常用操作");
        subtitleLabel.setFont(ThemeManager.smallFont());
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerBox.add(titleLabel);
        headerBox.add(Box.createVerticalStrut(3));
        headerBox.add(subtitleLabel);
        panel.add(headerBox, BorderLayout.NORTH);

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
        table.setRowHeight(30);
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        DefaultTableCellRenderer keyRenderer = new DefaultTableCellRenderer();
        keyRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        keyRenderer.setFont(ThemeManager.monoBoldFont());
        table.getColumnModel().getColumn(0).setCellRenderer(keyRenderer);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(300);
        table.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(470, 300));
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeManager.divider()));
        panel.add(scrollPane, BorderLayout.CENTER);

        JLabel tipLabel = ThemeManager.mutedLabel("提示：结果表格支持 Ctrl+C 复制选中行，双击单元格可复制单个值");
        tipLabel.setFont(ThemeManager.smallFont());
        panel.add(tipLabel, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(parent, panel, "快捷键说明", JOptionPane.INFORMATION_MESSAGE);
    }
}
