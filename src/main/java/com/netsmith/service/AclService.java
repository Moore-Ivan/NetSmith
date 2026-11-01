package com.netsmith.service;

import com.netsmith.CidrValidator;
import com.netsmith.SubnetCalculator;
import com.netsmith.ValidationError;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cisco 风格扩展 ACL 解析与策略检查（仅 IPv4）。
 * <p>规则语法（大小写不敏感，空白分隔，每行一条，支持 ! 与 # 注释行）：
 * <pre>permit|deny ip|tcp|udp|icmp &lt;源地址&gt; [源端口] &lt;目的地址&gt; [目的端口]</pre>
 * <ul>
 *   <li>地址参数：{@code any} / {@code host A.B.C.D} / {@code A.B.C.D}（等价 host）/
 *       {@code A.B.C.D/x}（CIDR）/ {@code A.B.C.D 反掩码}（wildcard）</li>
 *   <li>端口参数（仅 tcp/udp 规则）：{@code eq n} / {@code gt n} / {@code lt n} / {@code range lo hi}，
 *       n 支持数字或常见服务名（http、ssh、dns 等）</li>
 * </ul>
 * <p>匹配语义：逐条评估、首条匹配生效；所有规则均不匹配时按 Cisco 隐式规则
 * {@code deny ip any any} 处理。
 */
public class AclService {

    /** 常见服务名 → 端口号（规则端口与数据包端口均可使用） */
    private static final Map<String, Integer> WELL_KNOWN_PORTS = Map.ofEntries(
            Map.entry("http", 80), Map.entry("www", 80), Map.entry("https", 443),
            Map.entry("ssh", 22), Map.entry("telnet", 23), Map.entry("smtp", 25),
            Map.entry("dns", 53), Map.entry("domain", 53), Map.entry("ftp", 21),
            Map.entry("ftp-data", 20), Map.entry("tftp", 69), Map.entry("pop3", 110),
            Map.entry("imap", 143), Map.entry("snmp", 161), Map.entry("bgp", 179),
            Map.entry("ntp", 123), Map.entry("syslog", 514), Map.entry("ldap", 389),
            Map.entry("rdp", 3389), Map.entry("mysql", 3306));

    private static final List<String> SUPPORTED_PROTOCOLS = List.of("ip", "tcp", "udp", "icmp");

    /** 判定结果（不可变值对象） */
    public static class AclCheckResult {
        /** 最终是否放行 */
        public final boolean permitted;
        /** 是否为隐式 deny（未命中任何显式规则） */
        public final boolean implicitDeny;
        /** 命中规则序号（从 1 计），隐式 deny 时为 -1 */
        public final int matchedRuleNo;
        /** 命中规则的原始文本，隐式 deny 时为 null */
        public final String matchedRuleText;
        /** 逐条规则评估轨迹（含不匹配原因），已格式化 */
        public final List<String> trace;
        /** 有效规则总数 */
        public final int ruleCount;
        /** 数据包摘要描述 */
        public final String packetSummary;

        AclCheckResult(boolean permitted, boolean implicitDeny, int matchedRuleNo,
                       String matchedRuleText, List<String> trace, int ruleCount, String packetSummary) {
            this.permitted = permitted;
            this.implicitDeny = implicitDeny;
            this.matchedRuleNo = matchedRuleNo;
            this.matchedRuleText = matchedRuleText;
            this.trace = trace;
            this.ruleCount = ruleCount;
            this.packetSummary = packetSummary;
        }
    }

    /** 端口匹配条件 */
    private record PortCond(String op, int v1, int v2) {
        boolean matches(int port) {
            return switch (op) {
                case "eq" -> port == v1;
                case "gt" -> port > v1;
                case "lt" -> port < v1;
                default -> port >= v1 && port <= v2; // range
            };
        }

        String describe() {
            return op.equals("range") ? op + " " + v1 + "-" + v2 : op + " " + v1;
        }
    }

    /** 单条 ACL 规则（解析后的内部表示） */
    private static final class AclRule {
        final int lineNo;
        final String rawText;
        final boolean permit;
        final String protocol;
        final long srcNet;
        final long srcWild;
        final long dstNet;
        final long dstWild;
        final PortCond srcPort;
        final PortCond dstPort;

        AclRule(int lineNo, String rawText, boolean permit, String protocol,
                long srcNet, long srcWild, long dstNet, long dstWild,
                PortCond srcPort, PortCond dstPort) {
            this.lineNo = lineNo;
            this.rawText = rawText;
            this.permit = permit;
            this.protocol = protocol;
            this.srcNet = srcNet;
            this.srcWild = srcWild;
            this.dstNet = dstNet;
            this.dstWild = dstWild;
            this.srcPort = srcPort;
            this.dstPort = dstPort;
        }
    }

    /**
     * 用 ACL 规则集检查一个数据包。
     *
     * @param rulesText ACL 规则文本（每行一条）
     * @param srcIp     源 IP
     * @param dstIp     目的 IP
     * @param protocol  数据包协议：ip / tcp / udp / icmp
     * @param srcPort   源端口（tcp/udp 时有效，可为空表示未指定；ip/icmp 时忽略）
     * @param dstPort   目的端口（同上）
     */
    public AclCheckResult check(String rulesText, String srcIp, String dstIp,
                                String protocol, String srcPort, String dstPort) {
        List<AclRule> rules = parseRules(rulesText);

        if (srcIp == null || srcIp.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.IP_START, "请输入源 IP");
        }
        if (dstIp == null || dstIp.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.IP_END, "请输入目的 IP");
        }
        long sip;
        try {
            sip = SubnetCalculator.ipToLong(srcIp.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationError(ValidationError.Field.IP_START, "无效的源 IP: " + e.getMessage());
        }
        long dip;
        try {
            dip = SubnetCalculator.ipToLong(dstIp.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationError(ValidationError.Field.IP_END, "无效的目的 IP: " + e.getMessage());
        }

        String proto = protocol == null ? "ip" : protocol.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PROTOCOLS.contains(proto)) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "协议仅支持 ip/tcp/udp/icmp: " + protocol);
        }
        Integer sport = parsePacketPort(srcPort, "源端口", ValidationError.Field.IP_START);
        Integer dport = parsePacketPort(dstPort, "目的端口", ValidationError.Field.IP_END);

        String packetSummary = buildPacketSummary(proto, srcIp.trim(), dstIp.trim(), sport, dport);

        List<String> trace = new ArrayList<>();
        for (AclRule r : rules) {
            String mismatch = firstMismatch(r, sip, dip, proto, sport, dport);
            if (mismatch == null) {
                trace.add(String.format("  #%-3d %s   → 匹配 ✓", r.lineNo, r.rawText));
                return new AclCheckResult(r.permit, false, r.lineNo, r.rawText,
                        trace, rules.size(), packetSummary);
            }
            trace.add(String.format("  #%-3d %s   → 不匹配 (%s)", r.lineNo, r.rawText, mismatch));
        }
        trace.add("  （已遍历全部规则，未命中）→ 隐式规则 deny ip any any 生效");
        return new AclCheckResult(false, true, -1, null, trace, rules.size(), packetSummary);
    }

    /** 返回首个不满足项的原因描述；全部满足返回 null（即命中） */
    private static String firstMismatch(AclRule r, long sip, long dip, String proto,
                                        Integer sport, Integer dport) {
        if (!r.protocol.equals("ip") && !r.protocol.equals(proto)) {
            return "协议不符: 规则 " + r.protocol + " ≠ 数据包 " + proto;
        }
        if (!ipInWildcard(sip, r.srcNet, r.srcWild)) {
            return "源地址不符";
        }
        if (r.srcPort != null && (sport == null || !r.srcPort.matches(sport))) {
            return sport == null ? "数据包未提供源端口" : "源端口不符 (要求 " + r.srcPort.describe() + ")";
        }
        if (!ipInWildcard(dip, r.dstNet, r.dstWild)) {
            return "目的地址不符";
        }
        if (r.dstPort != null && (dport == null || !r.dstPort.matches(dport))) {
            return dport == null ? "数据包未提供目的端口" : "目的端口不符 (要求 " + r.dstPort.describe() + ")";
        }
        return null;
    }

    /** 通配符匹配：通配位之外的比特必须完全一致（支持非连续反掩码） */
    private static boolean ipInWildcard(long ip, long net, long wild) {
        return (ip & ~wild) == (net & ~wild);
    }

    // ========== 规则解析 ==========

    private List<AclRule> parseRules(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "请输入 ACL 规则");
        }
        List<AclRule> rules = new ArrayList<>();
        int lineNo = 0;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("#")) {
                continue;
            }
            lineNo++;
            try {
                rules.add(parseRule(lineNo, line));
            } catch (ValidationError e) {
                throw new ValidationError(e.getField(),
                        "第 " + lineNo + " 行 [" + line + "]: " + e.getMessage());
            } catch (Exception e) {
                throw new ValidationError(ValidationError.Field.UNKNOWN,
                        "第 " + lineNo + " 行 [" + line + "]: 规则语法错误");
            }
        }
        if (rules.isEmpty()) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "未解析到有效 ACL 规则");
        }
        return rules;
    }

    private static AclRule parseRule(int lineNo, String line) {
        String[] t = line.split("\\s+");
        int[] idx = {0};

        boolean permit = switch (t[idx[0]].toLowerCase(Locale.ROOT)) {
            case "permit" -> true;
            case "deny" -> false;
            default -> throw new ValidationError(ValidationError.Field.UNKNOWN,
                    "规则必须以 permit 或 deny 开头");
        };
        idx[0]++;

        requireToken(t, idx, "协议");
        String proto = t[idx[0]].toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PROTOCOLS.contains(proto)) {
            throw new ValidationError(ValidationError.Field.UNKNOWN,
                    "协议仅支持 ip/tcp/udp/icmp: " + t[idx[0]]);
        }
        idx[0]++;

        long[] src = parseAddrSpec(t, idx, "源");
        PortCond srcPort = proto.equals("tcp") || proto.equals("udp")
                ? parsePortCond(t, idx) : null;
        long[] dst = parseAddrSpec(t, idx, "目的");
        PortCond dstPort = proto.equals("tcp") || proto.equals("udp")
                ? parsePortCond(t, idx) : null;

        if (idx[0] < t.length) {
            throw new ValidationError(ValidationError.Field.UNKNOWN,
                    "无法识别的参数: " + t[idx[0]]);
        }
        return new AclRule(lineNo, line, permit, proto,
                src[0], src[1], dst[0], dst[1], srcPort, dstPort);
    }

    /**
     * 解析地址参数：any | host A.B.C.D | A.B.C.D | A.B.C.D/x | A.B.C.D 反掩码。
     * 返回 {网络地址, 通配符}；any 表示为 {0, 0xFFFFFFFF}，单个 IP 表示为 {ip, 0}。
     */
    private static long[] parseAddrSpec(String[] t, int[] idx, String side) {
        if (idx[0] >= t.length) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "缺少" + side + "地址参数");
        }
        String tok = t[idx[0]];
        if (tok.equalsIgnoreCase("any")) {
            idx[0]++;
            return new long[]{0L, 0xFFFFFFFFL};
        }
        if (tok.equalsIgnoreCase("host")) {
            idx[0]++;
            requireToken(t, idx, "host 后的 IP");
            return new long[]{parseIp(t[idx[0]++], side), 0L};
        }
        if (tok.contains("/")) {
            String[] parts;
            try {
                parts = CidrValidator.validateCidr(tok);
            } catch (ValidationError e) {
                throw new ValidationError(ValidationError.Field.UNKNOWN, side + "地址无效: " + e.getMessage());
            }
            idx[0]++;
            int prefix = Integer.parseInt(parts[1]);
            long net = SubnetCalculator.getNetworkAddress(SubnetCalculator.ipToLong(parts[0]), prefix);
            return new long[]{net, SubnetCalculator.getWildcardMask(prefix)};
        }
        long ip = parseIp(tok, side);
        idx[0]++;
        // 反掩码形式：紧随其后的 token 也是点分十进制（Cisco 语义中即为通配符）
        if (idx[0] < t.length && isDottedQuad(t[idx[0]])) {
            long wild = parseWildcard(t[idx[0]++]);
            return new long[]{ip, wild};
        }
        return new long[]{ip, 0L};
    }

    /** 解析端口条件（eq/gt/lt/range），下一个 token 不是端口操作符时返回 null */
    private static PortCond parsePortCond(String[] t, int[] idx) {
        if (idx[0] >= t.length) {
            return null;
        }
        String op = t[idx[0]].toLowerCase(Locale.ROOT);
        if (!op.equals("eq") && !op.equals("gt") && !op.equals("lt") && !op.equals("range")) {
            return null;
        }
        idx[0]++;
        requireToken(t, idx, "端口值");
        int v1 = parsePortValue(t[idx[0]++]);
        if (op.equals("range")) {
            requireToken(t, idx, "range 的结束端口");
            int v2 = parsePortValue(t[idx[0]++]);
            if (v1 > v2) {
                throw new ValidationError(ValidationError.Field.UNKNOWN,
                        "range 起始端口不能大于结束端口: " + v1 + " > " + v2);
            }
            return new PortCond(op, v1, v2);
        }
        return new PortCond(op, v1, v1);
    }

    /** 解析端口值：数字（0-65535）或常见服务名 */
    private static int parsePortValue(String s) {
        String v = s.toLowerCase(Locale.ROOT);
        Integer named = WELL_KNOWN_PORTS.get(v);
        if (named != null) {
            return named;
        }
        try {
            int p = Integer.parseInt(v);
            if (p >= 0 && p <= 65535) {
                return p;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new ValidationError(ValidationError.Field.UNKNOWN,
                "无效端口: " + s + "（应为 0-65535 数字或常见服务名如 http/ssh）");
    }

    /** 解析数据包端口输入：空表示未指定（返回 null） */
    private static Integer parsePacketPort(String s, String side, ValidationError.Field field) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return parsePortValue(s);
        } catch (ValidationError e) {
            throw new ValidationError(field, side + "无效: " + e.getMessage());
        }
    }

    private static String buildPacketSummary(String proto, String srcIp, String dstIp,
                                             Integer sport, Integer dport) {
        StringBuilder sb = new StringBuilder();
        sb.append(proto.toUpperCase(Locale.ROOT)).append("  ")
                .append(srcIp).append(" → ").append(dstIp);
        if (proto.equals("tcp") || proto.equals("udp")) {
            sb.append("   源端口: ").append(sport == null ? "未指定" : sport)
                    .append("，目的端口: ").append(dport == null ? "未指定" : dport);
        } else if (sport != null || dport != null) {
            sb.append("   (协议为 ").append(proto).append("，端口参数已忽略)");
        }
        return sb.toString();
    }

    private static void requireToken(String[] t, int[] idx, String what) {
        if (idx[0] >= t.length) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "缺少" + what);
        }
    }

    private static long parseIp(String s, String side) {
        try {
            return SubnetCalculator.ipToLong(s);
        } catch (IllegalArgumentException e) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, side + "地址无效: " + s);
        }
    }

    private static final String DOTTED_QUAD = "\\d{1,3}(\\.\\d{1,3}){3}";

    private static boolean isDottedQuad(String s) {
        return s.matches(DOTTED_QUAD);
    }

    private static long parseWildcard(String s) {
        if (!isDottedQuad(s)) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "无效的反掩码: " + s);
        }
        try {
            return SubnetCalculator.ipToLong(s);
        } catch (IllegalArgumentException e) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "无效的反掩码: " + s);
        }
    }
}
