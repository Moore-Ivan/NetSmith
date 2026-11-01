package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class SupernetSplitPanel extends BaseOperationPanel {
    private final ResultView resultView;

    public SupernetSplitPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("超网拆分（路由汇总的逆操作）");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        HistoryComboBox txtCidr = new HistoryComboBox("cidr_supernet", 200);
        HistoryComboBox txtPrefix = new HistoryComboBox("prefix_supernet", 80);
        JButton btnSplit = new JButton("拆分");
        JButton exampleBtn = new JButton("示例");
        JButton clearBtn = new JButton("清空");

        ErrorIndicator cidrError = new ErrorIndicator();
        ErrorIndicator prefixError = new ErrorIndicator();

        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("超网 CIDR:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 3;
        add(txtCidr, gbc);
        gbc.gridx = 4; gbc.weightx = 0; gbc.gridwidth = 1;
        add(cidrError, gbc);

        gbc.gridy = 1; gbc.gridx = 0;
        add(new JLabel("目标前缀:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0; gbc.gridwidth = 1;
        add(txtPrefix, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        add(prefixError, gbc);
        gbc.gridx = 3; gbc.weightx = 0;
        add(btnSplit, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(exampleBtn);
        buttonPanel.add(clearBtn);
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 5; gbc.weightx = 1.0;
        add(buttonPanel, gbc);

        exampleBtn.addActionListener(e -> {
            txtCidr.setText("10.0.0.0/8");
            txtCidr.save();
            txtPrefix.setText("16");
            txtPrefix.save();
        });

        clearBtn.addActionListener(e -> {
            txtCidr.setText("");
            txtPrefix.setText("");
            clearErrors(cidrError, prefixError);
        });

        btnSplit.addActionListener(e -> {
            txtCidr.save();
            txtPrefix.save();
            try {
                String cidr = txtCidr.getText().trim();
                String prefixStr = txtPrefix.getText().trim();

                CidrValidator.validateCidr(cidr);
                int newPrefix = CidrValidator.validatePrefix(prefixStr, 0, 32);

                clearErrors(cidrError, prefixError);

                long totalCount = SubnetController.getSplitSupernetCount(cidr, newPrefix);
                // 复用 SubnetTablePanel 的默认页大小，避免硬编码与面板 pageSize 脱节
                final int pageSize = SubnetTablePanel.DEFAULT_PAGE_SIZE;

                if (totalCount <= pageSize * 2) {
                    CalculationWorker.executeWithProgress(this, "正在拆分超网...", btnSplit,
                        new CalculationWorker.CallableWithProgress<List<SubnetTablePanel.SubnetRow>>() {
                            private CalculationWorker.ProgressHandler handler;

                            @Override
                            public void setProgressHandler(CalculationWorker.ProgressHandler handler) {
                                this.handler = handler;
                            }

                            @Override
                            public List<SubnetTablePanel.SubnetRow> call() throws Exception {
                                return SubnetController.splitSupernetSync(cidr, newPrefix);
                            }
                        },
                        new CalculationWorker.CalculationCallback<List<SubnetTablePanel.SubnetRow>>() {
                            @Override
                            public void onStart() {
                                resultView.showTable(java.util.Collections.emptyList());
                            }

                            @Override
                            public void onProgress(int progress, String message) {}

                            @Override
                            public void onComplete(List<SubnetTablePanel.SubnetRow> result) {
                                resultView.showTable(result);
                                statusCallback.accept("拆分完成，共 " + result.size() + " 个子网");
                            }

                            @Override
                            public void onError(Exception ex) {
                                handleError(ex, cidrError, prefixError, txtCidr, txtPrefix);
                            }
                        }
                    );
                    return;
                }

                statusCallback.accept("正在计算，总计 " + totalCount + " 个子网，使用分页加载...");
                String finalCidr = cidr;
                int finalNewPrefix = newPrefix;
                resultView.showPagedTable(
                    Math.min(totalCount, (long) SubnetTablePanel.MAX_PAGES * pageSize),
                    (page, size) -> {
                        try {
                            return SubnetController.splitSupernetPagedSync(finalCidr, finalNewPrefix, page, size);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                );
                // 状态回调由 SubnetTablePanel 在数据加载完成后统一处理，避免重复
                // statusCallback.accept("拆分完成，共 " + totalCount + " 个子网（分页显示）");
            } catch (ValidationError ex) {
                handleError(ex, cidrError, prefixError, txtCidr, txtPrefix);
            }
        });

        bindEnterKey((JTextField) txtCidr.getEditor().getEditorComponent(), btnSplit);
        bindEnterKey((JTextField) txtPrefix.getEditor().getEditorComponent(), btnSplit);
    }

    private void handleError(Exception ex, ErrorIndicator cidrError, ErrorIndicator prefixError,
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