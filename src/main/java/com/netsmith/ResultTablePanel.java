package com.netsmith;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class ResultTablePanel extends JPanel {

    private ResultTableModel model;
    private JTable table;
    private JPopupMenu rightClickMenu;
    private JScrollPane scrollPane;
    
    // 列宽记忆
    private int[] columnWidths = {150, 350}; // 默认3:7比例
    /** 列模型监听器：fireTableStructureChanged 会重建 TableColumn 对象，需要每次重新挂载 */
    private javax.swing.event.TableColumnModelListener columnModelListener;
    /** 面板尺寸监听器只注册一次（不依赖列模型重建） */
    private boolean componentListenerRegistered = false;
    
    public ResultTablePanel() {
        setLayout(new BorderLayout());
        // 加载保存的列宽
        columnWidths = ConfigStore.loadColumnWidths("resultTable", columnWidths);
        initComponents();
        initRightClickMenu();
    }

    private void initComponents() {
        model = new ResultTableModel();
        table = new JTable(model);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        
        // 列对齐：第一列左对齐，第二列左对齐
        DefaultTableCellRenderer leftRenderer = new DefaultTableCellRenderer();
        leftRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        leftRenderer.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        table.getColumnModel().getColumn(0).setCellRenderer(leftRenderer);
        table.getColumnModel().getColumn(1).setCellRenderer(leftRenderer);
        
        // 双击复制单元格
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    int col = table.getSelectedColumn();
                    if (row != -1 && col != -1) {
                        Object value = model.getValueAt(row, col);
                        if (value != null) {
                            copyToClipboard(value.toString());
                        }
                    }
                }
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row != -1) {
                        table.setRowSelectionInterval(row, row);
                        rightClickMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });
        
        this.scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("输出结果"));
        add(scrollPane, BorderLayout.CENTER);
        
        // 设置列宽：加载保存的列宽 + 列宽变化监听（必须在 scrollPane 创建之后）
        applyColumnSettings();
    }
    
    private void initRightClickMenu() {
        rightClickMenu = new JPopupMenu("操作");
        
        // 复制选中的行
        JMenuItem copyRowItem = new JMenuItem("复制选中的行");
        copyRowItem.addActionListener(e -> {
            int[] selectedRows = table.getSelectedRows();
            if (selectedRows.length == 0) return;
            
            StringBuilder sb = new StringBuilder();
            for (int viewRow : selectedRows) {
                int modelRow = table.convertRowIndexToModel(viewRow);
                Object key = model.getValueAt(modelRow, 0);
                Object value = model.getValueAt(modelRow, 1);
                sb.append(key != null ? key.toString() : "").append("\t");
                sb.append(value != null ? value.toString() : "").append("\n");
            }
            copyToClipboard(sb.toString().trim());
        });
        rightClickMenu.add(copyRowItem);
        
        // 复制全部数据
        JMenuItem copyAllItem = new JMenuItem("复制全部数据");
        copyAllItem.addActionListener(e -> copyAllData());
        rightClickMenu.add(copyAllItem);
        
        // 导出为 CSV
        JMenuItem exportCsvItem = new JMenuItem("导出为 CSV");
        exportCsvItem.addActionListener(e -> exportAsCsv());
        rightClickMenu.add(exportCsvItem);
    }
    
    /**
     * 应用列宽设置并注册列宽变化监听器
     * 在初始化表格或列结构变更后（fireTableStructureChanged）调用，确保列宽记忆功能正常工作
     * 使用 AUTO_RESIZE_OFF 模式，通过 ComponentListener 手动调整"值"列填满容器
     */
    private void applyColumnSettings() {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int colCount = Math.min(2, table.getColumnCount());
        for (int i = 0; i < colCount; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
            table.getColumnModel().getColumn(i).setMinWidth(60);
        }
        
        // 列宽变化监听：fireTableStructureChanged 会重建 TableColumn 对象（列宽丢失、
        // 监听挂载点变化），因此每次调用都先移除旧监听器再重新注册，保证不累积也不失效
        if (columnModelListener != null) {
            table.getColumnModel().removeColumnModelListener(columnModelListener);
        }
        columnModelListener = new javax.swing.event.TableColumnModelListener() {
            @Override
            public void columnAdded(javax.swing.event.TableColumnModelEvent e) {}
            @Override
            public void columnRemoved(javax.swing.event.TableColumnModelEvent e) {}
            @Override
            public void columnMoved(javax.swing.event.TableColumnModelEvent e) {}
            @Override
            public void columnMarginChanged(javax.swing.event.ChangeEvent e) {
                columnWidths[0] = table.getColumnModel().getColumn(0).getWidth();
                ConfigStore.saveColumnWidths("resultTable", columnWidths);
                // 调整"值"列以填满容器
                resizeValueColumn();
            }
            @Override
            public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) {}
        };
        table.getColumnModel().addColumnModelListener(columnModelListener);

        // 面板大小变化监听与列模型无关，只注册一次
        if (!componentListenerRegistered) {
            componentListenerRegistered = true;
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    resizeValueColumn();
                }
            });
        }
    }
    
    /**
     * 调整"值"列宽度以填满表格容器
     * "项目"列保持用户设置的宽度，"值"列填充剩余空间
     */
    private void resizeValueColumn() {
        if (table.getColumnCount() < 2) return;
        if (scrollPane == null) return;
        
        // 使用 scrollPane 的视口宽度，而不是 table.getWidth()
        int viewportWidth = scrollPane.getViewport().getWidth();
        if (viewportWidth <= 0) return;
        
        int projectWidth = table.getColumnModel().getColumn(0).getWidth();
        int valueWidth = viewportWidth - projectWidth;
        if (valueWidth < 60) valueWidth = 60; // 最小宽度
        
        table.getColumnModel().getColumn(1).setWidth(valueWidth);
    }

    /**
     * 从文本格式解析并设置数据
     * 支持格式："Key : Value" 或 "Key : Value\nKey2 : Value2"
     * 支持中文冒号（：）和英文冒号（:）
     */
    public void setText(String text) {
        List<ResultRow> rows = new ArrayList<>();
        if (text != null && !text.trim().isEmpty()) {
            String[] lines = text.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                // 优先查找中文冒号，再查找英文冒号
                int colonIndex = line.indexOf('：'); // 中文冒号
                if (colonIndex == -1) {
                    colonIndex = line.indexOf(':'); // 英文冒号
                }
                
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    rows.add(new ResultRow(key, value));
                } else {
                    // 没有冒号，作为单行文本显示
                    rows.add(new ResultRow("", line));
                }
            }
        }
        model.setData(rows);
    }
    
    /**
     * 设置多列数据（用于结构化表格）
     */
    public void setData(List<ResultRow> data) {
        model.setData(data);
    }
    
    /**
     * 设置多列数据（自定义列名）
     * 注意：此方法会触发 fireTableStructureChanged，需要重新应用列宽设置
     */
    public void setData(List<Object[]> data, String[] columnNames) {
        model.setData(data, columnNames);
        // fireTableStructureChanged 会重置列模型，需要重新应用列宽设置
        applyColumnSettings();
    }
    
    /**
     * 清空数据
     */
    public void clearData() {
        model.setData(new ArrayList<>());
    }
    
    /**
     * 获取所有数据作为文本（制表符分隔）
     */
    public String getAllDataAsText() {
        // 空表格直接返回空串：否则会返回纯表头，让调用方误判"有内容可复制"
        if (model.getRowCount() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        // 表头
        for (int col = 0; col < model.getColumnCount(); col++) {
            sb.append(model.getColumnName(col));
            if (col < model.getColumnCount() - 1) sb.append("\t");
        }
        sb.append("\n");
        // 数据行
        for (int row = 0; row < model.getRowCount(); row++) {
            for (int col = 0; col < model.getColumnCount(); col++) {
                Object val = model.getValueAt(row, col);
                sb.append(val != null ? val.toString() : "");
                if (col < model.getColumnCount() - 1) sb.append("\t");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }
    
    private void copyAllData() {
        String data = getAllDataAsText();
        if (data.isEmpty()) return;
        copyToClipboard(data);
    }
    
    private void exportAsCsv() {
        StringBuilder sb = new StringBuilder();
        // 表头
        for (int col = 0; col < model.getColumnCount(); col++) {
            sb.append("\"").append(model.getColumnName(col)).append("\"");
            if (col < model.getColumnCount() - 1) sb.append(",");
        }
        sb.append("\n");
        // 数据行
        for (int row = 0; row < model.getRowCount(); row++) {
            for (int col = 0; col < model.getColumnCount(); col++) {
                Object val = model.getValueAt(row, col);
                sb.append("\"").append(val != null ? val.toString().replace("\"", "\"\"") : "").append("\"");
                if (col < model.getColumnCount() - 1) sb.append(",");
            }
            sb.append("\n");
        }
        copyToClipboard(sb.toString().trim());
    }
    
    private void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    }
    
    public static class ResultRow {
        public final String key;
        public final String value;
        
        public ResultRow(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
    
    public static class ResultTableModel extends AbstractTableModel {
        private List<Object[]> data = new ArrayList<>();
        private String[] columnNames = {"项目", "值"};
        
        public void setData(List<ResultRow> rows) {
            // 保持列结构不变，只更新数据，避免重置列宽
            this.columnNames = new String[]{"项目", "值"};
            this.data = new ArrayList<>();
            for (ResultRow row : rows) {
                this.data.add(new Object[]{row.key, row.value});
            }
            fireTableDataChanged();
        }
        
        public void setData(List<Object[]> data, String[] columnNames) {
            this.columnNames = columnNames;
            this.data = data;
            fireTableStructureChanged();
            fireTableDataChanged();
        }
        
        @Override
        public int getRowCount() {
            return data.size();
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= data.size()) return null;
            Object[] row = data.get(rowIndex);
            if (columnIndex < 0 || columnIndex >= row.length) return null;
            return row[columnIndex];
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }
    }
}
