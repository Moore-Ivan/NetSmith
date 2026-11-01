package com.netsmith;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class VlsmPanel extends BaseOperationPanel {

    private final ResultView resultView;
    private HistoryComboBox txtCidr;
    private HistoryComboBox txtHosts;
    private JButton btnVlsm;
    private ErrorIndicator cidrError;
    private ErrorIndicator hostsError;

    public VlsmPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("VLSM 变长子网划分"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        txtCidr = new HistoryComboBox("cidr_vlsm", 200);
        txtHosts = new HistoryComboBox("hosts_vlsm", 220);
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

        JLabel info = new JLabel("说明：需求会按降序排序，优先分配大子网以减少地址碎片");
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
            txtCidr.setText("172.16.0.0/22");
            txtCidr.save();
            txtHosts.setText("200,50,10,2");
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
        CalculationWorker.executeWithProgress(this, "正在执行 VLSM 划分...", btnVlsm,
            new CalculationWorker.CallableWithProgress<List<SubnetTablePanel.SubnetRow>>() {
                private CalculationWorker.ProgressHandler handler;
                @Override
                public void setProgressHandler(CalculationWorker.ProgressHandler h) { this.handler = h; }
                @Override
                public List<SubnetTablePanel.SubnetRow> call() throws Exception {
                    if (handler != null) handler.onProgress(50, "VLSM 计算中...");
                    return SubnetController.vlsmSync(cidr, hostList);
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
                    handleValidationError(ex, cidrError, hostsError, txtCidr, txtHosts,
                        ValidationError.Field.HOST_LIST);
                }
            }
        );
    }
}