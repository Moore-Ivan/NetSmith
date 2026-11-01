package com.netsmith;

import com.netsmith.service.IPv4SubnetService;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * 随机 IP 地址池生成面板：输入 CIDR 与数量 N，随机生成 N 个不重复的可用 IPv4（测试用）。
 */
public class RandomIpPoolPanel extends BaseOperationPanel {
    private final ResultView resultView;

    public RandomIpPoolPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("随机 IP 地址池生成"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        HistoryComboBox txtCidr = new HistoryComboBox("random_pool_cidr", 200);
        HistoryComboBox txtCount = new HistoryComboBox("random_pool_count", 120);
        JButton btnGenerate = new JButton("生成");
        JButton exampleBtn = new JButton("示例");
        JButton clearBtn = new JButton("清空");

        ErrorIndicator cidrError = new ErrorIndicator();
        ErrorIndicator countError = new ErrorIndicator();

        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("CIDR 网段:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        add(txtCidr, gbc);
        gbc.gridx = 3; gbc.weightx = 0; gbc.gridwidth = 1;
        add(cidrError, gbc);

        gbc.gridy = 1; gbc.gridx = 0;
        add(new JLabel("生成数量:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2;
        add(txtCount, gbc);
        gbc.gridx = 3; gbc.weightx = 0; gbc.gridwidth = 1;
        add(countError, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnGenerate);
        buttonPanel.add(exampleBtn);
        buttonPanel.add(clearBtn);
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 4; gbc.weightx = 1.0;
        add(buttonPanel, gbc);

        JLabel hintLabel = new JLabel("从网段可用地址中随机抽取 N 个不重复 IP (上限 "
                + IPv4SubnetService.MAX_RANDOM_POOL_SIZE + "，/31 按 2 个、/32 按 1 个可用地址计)");
        hintLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 4;
        add(hintLabel, gbc);

        exampleBtn.addActionListener(e -> {
            txtCidr.setText("192.168.1.0/24");
            txtCidr.save();
            txtCount.setText("10");
            txtCount.save();
        });

        clearBtn.addActionListener(e -> {
            txtCidr.setText("");
            txtCount.setText("");
            clearErrors(cidrError, countError);
        });

        btnGenerate.addActionListener(e -> {
            txtCidr.save();
            txtCount.save();
            String cidr = txtCidr.getText().trim();
            String countStr = txtCount.getText().trim();
            CalculationWorker.executeWithProgress(this, "正在随机生成 IP...", btnGenerate,
                new CalculationWorker.CallableWithProgress<List<String>>() {
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { }
                    @Override
                    public List<String> call() {
                        return SubnetController.randomPoolIpsSync(cidr, countStr);
                    }
                },
                new CalculationWorker.CalculationCallback<List<String>>() {
                    @Override public void onProgress(int p, String m) {}
                    @Override
                    public void onComplete(List<String> result) {
                        resultView.showText(formatReport(cidr, result));
                        clearErrors(cidrError, countError);
                    }
                    @Override
                    public void onError(Exception ex) {
                        handleValidationError(ex, cidrError, countError, txtCidr, txtCount,
                                ValidationError.Field.HOST_COUNT);
                    }
                }
            );
        });

        bindEnterKey((JTextField) txtCount.getEditor().getEditorComponent(), btnGenerate);
    }

    private String formatReport(String cidr, List<String> ips) {
        StringBuilder sb = new StringBuilder();
        sb.append("═".repeat(55)).append("\n");
        sb.append("   随机 IP 地址池生成结果\n");
        sb.append("═".repeat(55)).append("\n");
        sb.append("  网段:     ").append(cidr).append("\n");
        sb.append("  生成数量: ").append(ips.size()).append(" (随机不重复选取)\n");
        sb.append("─".repeat(55)).append("\n");
        for (int i = 0; i < ips.size(); i++) {
            sb.append(String.format("  %5d  %s%n", i + 1, ips.get(i)));
        }
        sb.append("═".repeat(55)).append("\n");
        return sb.toString();
    }
}
