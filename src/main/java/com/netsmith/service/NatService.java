package com.netsmith.service;

import com.netsmith.CidrValidator;
import com.netsmith.SubnetCalculator;
import com.netsmith.ValidationError;

/**
 * NAT 配置辅助服务（仅 IPv4）：根据内网 CIDR 与公网出口（单 IP 或 CIDR 地址池）
 * 生成 NAT 转换表建议与参考配置片段。
 * <p>方案决策：
 * <ul>
 *   <li>公网地址池可用 IP ≥ 内网可用主机数 → 静态 NAT 一对一映射</li>
 *   <li>否则 → NAPT/PAT：单公网 IP 时全体主机复用该地址；
 *       地址池时把内网主机均分到各公网 IP，并给出端口块均分建议</li>
 * </ul>
 */
public class NatService {

    /** 静态映射表最多展示条数 */
    private static final int STATIC_TABLE_CAP = 20;
    /** 端口块示例最多展示条数 */
    private static final int PORT_BLOCK_SAMPLE_CAP = 10;
    /** 公网 IP 分组建议最多展示组数 */
    private static final int POOL_GROUP_SAMPLE_CAP = 5;
    /** 配置片段最多展示行数 */
    private static final int CONFIG_SAMPLE_CAP = 5;
    /** PAT 可用端口下界（避开 0-1023 知名端口） */
    private static final long PORT_BASE = 1024;
    private static final long PORT_TOP = 65535;
    private static final long PORT_TOTAL = PORT_TOP - PORT_BASE + 1;

    /**
     * 生成 NAT 配置建议报告（纯文本）。
     *
     * @param internalCidr 内网网段，如 192.168.1.0/24
     * @param publicEgress 公网出口：单 IP（203.0.113.5）或 CIDR 地址池（203.0.113.0/29）
     */
    public String buildNatReport(String internalCidr, String publicEgress) {
        // ---- 内网侧 ----
        String[] inner = CidrValidator.validateCidr(internalCidr);
        int intPrefix = Integer.parseInt(inner[1]);
        long intNet = SubnetCalculator.getNetworkAddress(SubnetCalculator.ipToLong(inner[0]), intPrefix);
        long intLo, intHi; // 内网可用主机区间（闭区间），口径与项目约定一致：/31→2、/32→1
        if (intPrefix >= 32) {
            intLo = intHi = intNet;
        } else if (intPrefix == 31) {
            intLo = intNet;
            intHi = intNet + 1;
        } else {
            intLo = intNet + 1;
            intHi = SubnetCalculator.getBroadcastAddress(intNet, intPrefix) - 1;
        }
        long intUsable = intHi - intLo + 1;

        // ---- 公网侧（错误统一归属 IP_START，面板绑定到公网出口输入框） ----
        String egress = publicEgress == null ? "" : publicEgress.trim();
        if (egress.isEmpty()) {
            throw new ValidationError(ValidationError.Field.IP_START, "请输入公网出口 IP 或 CIDR");
        }
        boolean pubIsPool = egress.contains("/");
        long pubLo, pubHi, pubUsable;
        int pubPrefix;
        if (pubIsPool) {
            String[] p;
            try {
                p = CidrValidator.validateCidr(egress);
            } catch (ValidationError e) {
                throw new ValidationError(ValidationError.Field.IP_START, "公网出口 " + e.getMessage());
            }
            pubPrefix = Integer.parseInt(p[1]);
            long net = SubnetCalculator.getNetworkAddress(SubnetCalculator.ipToLong(p[0]), pubPrefix);
            if (pubPrefix >= 32) {
                pubLo = pubHi = net;
            } else if (pubPrefix == 31) {
                pubLo = net;
                pubHi = net + 1;
            } else {
                pubLo = net + 1;
                pubHi = SubnetCalculator.getBroadcastAddress(net, pubPrefix) - 1;
            }
            pubUsable = pubHi - pubLo + 1;
        } else {
            try {
                CidrValidator.validateIp(egress);
            } catch (ValidationError e) {
                throw new ValidationError(ValidationError.Field.IP_START, "公网出口 " + e.getMessage());
            }
            pubLo = pubHi = SubnetCalculator.ipToLong(egress);
            pubUsable = 1;
            pubPrefix = 32;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("═".repeat(55)).append("\n");
        sb.append("   NAT 配置建议报告\n");
        sb.append("═".repeat(55)).append("\n");
        sb.append("  内网网段:  ").append(SubnetCalculator.longToIp(intNet)).append("/").append(intPrefix).append("\n");
        sb.append("            可用主机 ").append(String.format("%,d", intUsable))
                .append(" 台 (").append(SubnetCalculator.longToIp(intLo))
                .append(" ~ ").append(SubnetCalculator.longToIp(intHi)).append(")\n");
        if (pubIsPool) {
            sb.append("  公网出口:  ").append(egress).append("\n");
            sb.append("            可用公网 IP ").append(String.format("%,d", pubUsable))
                    .append(" 个 (").append(SubnetCalculator.longToIp(pubLo))
                    .append(" ~ ").append(SubnetCalculator.longToIp(pubHi)).append(")\n");
        } else {
            sb.append("  公网出口:  ").append(egress).append(" (单个公网 IP)\n");
        }
        sb.append("─".repeat(55)).append("\n");

        if (pubIsPool && pubUsable >= intUsable) {
            buildStaticReport(sb, intLo, intUsable, pubLo);
        } else {
            buildPatReport(sb, intLo, intHi, intUsable, intNet, intPrefix,
                    pubLo, pubHi, pubUsable, pubPrefix, pubIsPool);
        }
        sb.append("═".repeat(55)).append("\n");
        return sb.toString();
    }

    /** 静态 NAT 一对一映射方案 */
    private void buildStaticReport(StringBuilder sb, long intLo, long intUsable, long pubLo) {
        sb.append("  推荐方案:  静态 NAT 一对一映射 (公网地址充足)\n");
        sb.append("─".repeat(55)).append("\n");
        sb.append("  静态映射表 (共 ").append(String.format("%,d", intUsable)).append(" 条");
        if (intUsable > STATIC_TABLE_CAP) {
            sb.append("，以下显示前 ").append(STATIC_TABLE_CAP).append(" 条");
        }
        sb.append("):\n");
        long shown = Math.min(intUsable, STATIC_TABLE_CAP);
        for (long i = 0; i < shown; i++) {
            sb.append(String.format("    %-15s  ↔  %s%n",
                    SubnetCalculator.longToIp(intLo + i), SubnetCalculator.longToIp(pubLo + i)));
        }
        sb.append("─".repeat(55)).append("\n");
        sb.append("  参考配置片段 (Cisco 风格，接口名按实际修改):\n");
        long cfgShown = Math.min(intUsable, CONFIG_SAMPLE_CAP);
        for (long i = 0; i < cfgShown; i++) {
            sb.append("    ip nat inside source static ")
                    .append(SubnetCalculator.longToIp(intLo + i)).append(" ")
                    .append(SubnetCalculator.longToIp(pubLo + i)).append("\n");
        }
        if (intUsable > CONFIG_SAMPLE_CAP) {
            sb.append("    ... (其余 ").append(String.format("%,d", intUsable - CONFIG_SAMPLE_CAP))
                    .append(" 条按相同格式追加)\n");
        }
    }

    /** NAPT/PAT 方案：单 IP 复用 或 地址池均分 + 端口块建议 */
    private void buildPatReport(StringBuilder sb, long intLo, long intHi, long intUsable,
                                long intNet, int intPrefix,
                                long pubLo, long pubHi, long pubUsable, int pubPrefix,
                                boolean pubIsPool) {
        sb.append("  推荐方案:  NAPT / PAT 端口复用 (公网地址不足，无法一对一映射)\n");
        sb.append("─".repeat(55)).append("\n");

        long hostsPerIp = pubIsPool ? ceilDiv(intUsable, pubUsable) : intUsable;

        // 地址池均分建议
        if (pubIsPool && pubUsable > 1) {
            sb.append("  公网 IP 均分建议:\n");
            sb.append("    每个公网 IP 承载约 ").append(String.format("%,d", hostsPerIp)).append(" 台内网主机\n");
            long groups = Math.min(pubUsable, POOL_GROUP_SAMPLE_CAP);
            for (long g = 0; g < groups; g++) {
                long gLo = intLo + g * hostsPerIp;
                if (gLo > intHi) {
                    break;
                }
                long gHi = Math.min(gLo + hostsPerIp - 1, intHi);
                sb.append("    ").append(SubnetCalculator.longToIp(pubLo + g))
                        .append("  ←  ").append(SubnetCalculator.longToIp(gLo))
                        .append(" ~ ").append(SubnetCalculator.longToIp(gHi)).append("\n");
            }
            if (pubUsable > POOL_GROUP_SAMPLE_CAP) {
                sb.append("    ... (其余 ").append(String.format("%,d", pubUsable - POOL_GROUP_SAMPLE_CAP))
                        .append(" 个公网 IP 依次顺延均分)\n");
            }
        }

        // 端口块均分建议
        sb.append("  端口分配建议:\n");
        sb.append("    可用端口范围: 1024 ~ 65535 (每个公网 IP 共 ").append(String.format("%,d", PORT_TOTAL)).append(" 个)\n");
        long perHostPorts = hostsPerIp > 0 ? PORT_TOTAL / hostsPerIp : 0;
        if (perHostPorts == 0) {
            sb.append("    警告: 内网主机数超过单 IP 可用端口总数，无法按主机均分端口块；\n")
                    .append("    PAT 动态复用端口仍可工作，但高并发场景建议扩容公网 IP。\n");
        } else {
            sb.append("    每个公网 IP 承载 ").append(String.format("%,d", hostsPerIp))
                    .append(" 台主机，均分后每台约 ").append(String.format("%,d", perHostPorts))
                    .append(" 个并发端口\n");
            sb.append("    端口块示例 (前 ").append(Math.min(hostsPerIp, PORT_BLOCK_SAMPLE_CAP))
                    .append(" 台主机):\n");
            long blocks = Math.min(hostsPerIp, PORT_BLOCK_SAMPLE_CAP);
            for (long i = 0; i < blocks; i++) {
                long start = PORT_BASE + i * perHostPorts;
                long end = (i == hostsPerIp - 1) ? PORT_TOP : start + perHostPorts - 1;
                sb.append(String.format("      %-15s →  %s : %d-%d%n",
                        SubnetCalculator.longToIp(intLo + i),
                        SubnetCalculator.longToIp(pubLo), start, end));
            }
            if (hostsPerIp > PORT_BLOCK_SAMPLE_CAP) {
                sb.append("      ... (其余主机依次顺延)\n");
            }
        }

        sb.append("  NAT 转换表示例 (动态 PAT，实际端口由设备按需分配，以下仅示意):\n");
        sb.append(String.format("    %-15s →  %s%n",
                SubnetCalculator.longToIp(intLo) + ":1024",
                SubnetCalculator.longToIp(pubLo) + ":1024"));

        // 参考配置片段
        sb.append("─".repeat(55)).append("\n");
        sb.append("  参考配置片段 (Cisco 风格，接口名按实际修改):\n");
        sb.append("    access-list 100 permit ip ").append(SubnetCalculator.longToIp(intNet))
                .append(" ").append(SubnetCalculator.longToIp(SubnetCalculator.getWildcardMask(intPrefix)))
                .append(" any\n");
        if (pubIsPool) {
            sb.append("    ip nat pool NAT_POOL ").append(SubnetCalculator.longToIp(pubLo))
                    .append(" ").append(SubnetCalculator.longToIp(pubHi))
                    .append(" netmask ").append(SubnetCalculator.longToIp(SubnetCalculator.getSubnetMask(pubPrefix)))
                    .append("\n");
            sb.append("    ip nat inside source list 100 pool NAT_POOL overload\n");
        } else {
            sb.append("    ip nat inside source list 100 interface GigabitEthernet0/0 overload\n");
        }
        if (!pubIsPool) {
            sb.append("    ! 或使用地址池形式 (单 IP 池):\n");
            sb.append("    ! ip nat pool NAT_POOL ").append(SubnetCalculator.longToIp(pubLo))
                    .append(" ").append(SubnetCalculator.longToIp(pubHi))
                    .append(" netmask 255.255.255.255\n");
            sb.append("    ! ip nat inside source list 100 pool NAT_POOL overload\n");
        }
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }
}
