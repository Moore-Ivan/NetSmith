package com.netsmith;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class IPv6VlsmPanel extends BaseOperationPanel {

    private final ResultView resultView;
    private HistoryComboBox txtCidr;
    private HistoryComboBox txtHosts;
    private JButton btnVlsm;
    private ErrorIndicator cidrError;
    private ErrorIndicator hostsError;

    public IPv6VlsmPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("IPv6 VLSM 变长子网划分"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        txtCidr = new HistoryComboBox("ipv6_cidr_vlsm", 200);
        txtHosts = new HistoryComboBox("ipv6_hosts_vlsm", 220);
        btnVlsm = new JButton("VLSM 划分");
        JButton exampleBtn = new JButton("示例");
        JButton clearBtn = new JButton("清空");

        cidrError = new ErrorIndicator();
        hostsError = new ErrorIndicator();

        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("主网络 CIDR:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 3;
        add(txtCidr, gbc);
        gbc.gridx = 4; gbc.weightx = 0; gbc.gridwidth = 1;
        add(cidrError, gbc);

        gbc.gridy = 1; gbc.gridx = 0;
        add(new JLabel("主机数列表 (逗号分隔):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 3;
        add(txtHosts, gbc);
        gbc.gridx = 4; gbc.weightx = 0; gbc.gridwidth = 1;
        add(hostsError, gbc);

        JLabel info = new JLabel("说明：支持大数字，如 2^64、0x1fffffffffffffff、18446744073709551616");
        info.setForeground(Color.GRAY);
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 5; gbc.weightx = 0;
        add(info, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnVlsm);
        buttonPanel.add(exampleBtn);
        buttonPanel.add(clearBtn);
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 5; gbc.weightx = 1.0;
        add(buttonPanel, gbc);

        exampleBtn.addActionListener(e -> {
            txtCidr.setText("2001:db8::/48");
            txtCidr.save();
            txtHosts.setText("2^64, 2^56, 2^48, 2^40");
            txtHosts.save();
        });

        clearBtn.addActionListener(e -> {
            txtCidr.setText("");
            txtHosts.setText("");
            clearErrors(cidrError, hostsError);
        });

        btnVlsm.addActionListener(e -> calculateVlsm());

        bindEnterKey((JTextField) txtCidr.getEditor().getEditorComponent(), btnVlsm);
        bindEnterKey((JTextField) txtHosts.getEditor().getEditorComponent(), btnVlsm);
    }

    private void calculateVlsm() {
        txtCidr.save();
        txtHosts.save();
        clearErrors(cidrError, hostsError);
        String cidr = txtCidr.getText().trim();
        String hostList = txtHosts.getText().trim();
        CalculationWorker.executeWithProgress(this, "正在执行 IPv6 VLSM 划分...", btnVlsm,
            new CalculationWorker.CallableWithProgress<List<SubnetTablePanel.SubnetRow>>() {
                private CalculationWorker.ProgressHandler handler;
                @Override
                public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                @Override
                public List<SubnetTablePanel.SubnetRow> call() throws Exception {
                    if (handler != null) handler.onProgress(50, "VLSM 计算中...");
                    return SubnetController.ipv6VlsmSync(cidr, hostList);
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
                    statusCallback.accept("计算完成，共 " + result.size() + " 个子网");
                }
                @Override
                public void onError(Exception ex) {
                    handleValidationError(ex, cidrError, hostsError, txtCidr, txtHosts,
                        ValidationError.Field.IPV6_CIDR, ValidationError.Field.CIDR, ValidationError.Field.HOST_LIST);
                }
            }
        );
    }}
