package com.netsmith;

import com.netsmith.service.IPv4SubnetService;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class EqualSubnetPanel extends BaseOperationPanel {

    private final ResultView resultView;
    private HistoryComboBox txtMajorCidr;
    private HistoryComboBox txtSubnetCount;
    private HistoryComboBox txtHostPerSubnet;
    private JButton btnByCount;
    private JButton btnByHosts;
    private ErrorIndicator cidrError;
    private ErrorIndicator countError;
    private ErrorIndicator hostsError;

    public EqualSubnetPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("主网络 + 划分方式"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        txtMajorCidr = new HistoryComboBox("cidr_equal_subnet", 220);
        txtSubnetCount = new HistoryComboBox("count_equal_subnet", 80);
        txtHostPerSubnet = new HistoryComboBox("hosts_equal_subnet", 80);
        btnByCount = new JButton("按数量划分");
        btnByHosts = new JButton("按主机数划分");
        JButton exampleBtn = new JButton("示例");
        JButton clearBtn = new JButton("清空");

        cidrError = new ErrorIndicator();
        countError = new ErrorIndicator();
        hostsError = new ErrorIndicator();

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
        add(new JLabel("按每子网主机数:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0; gbc.gridwidth = 1;
        add(txtHostPerSubnet, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        add(hostsError, gbc);
        gbc.gridx = 3; gbc.weightx = 0;
        add(btnByHosts, gbc);

        JLabel info = new JLabel("提示：支持 /31（2主机）和 /32（1主机）的特殊子网（RFC 3021）");
        info.setForeground(Color.GRAY);
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 6; gbc.weightx = 0;
        add(info, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(exampleBtn);
        buttonPanel.add(clearBtn);
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 6; gbc.weightx = 1.0;
        add(buttonPanel, gbc);

        exampleBtn.addActionListener(e -> {
            txtMajorCidr.setText("192.168.1.0/24");
            txtMajorCidr.save();
            txtSubnetCount.setText("4");
            txtSubnetCount.save();
        });

        clearBtn.addActionListener(e -> {
            txtMajorCidr.setText("");
            txtSubnetCount.setText("");
            txtHostPerSubnet.setText("");
            clearErrors(cidrError, countError, hostsError);
        });

        btnByCount.addActionListener(e -> calculateByCount());
        btnByHosts.addActionListener(e -> calculateByHosts());

        bindEnterKey((JTextField) txtMajorCidr.getEditor().getEditorComponent(), btnByCount);
        bindEnterKey((JTextField) txtSubnetCount.getEditor().getEditorComponent(), btnByCount);
        bindEnterKey((JTextField) txtHostPerSubnet.getEditor().getEditorComponent(), btnByHosts);
    }

    private void calculateByCount() {
        txtMajorCidr.save();
        txtSubnetCount.save();
        clearErrors(cidrError, countError, hostsError);
        String cidr = txtMajorCidr.getText().trim();
        String countStr = txtSubnetCount.getText().trim();
        CalculationWorker.executeWithProgress(this, "正在按数量划分子网...", btnByCount,
            new CalculationWorker.CallableWithProgress<List<SubnetTablePanel.SubnetRow>>() {
                private CalculationWorker.ProgressHandler handler;
                @Override
                public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                @Override
                public List<SubnetTablePanel.SubnetRow> call() throws Exception {
                    if (handler != null) handler.onProgress(50, "计算中...");
                    return SubnetController.subnetByCountSync(cidr, countStr, 1024);
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
                    statusCallback.accept("计算完成");
                }
                @Override
                public void onError(Exception ex) {
                    handleValidationError(ex, cidrError, countError, txtMajorCidr, txtSubnetCount,
                        ValidationError.Field.SUBNET_COUNT);
                }
            }
        );
    }

    private void calculateByHosts() {
        txtMajorCidr.save();
        txtHostPerSubnet.save();
        clearErrors(cidrError, countError, hostsError);
        String cidr = txtMajorCidr.getText().trim();
        String hostStr = txtHostPerSubnet.getText().trim();
        CalculationWorker.executeWithProgress(this, "正在按主机数划分子网...", btnByHosts,
            new CalculationWorker.CallableWithProgress<IPv4SubnetService.SubnetByHostsResultEx>() {
                private CalculationWorker.ProgressHandler handler;
                @Override
                public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                @Override
                public IPv4SubnetService.SubnetByHostsResultEx call() throws Exception {
                    if (handler != null) handler.onProgress(50, "计算中...");
                    return SubnetController.subnetByHostsWithInfoSync(cidr, hostStr);
                }
            },
            new CalculationWorker.CalculationCallback<IPv4SubnetService.SubnetByHostsResultEx>() {
                @Override
                public void onStart() {
                    resultView.showTable(java.util.Collections.emptyList());
                }
                @Override public void onProgress(int p, String m) {}
                @Override
                public void onComplete(IPv4SubnetService.SubnetByHostsResultEx result) {
                    resultView.showTable(result.rows);
                    String statusMsg = result.truncated
                            ? String.format("计算完成（仅显示前 1024 个，共 %d 个子网，实际前缀 /%d）", result.totalCount, result.prefix)
                            : "计算完成";
                    statusCallback.accept(statusMsg);
                }
                @Override
                public void onError(Exception ex) {
                    handleValidationError(ex, cidrError, hostsError, txtMajorCidr, txtHostPerSubnet,
                        ValidationError.Field.HOST_COUNT);
                }
            }
        );
    }
}