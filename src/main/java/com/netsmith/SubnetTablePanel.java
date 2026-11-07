package com.netsmith;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SubnetTablePanel extends JPanel {
    /** 分页数据提供者 - 服务端分页模式使用 */
    @FunctionalInterface
    public interface PagedDataProvider {
        List<SubnetRow> fetchPage(int page, int pageSize);
    }

    private SubnetTableModel model;
    private JTable table;
    private TableRowSorter<SubnetTableModel> sorter;
    private JPanel paginationPanel;
    private JLabel pageInfoLabel;
    private JProgressBar loadingBar;      // 分页加载进度指示（不确定模式动画）
    private JButton prevButton, nextButton, firstButton, lastButton;
    private int currentPage = 1;
    private int pageSize = DEFAULT_PAGE_SIZE;
    private long totalRows = 0;
    private List<SubnetRow> allData = new ArrayList<>();
    private boolean isPageLoading = false; // 分页数据加载中标志
    private int pageRequestId = 0;         // 分页请求序号，防止过期请求覆盖新数据

    // 服务端分页模式
    private boolean pagedMode = false;
    private PagedDataProvider pagedProvider;
    public static final int MAX_PAGES = 10000; // 最大页数限制，防止性能爆炸
    /** 默认每页条数：外部调用方（IPv6SubnetPanel 等）计算最大可显示数量时应复用此常量，避免硬编码与本面板 pageSize 脱节 */
    public static final int DEFAULT_PAGE_SIZE = 100;
    /**
     * 分页渲染阈值：超过此数量 setData() 自动委托 {@link #setPagedData} 切换"分页渲染"模式。
     * <p><b>分页渲染 ≠ 分页加载</b>：分页渲染只是把 JTable 一次性渲染/排序的开销切成按页加载，
     * 数据列表本身仍整份驻留堆内存（provider 按引用持有调用方传入的 data，不额外复制）；
     * 真正"按需计算、不驻留全量数据"的路径是调用方直接走 showPagedTable() 的服务端分页，
     * 由 provider 仅依赖输入参数逐页计算（如 splitSupernetPaged）。
     * 超过 {@link #MAX_MEMORY_DATA_SIZE} 时 setData() 直接抛异常，
     * 调用方必须改用 showPagedTable() 并提供可按需计算的 provider。
     */
    public static final int PAGED_MODE_THRESHOLD = 5000;
    /**
     * setData() 内存上限（行数）。SubnetRow 每行含 5 个字符串对象，约数百字节/行，
     * 2 万行约占用数 MB 堆——对 Swing 桌面应用再高既无必要也有 OOM 风险。
     * 超限时 setData() 抛 IllegalArgumentException，调用方应改走 showPagedTable()。
     */
    public static final int MAX_MEMORY_DATA_SIZE = 20000;
    
    // 列宽记忆
    private int[] columnWidths = {180, 80, 120, 100, 220};
    private JScrollPane scrollPane;
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private JPanel emptyState;
    private boolean isResizing = false; // 重入保护标志
    private JPopupMenu rightClickMenu;
    private java.util.function.Consumer<String> statusCallback;

    public SubnetTablePanel() {
        setLayout(new BorderLayout());
        // 加载保存的列宽
        columnWidths = ConfigStore.loadColumnWidths("subnetTable", columnWidths);
        initComponents();
        initRightClickMenu();
    }

    /** 设置状态栏回调 */
    public void setStatusCallback(java.util.function.Consumer<String> callback) {
        this.statusCallback = callback;
    }

    private void initComponents() {
        model = new SubnetTableModel();
        model.addTableModelListener(e -> updateEmptyState());
        table = new JTable(model);
        table.setAutoCreateRowSorter(false);
        
        // 自定义排序比较器
        sorter = new TableRowSorter<>(model);
        sorter.setComparator(0, (Comparator<String>) (a, b) -> {
            try {
                String ipA = a.split("/")[0];
                String ipB = b.split("/")[0];
                if (ipA.contains(":") || ipB.contains(":")) {
                    BigInteger aVal = IPv6Calculator.parseIPv6(ipA);
                    BigInteger bVal = IPv6Calculator.parseIPv6(ipB);
                    return aVal.compareTo(bVal);
                }
                long aLong = SubnetCalculator.ipToLong(ipA);
                long bLong = SubnetCalculator.ipToLong(ipB);
                return Long.compare(aLong, bLong);
            } catch (Exception e) {
                return a.compareTo(b);
            }
        });
        sorter.setComparator(1, (Comparator<Integer>) Integer::compare);
        sorter.setComparator(2, (Comparator<String>) (a, b) -> {
            try {
                BigInteger aVal = new BigInteger(a);
                BigInteger bVal = new BigInteger(b);
                return aVal.compareTo(bVal);
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });
        sorter.setComparator(3, (Comparator<String>) String::compareTo);
        sorter.setComparator(4, (Comparator<String>) (a, b) -> {
            try {
                if (a.contains(":") || b.contains(":")) {
                    BigInteger aVal = IPv6Calculator.parseIPv6(a);
                    BigInteger bVal = IPv6Calculator.parseIPv6(b);
                    return aVal.compareTo(bVal);
                }
                long aLong = SubnetCalculator.ipToLong(a);
                long bLong = SubnetCalculator.ipToLong(b);
                return Long.compare(aLong, bLong);
            } catch (Exception e) {
                return a.compareTo(b);
            }
        });
        table.setRowSorter(sorter);
        
        table.setFont(ThemeManager.monoFont());
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        centerRenderer.setFont(ThemeManager.monoFont());
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }
        
        // 双击复制单元格
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    int col = table.getSelectedColumn();
                    if (row != -1 && col != -1) {
                        int modelRow = table.convertRowIndexToModel(row);
                        Object value = model.getValueAt(modelRow, col);
                        if (value != null) {
                            copyToClipboard(value.toString());
                            notifyStatus("已复制单元格内容");
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
        
        installCopyShortcut();
        
        // 列宽记忆 - 加载保存的列宽
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        // 各列最小宽度：掩码固定 15 字符，过窄会把 "255.255.255.192" 截断成省略号
        int[] minWidths = {150, 64, 100, 140, 140};
        TableColumn[] columns = new TableColumn[5];
        for (int i = 0; i < 5; i++) {
            columns[i] = table.getColumnModel().getColumn(i);
            int width = Math.max(columnWidths[i], minWidths[i]);
            columnWidths[i] = width;
            columns[i].setMinWidth(minWidths[i]);
            columns[i].setPreferredWidth(width);
        }
        
        // 监听列宽变化，保存到记忆
        table.getColumnModel().addColumnModelListener(new javax.swing.event.TableColumnModelListener() {
            @Override
            public void columnAdded(javax.swing.event.TableColumnModelEvent e) {}
            @Override
            public void columnRemoved(javax.swing.event.TableColumnModelEvent e) {}
            @Override
            public void columnMoved(javax.swing.event.TableColumnModelEvent e) {}
            @Override
            public void columnMarginChanged(javax.swing.event.ChangeEvent e) {
                if (isResizing) return; // 重入保护
                for (int i = 0; i < 5; i++) {
                    columnWidths[i] = table.getColumnModel().getColumn(i).getWidth();
                }
                ConfigStore.saveColumnWidths("subnetTable", columnWidths);
                // 调整"末地址"列以填满容器
                resizeLastAddressColumn();
            }
            @Override
            public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) {}
        });
        
        // 监听面板大小变化，调整"末地址"列填满容器
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                resizeLastAddressColumn();
            }
        });
        
        scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        
        // 空状态与表格共用卡片，避免查询失败或尚未出结果时只显示一排空表头
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        emptyState = createEmptyState();
        cardPanel.add(emptyState, "empty");
        cardPanel.add(scrollPane, "table");
        add(cardPanel, BorderLayout.CENTER);
        
        // 卡片从空状态切换到表格时视口才获得真实宽度，需要在此刻重新分配末列宽度
        scrollPane.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                resizeLastAddressColumn();
            }
            
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                resizeLastAddressColumn();
            }
        });
        
        paginationPanel = createPaginationPanel();
        add(paginationPanel, BorderLayout.SOUTH);
        updatePaginationUI();
    }
    
    /** 空状态提示：说明下一步操作与可用的复制方式 */
    private JPanel createEmptyState() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);

        JLabel title = new JLabel("暂无子网数据");
        title.setFont(ThemeManager.boldFont().deriveFont(15f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint = ThemeManager.mutedLabel("输入主网络并选择划分方式后点击划分，结果会显示在这里");
        hint.setFont(ThemeManager.smallFont());
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel tip = ThemeManager.mutedLabel("双击单元格复制 · Ctrl+C 复制选中行");
        tip.setFont(ThemeManager.smallFont());
        tip.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(title);
        box.add(Box.createVerticalStrut(8));
        box.add(hint);
        box.add(Box.createVerticalStrut(4));
        box.add(tip);
        panel.add(box);
        return panel;
    }

    private void updateEmptyState() {
        if (cardPanel == null || cardLayout == null) return;
        boolean empty = model.getRowCount() == 0;
        cardLayout.show(cardPanel, empty ? "empty" : "table");
        if (!empty) {
            cardPanel.revalidate();
            cardPanel.repaint();
            SwingUtilities.invokeLater(this::resizeLastAddressColumn);
        }
    }

    /** 表格获得焦点时，Ctrl+C 复制选中行（无选中则复制当前页） */
    private void installCopyShortcut() {
        KeyStroke copyKey = KeyStroke.getKeyStroke(KeyEvent.VK_C,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        table.getInputMap(JComponent.WHEN_FOCUSED).put(copyKey, "copySelectedRows");
        table.getActionMap().put("copySelectedRows", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (table.getSelectedRowCount() == 0) {
                    copyCurrentPageAllColumns();
                } else {
                    copySelectedRows();
                }
            }
        });
    }
    
    /** 复制选中行（含表头），供右键菜单与快捷键共用 */
    private void copySelectedRows() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < model.getColumnCount(); col++) {
            sb.append(model.getColumnName(col));
            if (col < model.getColumnCount() - 1) {
                sb.append("\t");
            }
        }
        sb.append("\n");
        for (int viewRow : selectedRows) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            for (int col = 0; col < model.getColumnCount(); col++) {
                Object value = model.getValueAt(modelRow, col);
                sb.append(value != null ? value.toString() : "");
                if (col < model.getColumnCount() - 1) {
                    sb.append("\t");
                }
            }
            sb.append("\n");
        }
        copyToClipboard(sb.toString().trim());
        notifyStatus("已复制 " + selectedRows.length + " 行数据");
    }
    
    private void notifyStatus(String message) {
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
    }
    
    /**
     * 调整"末地址"列宽度以填满表格容器
     */
    private void resizeLastAddressColumn() {
        if (isResizing) return;
        if (table.getColumnCount() < 5) return;
        if (scrollPane == null) return;
        
        // 使用 scrollPane 的视口宽度，而不是 table.getWidth()
        int viewportWidth = scrollPane.getViewport().getWidth();
        if (viewportWidth <= 0) return;
        
        // 计算前4列的总宽度
        int otherWidths = 0;
        for (int i = 0; i < 4; i++) {
            otherWidths += table.getColumnModel().getColumn(i).getWidth();
        }
        
        // 设置"末地址"列宽度为视口宽度减去其他列宽度
        int lastAddressWidth = viewportWidth - otherWidths;
        if (lastAddressWidth < 60) lastAddressWidth = 60; // 最小宽度
        
        isResizing = true;
        try {
            table.getColumnModel().getColumn(4).setPreferredWidth(lastAddressWidth);
            table.getColumnModel().getColumn(4).setWidth(lastAddressWidth);
            // 同步首选宽度后必须重新布局表格，否则表格仍按旧宽度绘制
            table.revalidate();
            table.repaint();
        } finally {
            isResizing = false;
        }
    }
    
    private void initRightClickMenu() {
        rightClickMenu = new JPopupMenu("操作");

        // 复制选中的行
        JMenuItem copyRowItem = new JMenuItem("复制选中的行");
        copyRowItem.addActionListener(e -> copySelectedRows());
        rightClickMenu.add(copyRowItem);

        // 复制当前页数据
        JMenuItem copyCurrentPageItem = new JMenuItem("复制当前页");
        copyCurrentPageItem.setToolTipText(pagedMode ? "（分页模式下可用）" : "（全部数据）");
        copyCurrentPageItem.addActionListener(e -> {
            if (pagedMode) {
                copyCurrentPageAllColumns();
            } else {
                copyAllDataInternal(false);
            }
        });
        rightClickMenu.add(copyCurrentPageItem);

        // 复制 CIDR 列（分页模式下复制当前页）
        JMenuItem copyCidrItem = new JMenuItem(pagedMode ? "复制当前页 CIDR" : "复制 CIDR 列");
        copyCidrItem.addActionListener(e -> {
            if (pagedMode) {
                copyCurrentPageCidr();
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (SubnetRow row : allData) {
                sb.append(row.networkAddress).append("\n");
            }
            if (sb.length() > 0) {
                copyToClipboard(sb.toString().trim());
            }
        });
        rightClickMenu.add(copyCidrItem);

        // 复制所有数据（非分页模式）或分页模式下小数据量导出
        JMenuItem copyAllItem = new JMenuItem(pagedMode ? "复制所有数据" : "复制所有数据");
        copyAllItem.addActionListener(e -> {
            if (pagedMode) {
                if (totalRows <= 10000 && pagedProvider != null) {
                    // 在后台线程获取数据，避免阻塞 EDT
                    new javax.swing.SwingWorker<List<SubnetRow>, Void>() {
                        @Override
                        protected List<SubnetRow> doInBackground() throws Exception {
                            return fetchAllPagedData();
                        }
                        @Override
                        protected void done() {
                            try {
                                List<SubnetRow> allRows = get();
                                StringBuilder sb = new StringBuilder();
                                for (int col = 0; col < model.getColumnCount(); col++) {
                                    sb.append(model.getColumnName(col));
                                    if (col < model.getColumnCount() - 1) sb.append("\t");
                                }
                                sb.append("\n");
                                for (SubnetRow row : allRows) {
                                    sb.append(row.networkAddress).append("\t");
                                    sb.append(row.prefix).append("\t");
                                    sb.append(row.usableHosts).append("\t");
                                    sb.append(row.subnetMask).append("\t");
                                    sb.append(row.broadcastAddress).append("\n");
                                }
                                copyToClipboard(sb.toString().trim());
                                if (statusCallback != null) {
                                    statusCallback.accept("已复制全部 " + allRows.size() + " 条数据");
                                }
                            } catch (java.util.concurrent.CancellationException ignored) {
                                // 用户取消导出：静默返回，不弹"导出失败"
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(SubnetTablePanel.this,
                                    "导出失败: " + ex.getMessage(),
                                    "导出错误",
                                    JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }.execute();
                } else {
                    JOptionPane.showMessageDialog(SubnetTablePanel.this,
                        "分页模式说明：\n" +
                        "当前数据量较大（" + totalRows + "条），无法一次性导出全部。\n" +
                        "• 可使用「复制当前页」复制当前页数据\n" +
                        "• 逐页切换后依次复制\n" +
                        "• 若需全部数据，请调整输入条件减少结果数量（≤10,000）",
                        "分页模式提示",
                        JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                copyAllDataInternal(true);
            }
        });
        rightClickMenu.add(copyAllItem);

        rightClickMenu.addSeparator();

        // 导出为 CSV（分页模式下小数据量导出）
        JMenuItem exportCsvItem = new JMenuItem(pagedMode ? "导出为 CSV" : "导出为 CSV");
        exportCsvItem.addActionListener(e -> {
            if (pagedMode) {
                if (totalRows <= 10000 && pagedProvider != null) {
                    // 在后台线程获取数据，避免阻塞 EDT
                    new javax.swing.SwingWorker<List<SubnetRow>, Void>() {
                        @Override
                        protected List<SubnetRow> doInBackground() throws Exception {
                            return fetchAllPagedData();
                        }
                        @Override
                        protected void done() {
                            try {
                                List<SubnetRow> allRows = get();
                                StringBuilder sb = new StringBuilder();
                                for (int col = 0; col < model.getColumnCount(); col++) {
                                    sb.append("\"").append(model.getColumnName(col)).append("\"");
                                    if (col < model.getColumnCount() - 1) sb.append(",");
                                }
                                sb.append("\n");
                                for (SubnetRow row : allRows) {
                                    sb.append("\"").append(row.networkAddress).append("\",");
                                    sb.append("\"").append(row.prefix).append("\",");
                                    sb.append("\"").append(row.usableHosts).append("\",");
                                    sb.append("\"").append(row.subnetMask).append("\",");
                                    sb.append("\"").append(row.broadcastAddress).append("\"\n");
                                }
                                copyToClipboard(sb.toString().trim());
                                if (statusCallback != null) {
                                    statusCallback.accept("已导出全部 " + allRows.size() + " 条数据为 CSV 格式");
                                }
                            } catch (java.util.concurrent.CancellationException ignored) {
                                // 用户取消导出：静默返回，不弹"导出失败"
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(SubnetTablePanel.this,
                                    "导出失败: " + ex.getMessage(),
                                    "导出错误",
                                    JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }.execute();
                } else {
                    String info = "分页模式说明：\n" +
                        "当前数据量较大（" + totalRows + "条），无法一次性导出全部。\n" +
                        "• 切换页码后可逐页复制表格内容\n" +
                        "• 使用「复制当前页」可复制当前页完整数据\n" +
                        "• 若需全部数据导出，请调整输入条件减少结果数量（≤10,000）";
                    copyToClipboard(info);
                    if (statusCallback != null) {
                        statusCallback.accept("已复制说明信息到剪贴板");
                    }
                }
            } else {
                exportCsvInternal();
            }
        });
        rightClickMenu.add(exportCsvItem);
    }

    /**
     * 从分页提供者获取全部数据（用于小数据量导出）
     */
    private List<SubnetRow> fetchAllPagedData() throws Exception {
        List<SubnetRow> allRows = new ArrayList<>();
        int current = 1;
        while (true) {
            List<SubnetRow> page = pagedProvider.fetchPage(current, pageSize);
            if (page.isEmpty()) break;
            allRows.addAll(page);
            if (page.size() < pageSize) break;
            current++;
        }
        return allRows;
    }

    /**
     * 复制当前页全部列数据
     */
    private void copyCurrentPageAllColumns() {
        if (model.getRowCount() == 0) {
            if (statusCallback != null) {
                statusCallback.accept("当前页无数据可复制");
            }
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        // 表头
        for (int col = 0; col < model.getColumnCount(); col++) {
            sb.append(model.getColumnName(col));
            if (col < model.getColumnCount() - 1) sb.append("\t");
        }
        sb.append("\n");
        // 当前页数据
        for (int row = 0; row < model.getRowCount(); row++) {
            for (int col = 0; col < model.getColumnCount(); col++) {
                Object val = model.getValueAt(row, col);
                sb.append(val != null ? val.toString() : "");
                if (col < model.getColumnCount() - 1) sb.append("\t");
            }
            sb.append("\n");
        }
        copyToClipboard(sb.toString().trim());
        if (statusCallback != null) {
            statusCallback.accept("已复制当前页 " + model.getRowCount() + " 条数据");
        }
    }

    private void copyAllDataInternal(boolean showInClipboard) {
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < model.getColumnCount(); col++) {
            sb.append(model.getColumnName(col));
            if (col < model.getColumnCount() - 1) sb.append("\t");
        }
        sb.append("\n");
        for (SubnetRow row : allData) {
            sb.append(row.networkAddress).append("\t");
            sb.append(row.prefix).append("\t");
            sb.append(row.usableHosts).append("\t");
            sb.append(row.subnetMask).append("\t");
            sb.append(row.broadcastAddress).append("\n");
        }
        String data = sb.toString().trim();
        if (data.isEmpty()) {
            if (statusCallback != null) {
                statusCallback.accept("表格为空，无内容可复制");
            }
            return;
        }
        copyToClipboard(data);
        if (statusCallback != null) {
            statusCallback.accept("已复制全部 " + allData.size() + " 条数据");
        }
    }

    private void exportCsvInternal() {
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < model.getColumnCount(); col++) {
            sb.append("\"").append(model.getColumnName(col)).append("\"");
            if (col < model.getColumnCount() - 1) sb.append(",");
        }
        sb.append("\n");
        for (SubnetRow row : allData) {
            sb.append("\"").append(row.networkAddress).append("\",");
            sb.append("\"").append(row.prefix).append("\",");
            sb.append("\"").append(row.usableHosts).append("\",");
            sb.append("\"").append(row.subnetMask).append("\",");
            sb.append("\"").append(row.broadcastAddress).append("\"\n");
        }
        copyToClipboard(sb.toString().trim());
        if (statusCallback != null) {
            statusCallback.accept("已导出 " + allData.size() + " 条数据为 CSV 格式");
        }
    }
    
    private void copyCurrentPageCidr() {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < model.getRowCount(); row++) {
            Object val = model.getValueAt(row, 0);
            if (val != null) {
                sb.append(val.toString()).append("\n");
            }
        }
        if (sb.length() > 0) {
            copyToClipboard(sb.toString().trim());
        }
    }
    
    private void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        if (statusCallback != null) {
            statusCallback.accept("已复制到剪贴板");
        }
    }
    
    private JPanel createPaginationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new javax.swing.border.EmptyBorder(4, 2, 0, 2));
        
        firstButton = ThemeManager.toolButton("首页", "跳转到第一页");
        prevButton = ThemeManager.toolButton("上一页", "跳转到上一页");
        nextButton = ThemeManager.toolButton("下一页", "跳转到下一页");
        lastButton = ThemeManager.toolButton("末页", "跳转到最后一页");
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        buttonPanel.add(firstButton);
        buttonPanel.add(prevButton);
        buttonPanel.add(nextButton);
        buttonPanel.add(lastButton);
        
        pageInfoLabel = ThemeManager.mutedLabel("");
        pageInfoLabel.setFont(ThemeManager.smallFont());

        // 分页加载进度指示（不确定模式动画），仅分页加载时可见
        loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);
        loadingBar.setPreferredSize(new Dimension(84, 10));
        loadingBar.setBorderPainted(false);
        loadingBar.setVisible(false);
        loadingBar.setToolTipText("正在加载分页数据...");

        JPanel westPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        westPanel.add(pageInfoLabel);
        westPanel.add(loadingBar);

        panel.add(westPanel, BorderLayout.WEST);
        panel.add(buttonPanel, BorderLayout.EAST);
        
        firstButton.addActionListener(e -> goToPage(1));
        prevButton.addActionListener(e -> goToPage(currentPage - 1));
        nextButton.addActionListener(e -> goToPage(currentPage + 1));
        lastButton.addActionListener(e -> goToPage((int) getTotalPages()));
        
        return panel;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
        updatePaginationUI();
    }
    
    public void setData(List<SubnetRow> data) {
        if (data.size() > MAX_MEMORY_DATA_SIZE) {
            throw new IllegalArgumentException(
                "数据量过大（" + data.size() + "条），超过内存上限 " + MAX_MEMORY_DATA_SIZE
                + "。请减少输入数据量，或改用支持分页加载的功能。");
        }

        pageRequestId++; // 使在途分页请求失效
        isPageLoading = false;
        loadingBar.setVisible(false);

        int dataSize = data.size();
        if (dataSize > PAGED_MODE_THRESHOLD) {
            // 超过渲染阈值：统一委托 setPagedData（分页渲染）。
            // provider 按引用持有调用方传入的 data，不再 new ArrayList 整份复制，
            // 避免与调用方列表共存时的引用数组翻倍（行对象本身始终共享）。
            setPagedData(dataSize, (page, size) -> {
                int start = (page - 1) * size;
                int end = Math.min(start + size, dataSize);
                return new ArrayList<>(data.subList(start, end));
            });
            return;
        }

        totalRows = dataSize;
        currentPage = 1;
        pagedMode = false;
        pagedProvider = null;
        allData = new ArrayList<>(data);
        if (table.getRowSorter() == null) {
            table.setRowSorter(sorter);
        }
        updateTableData();
        updatePaginationUI();
    }

    /**
     * 设置服务端分页数据，避免一次性加载海量数据
     * @param totalCount 总记录数
     * @param provider 分页数据提供者
     */
    public void setPagedData(long totalCount, PagedDataProvider provider) {
        pageRequestId++; // 使在途分页请求失效
        isPageLoading = false;
        loadingBar.setVisible(false);
        this.pagedMode = true;
        this.pagedProvider = provider;
        this.allData = null;
        this.totalRows = Math.min(totalCount, (long) MAX_PAGES * pageSize);
        this.currentPage = 1;
        // 分页模式下禁用排序（排序仅作用于当前页，切换页后排序重置，易造成混淆）
        table.setRowSorter(null);
        updateTableData();
        updatePaginationUI();
        
        // 数据加载完成后更新状态
        if (statusCallback != null) {
            statusCallback.accept("划分完成，共 " + totalRows + " 个子网（分页显示）");
        }
    }

    private void updateTableData() {
        if (pagedMode && pagedProvider != null) {
            // 分页模式下在后台线程加载数据，避免阻塞 EDT 并显示进度指示
            loadPageAsync(currentPage);
        } else {
            // 客户端分页：从 allData 切片
            int start = (currentPage - 1) * pageSize;
            int end = Math.min(start + pageSize, (int) Math.min(totalRows, Integer.MAX_VALUE));
            List<SubnetRow> pageData = new ArrayList<>();
            for (int i = start; i < end; i++) {
                pageData.add(allData.get(i));
            }
            model.setData(pageData);
        }
    }

    /**
     * 后台线程加载分页数据，加载期间显示进度动画并禁用翻页按钮
     * 使用请求序号校验，避免快速翻页时过期请求覆盖新数据
     */
    private void loadPageAsync(int page) {
        final int requestId = ++pageRequestId;
        isPageLoading = true;
        loadingBar.setVisible(true);
        updatePaginationUI();

        new SwingWorker<List<SubnetRow>, Void>() {
            @Override
            protected List<SubnetRow> doInBackground() throws Exception {
                return pagedProvider.fetchPage(page, pageSize);
            }

            @Override
            protected void done() {
                if (requestId != pageRequestId) {
                    return; // 已发起更新的请求，忽略过期结果
                }
                isPageLoading = false;
                loadingBar.setVisible(false);
                try {
                    List<SubnetRow> pageData = get();
                    model.setData(pageData);
                } catch (Exception e) {
                    // 解包 RuntimeException，保留原始异常类型
                    Throwable actualException = e;
                    if (e instanceof RuntimeException && e.getCause() != null) {
                        actualException = e.getCause();
                    }
                    final String errorMessage = "加载分页数据失败: " + actualException.getMessage();
                    if (statusCallback != null) {
                        statusCallback.accept(errorMessage);
                    }
                    JOptionPane.showMessageDialog(SubnetTablePanel.this,
                        errorMessage,
                        "加载错误",
                        JOptionPane.ERROR_MESSAGE);
                    model.setData(new ArrayList<>());
                }
                updatePaginationUI();
            }
        }.execute();
    }
    
    public void clearData() {
        pageRequestId++; // 使在途分页请求失效
        isPageLoading = false;
        loadingBar.setVisible(false);
        pagedMode = false;
        pagedProvider = null;
        if (allData != null) {
            allData.clear();
        } else {
            allData = new ArrayList<>();
        }
        totalRows = 0;
        model.setData(new ArrayList<>());
        updatePaginationUI();
    }
    
    private long getTotalPages() {
        if (totalRows == 0) return 1;
        long pages = (totalRows + pageSize - 1) / pageSize;
        return Math.min(pages, MAX_PAGES);
    }
    
    private void goToPage(int page) {
        long total = getTotalPages();
        if (page < 1) page = 1;
        if (page > total) page = (int) total;

        currentPage = page;
        updateTableData();
        updatePaginationUI();
    }
    
    private void updatePaginationUI() {
        long totalPages = getTotalPages();
        String countDisplay;
        if (pagedMode) {
            countDisplay = totalRows >= (long) MAX_PAGES * pageSize
                    ? "≥" + String.format("%,d", (long) MAX_PAGES * pageSize)
                    : String.format("%,d", totalRows);
        } else {
            countDisplay = String.format("%,d", totalRows);
        }
        String pageInfo = String.format("  第 %d / %d 页，共 %s 条记录  ",
            currentPage, totalPages, countDisplay);
        pageInfoLabel.setText(pageInfo);

        // 分页模式下添加排序提示
        if (pagedMode) {
            String truncatedNote = totalRows >= (long) MAX_PAGES * pageSize
                    ? "（总数已截断显示，实际数量可能更多）"
                    : "";
            pageInfoLabel.setToolTipText(
                "分页模式说明：" + truncatedNote + "\n" +
                "• 已禁用排序，避免切换页码后数据混乱\n" +
                "• 使用右键菜单「复制当前页」可复制当前页数据\n" +
                "• 切换页码可查看其他数据\n" +
                "• 若需全部数据，请调整输入条件减少结果数量"
            );
        } else {
            pageInfoLabel.setToolTipText(null);
        }

        firstButton.setEnabled(!isPageLoading && currentPage > 1);
        prevButton.setEnabled(!isPageLoading && currentPage > 1);
        nextButton.setEnabled(!isPageLoading && currentPage < totalPages);
        lastButton.setEnabled(!isPageLoading && currentPage < totalPages);

        paginationPanel.setVisible(totalRows > pageSize);
    }
    
    /** 获取表格数据（含表头）的制表符分隔文本，用于复制 */
    public String getAllDataAsText() {
        // 无数据时返回空串：否则会返回纯表头，让调用方误判"有内容可复制"
        int rowCount = pagedMode ? model.getRowCount() : allData.size();
        if (rowCount == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        // 表头
        for (int col = 0; col < model.getColumnCount(); col++) {
            sb.append(model.getColumnName(col));
            if (col < model.getColumnCount() - 1) {
                sb.append("\t");
            }
        }
        sb.append("\n");
        if (pagedMode) {
            // 分页模式下仅输出当前页数据
            for (int row = 0; row < model.getRowCount(); row++) {
                for (int col = 0; col < model.getColumnCount(); col++) {
                    Object val = model.getValueAt(row, col);
                    sb.append(val != null ? val.toString() : "");
                    if (col < model.getColumnCount() - 1) sb.append("\t");
                }
                sb.append("\n");
            }
        } else {
            // 数据行（使用 allData 而非当前页数据）
            for (SubnetRow row : allData) {
                sb.append(row.networkAddress).append("\t");
                sb.append(row.prefix).append("\t");
                sb.append(row.usableHosts).append("\t");
                sb.append(row.subnetMask).append("\t");
                sb.append(row.broadcastAddress).append("\n");
            }
        }
        return sb.toString().trim();
    }
    
    public static class SubnetRow {
        public final String networkAddress;
        public final int prefix;
        public final String usableHosts;
        public final String subnetMask;
        public final String broadcastAddress;

        public SubnetRow(String networkAddress, int prefix, String usableHosts, String subnetMask, String broadcastAddress) {
            this.networkAddress = networkAddress;
            this.prefix = prefix;
            this.usableHosts = usableHosts;
            this.subnetMask = subnetMask;
            this.broadcastAddress = broadcastAddress;
        }
    }
    
    public static class SubnetTableModel extends AbstractTableModel {
        private List<SubnetRow> data = new ArrayList<>();
        private final String[] columnNames = {"网络地址", "前缀", "可用主机数", "掩码", "末地址"};
        
        public void setData(List<SubnetRow> data) {
            this.data = data;
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
            SubnetRow row = data.get(rowIndex);
            switch (columnIndex) {
                case 0: return row.networkAddress;
                case 1: return row.prefix;
                case 2: return row.usableHosts;
                case 3: return row.subnetMask;
                case 4: return row.broadcastAddress;
                default: return null;
            }
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 1) return Integer.class;
            return String.class;
        }
        
        public List<SubnetRow> getData() {
            return data;
        }
    }
}
