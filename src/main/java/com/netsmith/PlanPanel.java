package com.netsmith;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class PlanPanel extends BaseOperationPanel {
    private final ResultView resultView;

    public PlanPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        Border titledBorder = BorderFactory.createTitledBorder("网络规划向导");
        setBorder(titledBorder);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 4, 6, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        HistoryComboBox txtCidr = new HistoryComboBox("cidr_plan", 220);
        HistoryComboBox txtDeptCount = new HistoryComboBox("dept_count", 80);
        HistoryComboBox txtHostsPerDept = new HistoryComboBox("hosts_per_dept", 80);
        JButton btnPlan = new JButton("生成规划");

        ErrorIndicator cidrError = new ErrorIndicator();
        ErrorIndicator deptError = new ErrorIndicator();
        ErrorIndicator hostsError = new ErrorIndicator();

        gbc.weightx = 0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        add(new JLabel("主网络 CIDR:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 5;
        gbc.weightx = 1.0;
        add(txtCidr, gbc);
        gbc.gridx = 6;
        gbc.weightx = 0;
        add(cidrError, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        add(new JLabel("部门数量:"), gbc);
        gbc.gridx = 1;
        add(txtDeptCount, gbc);
        gbc.gridx = 2;
        add(deptError, gbc);
        gbc.gridx = 3;
        add(new JLabel("每部门主机数:"), gbc);
        gbc.gridx = 4;
        add(txtHostsPerDept, gbc);
        gbc.gridx = 5;
        add(hostsError, gbc);

        JLabel info = new JLabel("说明：系统自动计算最优子网掩码，并给出地址利用率报告");
        info.setForeground(Color.GRAY);
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 7;
        add(info, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnPlan);
        JButton example = new JButton("示例");
        example.addActionListener(e -> {
            txtCidr.setText("192.168.0.0/16");
            txtCidr.save();
            txtDeptCount.setText("8");
            txtDeptCount.save();
            txtHostsPerDept.setText("200");
            txtHostsPerDept.save();
        });
        buttonPanel.add(example);
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> {
            txtCidr.setText("");
            txtDeptCount.setText("");
            txtHostsPerDept.setText("");
            cidrError.clearError();
            deptError.clearError();
            hostsError.clearError();
        });
        buttonPanel.add(clearBtn);
        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 7;
        add(buttonPanel, gbc);

        btnPlan.addActionListener(e -> {
            txtCidr.save();
            txtDeptCount.save();
            txtHostsPerDept.save();
            String cidr = txtCidr.getText().trim();
            String deptStr = txtDeptCount.getText().trim();
            String hostsStr = txtHostsPerDept.getText().trim();
            CalculationWorker.executeWithProgress(this, "正在生成网络规划...", btnPlan,
                new CalculationWorker.CallableWithProgress<List<SubnetTablePanel.SubnetRow>>() {
                    private CalculationWorker.ProgressHandler handler;
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                    @Override
                    public List<SubnetTablePanel.SubnetRow> call() throws Exception {
                        if (handler != null) handler.onProgress(50, "规划计算中...");
                        return SubnetController.planNetworkSync(cidr, deptStr, hostsStr);
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
                        cidrError.clearError();
                        deptError.clearError();
                        hostsError.clearError();
                    }
                    @Override
                    public void onError(Exception ex) {
                        handleValidationError(ex, cidrError, deptError, hostsError, txtCidr, txtDeptCount, txtHostsPerDept,
                            ValidationError.Field.DEPT_COUNT, ValidationError.Field.HOST_COUNT);
                    }
                }
            );
        });

        bindEnterKey((JTextField) txtCidr.getEditor().getEditorComponent(), btnPlan);
        bindEnterKey((JTextField) txtHostsPerDept.getEditor().getEditorComponent(), btnPlan);
    }
}
