package com.netsmith;

import com.netsmith.service.AclService;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * ACL 策略检查面板：输入 Cisco 风格扩展 ACL 规则 + 源/目的 IP + 端口，
 * 按首条匹配生效语义判定数据包是否放行，并输出逐条评估轨迹。
 */
public class AclCheckPanel extends BaseOperationPanel {
    private final ResultView resultView;

    /**
     * 输入区首选高度上限。规则区比其他面板高，若不封顶，外层 JTabbedPane（首选高度取
     * 所有子标签最大值）会把共享结果输出区挤压得过小；封顶后不足部分由规则区
     * （GridBag weighty=1）吸收，规则文本超出时在滚动区内滚动查看。
     */
    private static final int MAX_PREFERRED_HEIGHT = 210;

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return d.height > MAX_PREFERRED_HEIGHT ? new Dimension(d.width, MAX_PREFERRED_HEIGHT) : d;
    }

    public AclCheckPanel(ResultView resultView, Consumer<String> statusCallback) {
        super(statusCallback);
        this.resultView = resultView;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("ACL 策略检查 (Cisco 扩展 ACL, 仅 IPv4)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        JTextArea rulesArea = new JTextArea(6, 40);
        rulesArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        rulesArea.setLineWrap(false);
        JScrollPane rulesScroll = new JScrollPane(rulesArea);
        rulesScroll.setBorder(BorderFactory.createTitledBorder(
                "ACL 规则 (每行一条: permit|deny ip|tcp|udp|icmp <源> [端口] <目的> [端口])"));
        rulesScroll.setToolTipText("地址: any / host A.B.C.D / A.B.C.D / A.B.C.D/x / A.B.C.D 反掩码；"
                + "端口: eq|gt|lt n 或 range lo hi，n 可用服务名(http/ssh/dns 等)；"
                + "未命中任何规则时按隐式 deny ip any any 处理");
        ErrorIndicator rulesError = new ErrorIndicator();

        HistoryComboBox txtSrcIp = new HistoryComboBox("acl_src_ip", 160);
        HistoryComboBox txtDstIp = new HistoryComboBox("acl_dst_ip", 160);
        JComboBox<String> cmbProtocol = new JComboBox<>(new String[]{"tcp", "udp", "icmp", "ip"});
        HistoryComboBox txtSrcPort = new HistoryComboBox("acl_src_port", 80);
        HistoryComboBox txtDstPort = new HistoryComboBox("acl_dst_port", 80);

        ErrorIndicator srcError = new ErrorIndicator();
        ErrorIndicator dstError = new ErrorIndicator();
        ErrorIndicator srcPortError = new ErrorIndicator();
        ErrorIndicator dstPortError = new ErrorIndicator();

        // 规则区（占满整行）
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1.0;
        add(rulesScroll, gbc);
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 3; gbc.gridwidth = 1; gbc.weightx = 0;
        add(rulesError, gbc);

        // 源/目的 IP 行
        gbc.gridy = 1; gbc.gridx = 0; gbc.gridwidth = 1;
        add(new JLabel("源 IP:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        add(txtSrcIp, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JPanel ipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        ipRow.add(new JLabel("目的 IP:"));
        ipRow.add(txtDstIp);
        add(ipRow, gbc);
        gbc.gridx = 3;
        JPanel srcErrRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        srcErrRow.add(srcError);
        srcErrRow.add(dstError);
        add(srcErrRow, gbc);

        // 协议 + 端口行
        gbc.gridy = 2; gbc.gridx = 0;
        add(new JLabel("协议:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0;
        add(cmbProtocol, gbc);
        gbc.gridx = 2; gbc.weightx = 1.0;
        JPanel portRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        portRow.add(new JLabel("源端口:"));
        portRow.add(txtSrcPort);
        portRow.add(srcPortError);
        portRow.add(new JLabel("目的端口:"));
        portRow.add(txtDstPort);
        portRow.add(dstPortError);
        add(portRow, gbc);
        gbc.gridx = 3; gbc.weightx = 0;
        add(new JPanel(), gbc);

        // 按钮行
        JButton btnCheck = new JButton("检查");
        JButton exampleBtn = new JButton("示例");
        JButton clearBtn = new JButton("清空");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        buttonPanel.add(btnCheck);
        buttonPanel.add(exampleBtn);
        buttonPanel.add(clearBtn);
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 4; gbc.weightx = 1.0;
        add(buttonPanel, gbc);

        // icmp/ip 协议无端口概念：禁用端口输入避免误导
        Runnable updatePortEnabled = () -> {
            boolean portsEnabled = "tcp".equals(cmbProtocol.getSelectedItem())
                    || "udp".equals(cmbProtocol.getSelectedItem());
            txtSrcPort.setEnabled(portsEnabled);
            txtDstPort.setEnabled(portsEnabled);
        };
        cmbProtocol.addActionListener(e -> updatePortEnabled.run());
        updatePortEnabled.run();

        exampleBtn.addActionListener(e -> {
            rulesArea.setText(String.join("\n",
                    "permit tcp 192.168.1.0 0.0.0.255 any eq 80",
                    "permit tcp 192.168.1.0 0.0.0.255 any eq 443",
                    "permit icmp 192.168.1.0/24 any",
                    "deny   tcp any 10.0.0.0/8 range 20 21",
                    "deny   udp any 10.0.0.0 0.255.255.255 eq dns",
                    "permit ip any any"));
            txtSrcIp.setText("192.168.1.10");
            txtSrcIp.save();
            txtDstIp.setText("8.8.8.8");
            txtDstIp.save();
            cmbProtocol.setSelectedItem("tcp");
            txtSrcPort.setText("1025");
            txtDstPort.setText("80");
            txtDstPort.save();
        });

        clearBtn.addActionListener(e -> {
            rulesArea.setText("");
            txtSrcIp.setText("");
            txtDstIp.setText("");
            txtSrcPort.setText("");
            txtDstPort.setText("");
            clearErrors(rulesError, srcError, dstError, srcPortError, dstPortError);
        });

        btnCheck.addActionListener(e -> {
            txtSrcIp.save();
            txtDstIp.save();
            txtSrcPort.save();
            txtDstPort.save();
            String rules = rulesArea.getText();
            String srcIp = txtSrcIp.getText().trim();
            String dstIp = txtDstIp.getText().trim();
            String protocol = String.valueOf(cmbProtocol.getSelectedItem());
            String srcPort = txtSrcPort.getText().trim();
            String dstPort = txtDstPort.getText().trim();
            // 端口在面板侧先行校验，便于把错误定位到具体端口输入框
            if (!validatePortInline(srcPort, "源端口", srcPortError, txtSrcPort)
                    | !validatePortInline(dstPort, "目的端口", dstPortError, txtDstPort)) {
                return;
            }
            CalculationWorker.executeWithProgress(this, "正在检查 ACL 策略...", btnCheck,
                new CalculationWorker.CallableWithProgress<AclService.AclCheckResult>() {
                    @Override
                    public void setProgressHandler(CalculationWorker.ProgressHandler h) { }
                    @Override
                    public AclService.AclCheckResult call() {
                        return SubnetController.aclCheckSync(rules, srcIp, dstIp, protocol, srcPort, dstPort);
                    }
                },
                new CalculationWorker.CalculationCallback<AclService.AclCheckResult>() {
                    @Override public void onProgress(int p, String m) {}
                    @Override
                    public void onComplete(AclService.AclCheckResult result) {
                        resultView.showText(formatReport(result));
                        clearErrors(rulesError, srcError, dstError, srcPortError, dstPortError);
                    }
                    @Override
                    public void onError(Exception ex) {
                        // IP_START→源 IP、IP_END→目的 IP，其余（规则语法等）归属规则区
                        java.util.Map<ValidationError.Field, BaseOperationPanel.ErrorBinding> bindings =
                                new java.util.HashMap<>();
                        bindings.put(ValidationError.Field.IP_START,
                                new BaseOperationPanel.ErrorBinding(srcError, txtSrcIp));
                        bindings.put(ValidationError.Field.IP_END,
                                new BaseOperationPanel.ErrorBinding(dstError, txtDstIp));
                        handleValidationError(ex, rulesError, rulesScroll, bindings);
                    }
                }
            );
        });

        bindEnterKey((JTextField) txtDstIp.getEditor().getEditorComponent(), btnCheck);
    }

    /** 端口行内校验：空表示未指定；非空必须是 0-65535 数字或常见服务名 */
    private boolean validatePortInline(String port, String name, ErrorIndicator error, JComponent field) {
        if (port.isEmpty()) {
            error.clearError();
            return true;
        }
        if (port.matches("\\d{1,5}") && Integer.parseInt(port) >= 0 && Integer.parseInt(port) <= 65535) {
            error.clearError();
            return true;
        }
        if (WELL_KNOWN_PORT_NAMES.contains(port.toLowerCase())) {
            error.clearError();
            return true;
        }
        error.setError(field, name + "无效 (0-65535 或服务名)");
        statusCallback.accept("错误: " + name + "无效: " + port + " (应为 0-65535 或常见服务名)");
        return false;
    }

    /** 与 AclService 中 WELL_KNOWN_PORTS 对应的服务名白名单（用于面板侧行内校验提示） */
    private static final java.util.Set<String> WELL_KNOWN_PORT_NAMES = java.util.Set.of(
            "http", "www", "https", "ssh", "telnet", "smtp", "dns", "domain", "ftp",
            "ftp-data", "tftp", "pop3", "imap", "snmp", "bgp", "ntp", "syslog",
            "ldap", "rdp", "mysql");

    private String formatReport(AclService.AclCheckResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("═".repeat(55)).append("\n");
        sb.append("   ACL 策略检查结果\n");
        sb.append("═".repeat(55)).append("\n");
        sb.append("  数据包: ").append(r.packetSummary).append("\n");
        if (r.implicitDeny) {
            sb.append("  判定结果: 拒绝 (DENY) — 隐式规则\n");
            sb.append("  命中规则: 无 (Cisco 默认隐式 deny ip any any)\n");
        } else {
            sb.append("  判定结果: ").append(r.permitted ? "允许 (PERMIT)" : "拒绝 (DENY)").append("\n");
            sb.append("  命中规则: 第 ").append(r.matchedRuleNo)
                    .append(" 条  ").append(r.matchedRuleText).append("\n");
        }
        sb.append("─".repeat(55)).append("\n");
        sb.append("  规则评估轨迹 (首条匹配生效，共 ").append(r.ruleCount).append(" 条):\n");
        for (String line : r.trace) {
            sb.append(line).append("\n");
        }
        sb.append("═".repeat(55)).append("\n");
        return sb.toString();
    }
}
