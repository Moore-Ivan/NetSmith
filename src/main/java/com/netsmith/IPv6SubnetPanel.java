package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class IPv6SubnetPanel extends BaseOperationPanel {
    private final ResultView resultView;

    public IPv6SubnetPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("IPv6 子网划分");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        HistoryComboBox txtMajorCidr = new HistoryComboBox("ipv6_cidr_subnet", 260);
        HistoryComboBox txtSubnetCount = new HistoryComboBox("ipv6_count_subnet", 80);
        HistoryComboBox txtNewPrefix = new HistoryComboBox("ipv6_prefix_subnet", 80);
        JButton btnByCount = new JButton("按数量划分");
        JButton btnByPrefix = new JButton("按前缀划分");
        JButton exampleBtn = new JButton("示例");
        JButton clearBtn = new JButton("清空");

        ErrorIndicator cidrError = new ErrorIndicator();
        ErrorIndicator countError = new ErrorIndicator();
        ErrorIndicator prefixError = new ErrorIndicator();

        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("主网络 CIDR:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 4;
        add(txtMajorCidr, gbc);
        gbc.gridx = 5; gbc.weightx = 0; gbc.gridwidth = 1;
        add(cidrError, gbc);

        gbc.gridy = 1; gbc.gridx = 0;
        add(new JLabel("按子网数量:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0; gbc.gridwidth = 1;
        add(txtSubnetCount, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        add(countError, gbc);
        gbc.gridx = 3; gbc.weightx = 0;
        add(btnByCount, gbc);

        gbc.gridy = 2; gbc.gridx = 0;
        add(new JLabel("按目标前缀:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0; gbc.gridwidth = 1;
        add(txtNewPrefix, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        add(prefixError, gbc);
        gbc.gridx = 3; gbc.weightx = 0;
        add(btnByPrefix, gbc);

        JLabel info = new JLabel("提示：IPv6 通常按 /48-/64 分配给站点，/64 分配给子网");
        info.setForeground(Color.GRAY);
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 6; gbc.weightx = 0;
        add(info, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(exampleBtn);
        buttonPanel.add(clearBtn);
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 6; gbc.weightx = 1.0;
        add(buttonPanel, gbc);

        exampleBtn.addActionListener(e -> {
            txtMajorCidr.setText("2001:db8::/32");
            txtMajorCidr.save();
            txtSubnetCount.setText("8");
            txtSubnetCount.save();
        });

        clearBtn.addActionListener(e -> {
            txtMajorCidr.setText("");
            txtSubnetCount.setText("");
            txtNewPrefix.setText("");
            clearErrors(cidrError, countError, prefixError);
        });

        btnByCount.addActionListener(e -> {
            txtMajorCidr.save();
            txtSubnetCount.save();
            clearErrors(cidrError, countError, prefixError);
            String cidr = txtMajorCidr.getText().trim();
            String countStr = txtSubnetCount.getText().trim();
            CalculationWorker.executeWithProgress(this, "正在划分 IPv6 子网...", btnByCount,
                new CalculationWorker.CallableWithProgress<List<SubnetTablePanel.SubnetRow>>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public List<SubnetTablePanel.SubnetRow> call() throws Exception {
                        if (handler != null) handler.onProgress(50, "计算中...");
                        return SubnetController.ipv6SubnetByCountSync(cidr, countStr);
                    }
                },
                new CalculationWorker.CalculationCallback<List<SubnetTablePanel.SubnetRow>>() {
                    @Override
                    public void onStart() {
                        resultView.showTable(java.util.Collections.emptyList());
                    }
                    @Override public void onProgress(int p, String m) {}
                    @Override
                    public void onComplete(List<SubnetTablePanel.SubnetRow> result) {
                        resultView.showTable(result);
                    }
                    @Override
                    public void onError(Exception ex) {
                        handleCountError(ex, cidrError, countError, txtMajorCidr, txtSubnetCount);
                    }
                }
            );
        });

        btnByPrefix.addActionListener(e -> {
            txtMajorCidr.save();
            txtNewPrefix.save();
            String cidr = txtMajorCidr.getText().trim();
            String prefixStr = txtNewPrefix.getText().trim();
            try {
                IPv6Validator.validateCidr(cidr);
                int newPrefix = IPv6Validator.validatePrefix(prefixStr, 0, 128);
                clearErrors(cidrError, prefixError, countError);

                java.math.BigInteger totalCount = SubnetController.getIPv6SubnetCount(cidr, newPrefix);
                // 复用 SubnetTablePanel 的默认页大小，避免硬编码与面板 pageSize 脱节
                final int pageSize = SubnetTablePanel.DEFAULT_PAGE_SIZE;

                // 统一强制使用分页模式加载，不再提供"一次性加载全部"路径，彻底避免 OOM 风险
                statusCallback.accept("正在计算，总计 " + totalCount + " 个子网，使用分页加载...");
                String finalCidr = cidr;
                int finalNewPrefix = newPrefix;
                long maxDisplayCount = Math.min(
                    (long) SubnetTablePanel.MAX_PAGES * pageSize,
                    Long.MAX_VALUE
                );
                long displayCount = totalCount.min(java.math.BigInteger.valueOf(maxDisplayCount)).longValue();
                resultView.showPagedTable(
                    displayCount,
                    (page, size) -> {
                        try {
                            return SubnetController.ipv6SubnetByPrefixPagedSync(finalCidr, finalNewPrefix, page, size);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                );
                // 状态回调由 SubnetTablePanel 在数据加载完成后统一处理，避免重复
            } catch (ValidationError ex) {
                handlePrefixError(ex, cidrError, prefixError, txtMajorCidr, txtNewPrefix);
            }
        });

        bindEnterKey((JTextField) txtMajorCidr.getEditor().getEditorComponent(), btnByCount);
        bindEnterKey((JTextField) txtSubnetCount.getEditor().getEditorComponent(), btnByCount);
        bindEnterKey((JTextField) txtNewPrefix.getEditor().getEditorComponent(), btnByPrefix);
    }

    private void handleCountError(Exception ex, ErrorIndicator cidrError, ErrorIndicator countError,
                                  JComponent cidrField, JComponent countField) {
        if (ex instanceof ValidationError) {
            ValidationError ve = (ValidationError) ex;
            if (ve.getField() == ValidationError.Field.SUBNET_COUNT) {
                countError.setError(countField, ve.getMessage());
            } else {
                cidrError.setError(cidrField, ve.getMessage());
            }
        } else {
            cidrError.setError(cidrField, ex.getMessage());
        }
        statusCallback.accept("错误: " + ex.getMessage());
    }

    private void handlePrefixError(Exception ex, ErrorIndicator cidrError, ErrorIndicator prefixError,
                                   JComponent cidrField, JComponent prefixField) {
        if (ex instanceof ValidationError) {
            ValidationError ve = (ValidationError) ex;
            if (ve.getField() == ValidationError.Field.PREFIX) {
                prefixError.setError(prefixField, ve.getMessage());
            } else {
                cidrError.setError(cidrField, ve.getMessage());
            }
        } else {
            cidrError.setError(cidrField, ex.getMessage());
        }
        statusCallback.accept("错误: " + ex.getMessage());
    }
}