package com.netsmith;

import java.math.BigInteger;
import java.util.*;

/**
 * IPv6 地址解析与子网计算工具
 * 支持：网段详情、等长子网划分、地址类型判断、压缩/展开格式转换
 */
public class IPv6Calculator {

    // ---------- 基础转换 ----------

    /** 解析 IPv6 地址字符串（支持压缩格式和 ::）为 128 位 BigInteger */
    public static BigInteger parseIPv6(String addr) {
        String s = addr.trim().toLowerCase();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("IPv6 地址不能为空");
        }

        // 移除 CIDR 前缀（如果存在）
        if (s.contains("/")) {
            s = s.split("/")[0];
        }

        // 纯 IPv4 地址检测：不含冒号但符合点分十进制格式
        // IPv6 地址至少包含一个冒号；纯 IPv4 地址应使用 IPv4 相关功能
        if (!s.contains(":") && s.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            throw new IllegalArgumentException("这是 IPv4 地址，请使用 IPv4 相关功能");
        }

        // 预校验：检查是否包含非法字符
        // IPv4 映射地址（如 ::ffff:192.0.2.1）包含点号，需要特殊处理
        if (s.contains(".")) {
            // IPv4 映射地址格式：允许十六进制字符、冒号和点号
            if (!s.matches("[0-9a-f:.]+")) {
                throw new IllegalArgumentException("IPv6 地址包含非法字符: \"" + addr + "\"");
            }
        } else {
            // 纯 IPv6 地址：只允许十六进制字符和冒号
            if (!s.matches("[0-9a-f:]+")) {
                throw new IllegalArgumentException("IPv6 地址包含非法字符: \"" + addr + "\"");
            }
        }

        // 检查是否包含 ::: 或更多连续冒号
        if (s.contains(":::")) {
            throw new IllegalArgumentException("IPv6 地址含非法连续冒号: \"" + addr + "\"");
        }

        // 检查 :: 出现次数（只能出现一次）
        int doubleColonCount = 0;
        int idx = -1;
        while ((idx = s.indexOf("::", idx + 1)) != -1) {
            doubleColonCount++;
        }
        if (doubleColonCount > 1) {
            throw new IllegalArgumentException("IPv6 地址含多个 :: 缩写，非法: \"" + addr + "\"");
        }

        if (doubleColonCount == 1) {
            return parseIPv6WithDoubleColon(s, addr);
        } else {
            return parseIPv6Full(s, addr);
        }
    }

    /**
     * 解析包含 :: 的 IPv6 地址
     */
    private static BigInteger parseIPv6WithDoubleColon(String s, String originalAddr) {
        int doubleColonIndex = s.indexOf("::");

        // 拆分左右两侧
        String left = s.substring(0, doubleColonIndex);
        String right = s.substring(doubleColonIndex + 2);

        // 处理空字符串情况（如 "::" 或 "::1" 或 "2001::"）
        List<String> leftParts = new ArrayList<>();
        if (!left.isEmpty()) {
            for (String part : left.split(":")) {
                if (!part.isEmpty()) {
                    if (part.contains(".")) {
                        // IPv4 嵌入式表示法，转换为两个十六进制段
                        String[] ipv4Parts = parseIPv4ToHex(part, originalAddr);
                        leftParts.add(ipv4Parts[0]);
                        leftParts.add(ipv4Parts[1]);
                    } else {
                        validateHexPart(part, originalAddr);
                        leftParts.add(part);
                    }
                }
            }
        }

        List<String> rightParts = new ArrayList<>();
        if (!right.isEmpty()) {
            for (String part : right.split(":")) {
                if (!part.isEmpty()) {
                    if (part.contains(".")) {
                        // IPv4 嵌入式表示法，转换为两个十六进制段
                        String[] ipv4Parts = parseIPv4ToHex(part, originalAddr);
                        rightParts.add(ipv4Parts[0]);
                        rightParts.add(ipv4Parts[1]);
                    } else {
                        validateHexPart(part, originalAddr);
                        rightParts.add(part);
                    }
                }
            }
        }

        // 计算缺失的段数
        int totalParts = leftParts.size() + rightParts.size();
        int missing = 8 - totalParts;

        if (missing < 0) {
            throw new IllegalArgumentException(
                "IPv6 地址段数超过 8 组: \"" + originalAddr + "\"");
        }
        if (missing == 0) {
            throw new IllegalArgumentException(
                "IPv6 地址段数已达 8 组，:: 缩写无效: \"" + originalAddr + "\"");
        }

        // 构建完整的 128 位十六进制字符串
        StringBuilder full = new StringBuilder();
        for (String p : leftParts) {
            full.append(String.format("%4s", p).replace(' ', '0'));
        }
        for (int i = 0; i < missing; i++) {
            full.append("0000");
        }
        for (String p : rightParts) {
            full.append(String.format("%4s", p).replace(' ', '0'));
        }

        return new BigInteger(full.toString(), 16);
    }

    /**
     * 解析完整的 IPv6 地址（不含 ::）
     */
    private static BigInteger parseIPv6Full(String s, String originalAddr) {
        String[] parts = s.split(":");

        // 收集所有段（可能包含 IPv4 嵌入式表示法）
        List<String> allParts = new ArrayList<>();
        for (String p : parts) {
            if (p.contains(".")) {
                // IPv4 嵌入式表示法，转换为两个十六进制段
                String[] ipv4Parts = parseIPv4ToHex(p, originalAddr);
                allParts.add(ipv4Parts[0]);
                allParts.add(ipv4Parts[1]);
            } else {
                validateHexPart(p, originalAddr);
                allParts.add(p);
            }
        }

        // 验证段数必须为 8
        if (allParts.size() != 8) {
            throw new IllegalArgumentException(
                "IPv6 地址段数必须为 8 组（未使用 :: 缩写时）: \"" + originalAddr + "\"");
        }

        // 构建完整的 128 位十六进制字符串
        StringBuilder full = new StringBuilder();
        for (String p : allParts) {
            full.append(String.format("%4s", p).replace(' ', '0'));
        }

        return new BigInteger(full.toString(), 16);
    }

    /**
     * 将 IPv4 点分十进制地址转换为两个 16 位十六进制段
     * @param ipv4Str IPv4 地址字符串（如 "192.0.2.1"）
     * @param originalAddr 原始地址（用于错误消息）
     * @return 两个 4 位十六进制段的数组
     */
    private static String[] parseIPv4ToHex(String ipv4Str, String originalAddr) {
        String[] octets = ipv4Str.split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("无效的 IPv4 嵌入式地址: \"" + ipv4Str + "\" in \"" + originalAddr + "\"");
        }

        int[] values = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                values[i] = Integer.parseInt(octets[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("无效的 IPv4 地址: \"" + ipv4Str + "\" in \"" + originalAddr + "\"");
            }
            if (values[i] < 0 || values[i] > 255) {
                throw new IllegalArgumentException("IPv4 地址字节值必须在 0-255 之间: \"" + ipv4Str + "\" in \"" + originalAddr + "\"");
            }
        }

        // 转换为两个 16 位段
        int high16 = (values[0] << 8) | values[1];
        int low16 = (values[2] << 8) | values[3];

        return new String[] {
            String.format("%04x", high16),
            String.format("%04x", low16)
        };
    }

    /**
     * 验证单个十六进制段的有效性
     */
    private static void validateHexPart(String part, String originalAddr) {
        if (part.isEmpty()) {
            throw new IllegalArgumentException("IPv6 地址包含空段: \"" + originalAddr + "\"");
        }
        if (part.length() > 4) {
            throw new IllegalArgumentException(
                "IPv6 地址每段不能超过 4 位十六进制: \"" + originalAddr + "\"");
        }
        // 验证是否为有效的十六进制
        try {
            Integer.parseInt(part, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "IPv6 地址包含无效的十六进制段: \"" + part + "\" in \"" + originalAddr + "\"");
        }
    }

    /** 将 128 位 BigInteger 转换为完整展开格式 */
    public static String formatFull(BigInteger ip) {
        String hex = String.format("%32s", ip.toString(16)).replace(' ', '0');
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0) sb.append(":");
            sb.append(hex.substring(i * 4, (i + 1) * 4));
        }
        return sb.toString();
    }

    /** 将 128 位 BigInteger 转换为标准压缩格式 */
    public static String formatCompressed(BigInteger ip) {
        String full = formatFull(ip);
        String[] parts = full.split(":");
        
        int maxZeroRun = 0;
        int zeroStart = -1;
        int currentZeroRun = 0;
        
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("0000")) {
                currentZeroRun++;
                if (currentZeroRun > maxZeroRun) {
                    maxZeroRun = currentZeroRun;
                    zeroStart = i - currentZeroRun + 1;
                }
            } else {
                currentZeroRun = 0;
            }
        }
        
        if (maxZeroRun >= 2) {
            StringBuilder sb = new StringBuilder();
            boolean skipNextColon = false;
            
            for (int i = 0; i < parts.length; i++) {
                if (i == zeroStart) {
                    // 添加 "::" 并跳过所有零段
                    sb.append("::");
                    i += maxZeroRun - 1; // 跳过所有零段
                    skipNextColon = true; // 下一个段不需要前导冒号
                } else {
                    if (skipNextColon) {
                        // :: 后面的第一个非零段，直接添加不需要前导冒号
                        sb.append(shortenGroup(parts[i]));
                        skipNextColon = false;
                    } else if (i > 0) {
                        // 其他情况：需要添加前导冒号
                        sb.append(":");
                        sb.append(shortenGroup(parts[i]));
                    } else {
                        // 第一个段且不是零段
                        sb.append(shortenGroup(parts[i]));
                    }
                }
            }
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(":");
                sb.append(shortenGroup(parts[i]));
            }
            return sb.toString();
        }
    }

    private static String shortenGroup(String group) {
        int i = 0;
        while (i < group.length() - 1 && group.charAt(i) == '0') {
            i++;
        }
        return group.substring(i);
    }

    /** 获取子网掩码 */
    public static BigInteger getSubnetMask(int prefix) {
        if (prefix <= 0) return BigInteger.ZERO;
        if (prefix >= 128) return new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16);
        BigInteger mask = BigInteger.ZERO;
        for (int i = 0; i < prefix; i++) {
            mask = mask.setBit(127 - i);
        }
        return mask;
    }

    /** 获取网络地址 */
    public static BigInteger getNetworkAddress(BigInteger addr, int prefix) {
        BigInteger mask = getSubnetMask(prefix);
        return addr.and(mask);
    }

    /** 获取末地址 */
    public static BigInteger getLastAddress(BigInteger addr, int prefix) {
        BigInteger wildcard = BigInteger.ONE.shiftLeft(128 - prefix).subtract(BigInteger.ONE);
        return getNetworkAddress(addr, prefix).or(wildcard);
    }

    /**
     * 可用主机数。
     * 口径说明（有意如此，非疏漏）：
     * - 常规前缀：地址总数减 1，即扣除 Subnet-Router anycast（子网前缀+全零，RFC 4291 §2.6.1 保留）；
     * - /127：返回 2——RFC 6164 规定 /127 仅用于点对点链路，两个地址均可分配，
     *   Subnet-Router anycast 不适用于 /127；
     * - /128：返回 1——单个主机地址，无任播保留。
     * 因此 /128→1、/127→2、/126→3 是 RFC 语义下的正确序列，/126 与 /127 之间的
     * "跳跃"来自 /127 的特殊豁免，而非口径不一致。
     */
    public static BigInteger getUsableHostCount(int prefix) {
        if (prefix < 0 || prefix > 128) {
            throw new IllegalArgumentException("前缀必须在 0-128 之间: " + prefix);
        }
        if (prefix == 128) return BigInteger.ONE;
        if (prefix == 127) return BigInteger.valueOf(2);
        return BigInteger.ONE.shiftLeft(128 - prefix).subtract(BigInteger.ONE);
    }

    // ---------- 地址类型判断 ----------

    public static String getAddressType(BigInteger addr) {
        if (addr.equals(BigInteger.ZERO)) return "未指定地址 (::)";
        if (addr.equals(BigInteger.ONE)) return "回环地址 (::1)";
        if (addr.shiftRight(118).equals(BigInteger.valueOf(0x3FA))) return "链路本地地址 (fe80::/10)";
        
        // 唯一本地地址 (fc00::/7)
        if (addr.shiftRight(121).equals(BigInteger.valueOf(0x7E))) {
            // 区分 ULA 的两种形式
            // fc00::/8 - 全局 ID 由 IANA 分配
            // fd00::/8 - 全局 ID 由本地生成（随机）
            if ((addr.shiftRight(120).intValue() & 0xFF) == 0xFC) {
                return "唯一本地地址 (fc00::/8, IANA分配)";
            } else {
                return "唯一本地地址 (fd00::/8, 本地随机)";
            }
        }
        
        // 多播地址 (ff00::/8)
        if (addr.shiftRight(120).equals(BigInteger.valueOf(0xFF))) {
            int scope = addr.shiftRight(112).intValue() & 0x0F;
            String scopeName = switch(scope) {
                case 0 -> "保留";
                case 1 -> "节点本地";
                case 2 -> "链路本地";
                case 3 -> "子网本地";
                case 4 -> "管理本地";
                case 5 -> "站点本地";
                case 8 -> "组织本地";
                case 14 -> "全局";
                case 15 -> "保留";
                default -> "未知范围";
            };
            return "多播地址 (ff00::/8), " + scopeName + "范围";
        }
        
        // 全局单播地址范围内的特殊类型（需要在全局单播之前判断）
        if (addr.shiftRight(125).equals(BigInteger.ONE)) {
            // 文档前缀 (2001:db8::/32) - RFC 3849
            // 2001:db8::/32 的前 32 位为 0x20010db8
            if (addr.shiftRight(96).equals(BigInteger.valueOf(0x20010db8L))) {
                return "文档前缀地址 (2001:db8::/32, RFC 3849)";
            }
            
            // 6to4 地址 (2002::/16) - RFC 3056
            if (addr.shiftRight(112).equals(BigInteger.valueOf(0x2002))) {
                // 提取嵌入的 IPv4 地址：格式为 2002:V4ADDR:SUBNET::/48，
                // V4ADDR 位于 128 位地址的第 16-47 位（即右移 80 位后的低 32 位）
                long ipv4Long = addr.shiftRight(80).and(new BigInteger("FFFFFFFF", 16)).longValue();
                int octet1 = (int) ((ipv4Long >> 24) & 0xFF);
                int octet2 = (int) ((ipv4Long >> 16) & 0xFF);
                int octet3 = (int) ((ipv4Long >> 8) & 0xFF);
                int octet4 = (int) (ipv4Long & 0xFF);
                return String.format("6to4 隧道地址 (2002::/16), 嵌入 IPv4: %d.%d.%d.%d", 
                    octet1, octet2, octet3, octet4);
            }
            
            // Teredo 地址 (2001::/32) - RFC 4380
            if (addr.shiftRight(96).equals(BigInteger.valueOf(0x20010000L))) {
                return "Teredo 隧道地址 (2001::/32, RFC 4380)";
            }
            
            // ORCHID 地址 (2001:10::/28) - RFC 4843
            // 2001:10::/28 的前 28 位为 0x2001001（0x20010010 >> 4）
            if (addr.shiftRight(100).equals(BigInteger.valueOf(0x2001001L))) {
                return "ORCHID 地址 (2001:10::/28, RFC 4843)";
            }
            
            return "全局单播地址 (2000::/3)";
        }
        
        // IPv4 映射地址 (::ffff:0:0/96)
        if (isIPv4MappedAddress(addr)) {
            return "IPv4 映射地址 (::ffff:0:0/96)";
        }
        
        // IPv4 兼容地址 (::/96) - 已废弃
        if (isIPv4CompatibleAddress(addr)) {
            return "IPv4 兼容地址 (::/96, 已废弃)";
        }
        
        return "其他类型地址";
    }

    public static boolean isLinkLocal(BigInteger addr) {
        return addr.shiftRight(118).equals(BigInteger.valueOf(0x3FA));
    }

    public static boolean isMulticast(BigInteger addr) {
        return addr.shiftRight(120).equals(BigInteger.valueOf(0xFF));
    }

    /**
     * 判断是否为任播地址的候选地址
     * 任播地址从单播地址空间分配，通常具有以下特征：
     * - 地址是网络地址（host bits 全为 0）
     * - 前缀长度为 /128（单地址任播）或 /64（子网任播）
     * 
     * @param addr IPv6 地址
     * @param prefix 前缀长度
     * @return 是否为任播地址候选
     */
    public static boolean isAnycastCandidate(BigInteger addr, int prefix) {
        // 任播地址必须是网络地址（host bits 全为 0）
        BigInteger mask = getSubnetMask(prefix);
        BigInteger network = addr.and(mask);
        if (!addr.equals(network)) {
            return false;
        }
        
        // 任播地址通常使用 /128（单地址）或 /64（子网任播）前缀
        return prefix == 128 || prefix == 64;
    }

    /**
     * 判断是否为链路本地任播地址
     * 链路本地任播地址：fe80:: 和 fe80::1
     * 
     * @param addr IPv6 地址
     * @return 是否为链路本地任播地址
     */
    public static boolean isLinkLocalAnycast(BigInteger addr) {
        if (!isLinkLocal(addr)) {
            return false;
        }
        // fe80::/10 范围内的特殊任播地址
        BigInteger linkLocalAnycast = parseIPv6("fe80::");
        BigInteger linkLocalRouterAnycast = parseIPv6("fe80::1");
        return addr.equals(linkLocalAnycast) || addr.equals(linkLocalRouterAnycast);
    }

    /**
     * 判断是否为子网路由任播地址
     * 子网路由任播地址格式：子网前缀 + ::
     * 
     * @param addr IPv6 地址
     * @param prefix 前缀长度
     * @return 是否为子网路由任播地址
     */
    public static boolean isSubnetRouterAnycast(BigInteger addr, int prefix) {
        // 必须是网络地址
        BigInteger mask = getSubnetMask(prefix);
        BigInteger network = addr.and(mask);
        if (!addr.equals(network)) {
            return false;
        }
        
        // 子网路由任播通常使用 /64 前缀
        return prefix == 64;
    }

    /**
     * 判断是否为本地子网路由任播地址
     * 本地子网路由任播地址格式：子网前缀 + ::1
     * 
     * @param addr IPv6 地址
     * @param prefix 前缀长度
     * @return 是否为本地子网路由任播地址
     */
    public static boolean isLocalSubnetRouterAnycast(BigInteger addr, int prefix) {
        BigInteger mask = getSubnetMask(prefix);
        BigInteger network = addr.and(mask);
        // 地址应该是网络地址 + 1
        return addr.equals(network.add(BigInteger.ONE)) && prefix == 64;
    }

    /**
     * 获取任播地址类型描述
     * 
     * @param addr IPv6 地址
     * @param prefix 前缀长度
     * @return 任播地址类型描述，如果不是任播地址则返回 null
     */
    public static String getAnycastType(BigInteger addr, int prefix) {
        // 链路本地任播地址
        if (isLinkLocalAnycast(addr)) {
            if (addr.equals(parseIPv6("fe80::"))) {
                return "链路本地任播地址 (fe80::, RFC 4291)";
            } else if (addr.equals(parseIPv6("fe80::1"))) {
                return "链路本地路由器任播地址 (fe80::1, RFC 4291)";
            }
        }
        
        // 子网路由任播地址
        if (isSubnetRouterAnycast(addr, prefix)) {
            return "子网路由任播地址 (" + formatCompressed(addr) + "/" + prefix + ", RFC 4291)";
        }
        
        // 本地子网路由任播地址
        if (isLocalSubnetRouterAnycast(addr, prefix)) {
            return "本地子网路由任播地址 (" + formatCompressed(addr) + "/" + prefix + ", RFC 4291)";
        }
        
        // 全局单播范围内的 /128 任播地址
        if (prefix == 128 && isGlobalUnicast(addr)) {
            return "全局任播地址 (" + formatCompressed(addr) + "/128)";
        }
        
        // 其他任播候选地址
        if (isAnycastCandidate(addr, prefix)) {
            return "任播地址候选 (" + formatCompressed(addr) + "/" + prefix + ")";
        }
        
        return null;
    }

    /**
     * 判断是否为全局单播地址
     */
    private static boolean isGlobalUnicast(BigInteger addr) {
        return addr.shiftRight(125).equals(BigInteger.ONE);
    }

    // ---------- 子网详情 ----------

    public static String getSubnetDetails(String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) throw new IllegalArgumentException("格式：IPv6地址/前缀长度");
        BigInteger addr = parseIPv6(parts[0]);
        int prefix = Integer.parseInt(parts[1]);
        if (prefix < 0 || prefix > 128) throw new IllegalArgumentException("前缀 0-128");

        BigInteger mask = getSubnetMask(prefix);
        BigInteger network = getNetworkAddress(addr, prefix);
        BigInteger last = getLastAddress(addr, prefix);
        BigInteger usable = getUsableHostCount(prefix);

        StringBuilder sb = new StringBuilder();
        sb.append("地址段：       ").append(parts[0]).append("/").append(prefix).append("\n");
        sb.append("完整展开：     ").append(formatFull(addr)).append("\n");
        sb.append("压缩格式：     ").append(formatCompressed(addr)).append("\n");
        sb.append("子网掩码：     ").append(formatFull(mask)).append(" /").append(prefix).append("\n");
        sb.append("网络地址：     ").append(formatCompressed(network)).append("/").append(prefix).append("\n");
        sb.append("末地址：       ").append(formatCompressed(last)).append("\n");
        if (prefix < 128) {
            sb.append("可用主机数：   ").append(usable).append("\n");
        } else {
            sb.append("可用主机数：   1（/128 单个地址）\n");
        }
        sb.append("地址类型：     ").append(getAddressType(addr)).append("\n");
        
        // 任播地址类型检测
        String anycastType = getAnycastType(addr, prefix);
        if (anycastType != null) {
            sb.append("任播类型：     ").append(anycastType).append("\n");
        }
        
        return sb.toString();
    }

    // ---------- 子网划分 ----------

    public static class IPv6Block {
        public final BigInteger network;
        public final int prefix;
        public final BigInteger mask;
        public final BigInteger last;

        public IPv6Block(BigInteger network, int prefix) {
            BigInteger mask = getSubnetMask(prefix);
            this.network = network.and(mask);
            this.prefix = prefix;
            this.mask = mask;
            this.last = this.network.or(BigInteger.ONE.shiftLeft(128 - prefix).subtract(BigInteger.ONE));
        }

        public BigInteger size() { return BigInteger.ONE.shiftLeft(128 - prefix); }
        public BigInteger usableHosts() { return getUsableHostCount(prefix); }

        @Override
        public String toString() {
            return formatCompressed(network) + "/" + prefix;
        }
    }

    /** 空闲块比较器：按块大小升序，大小相同按起始地址升序（用于 VLSM 的 bestFit 查找） */
    private static final Comparator<IPv6Block> FREE_BLOCK_COMPARATOR = (a, b) -> {
        int c = a.size().compareTo(b.size());
        if (c != 0) return c;
        return a.network.compareTo(b.network);
    };

    /**
     * IPv6 子网迭代器 - 支持任意大数量子网的增量生成
     * 使用游标分页思想，每次只生成当前位置的子网，避免内存溢出
     */
    public static class SubnetIterator implements Iterator<IPv6Block> {
        private final BigInteger majorNetwork;
        private final BigInteger subnetSize;
        private final int newPrefix;
        private final BigInteger maxSubnets;
        private BigInteger currentIndex;

        /**
         * 创建子网迭代器
         * @param majorNetwork 主网络地址
         * @param subnetSize 子网大小
         * @param newPrefix 新前缀
         * @param maxSubnets 最大子网数量
         */
        public SubnetIterator(BigInteger majorNetwork, BigInteger subnetSize, 
                              int newPrefix, BigInteger maxSubnets) {
            this.majorNetwork = majorNetwork;
            this.subnetSize = subnetSize;
            this.newPrefix = newPrefix;
            this.maxSubnets = maxSubnets;
            this.currentIndex = BigInteger.ZERO;
        }

        @Override
        public boolean hasNext() {
            return currentIndex.compareTo(maxSubnets) < 0;
        }

        @Override
        public IPv6Block next() {
            if (!hasNext()) {
                throw new NoSuchElementException("没有更多子网");
            }
            IPv6Block block = new IPv6Block(
                majorNetwork.add(subnetSize.multiply(currentIndex)),
                newPrefix
            );
            currentIndex = currentIndex.add(BigInteger.ONE);
            return block;
        }

        /**
         * 获取当前迭代位置（游标）
         * @return 当前索引
         */
        public BigInteger getCurrentIndex() {
            return currentIndex;
        }

        /**
         * 跳转到指定位置
         * @param index 目标索引
         */
        public void seek(BigInteger index) {
            if (index.compareTo(BigInteger.ZERO) < 0) {
                throw new IllegalArgumentException("索引不能为负数");
            }
            this.currentIndex = index;
        }

        /**
         * 获取剩余子网数量
         * @return 剩余数量
         */
        public BigInteger getRemainingCount() {
            return maxSubnets.subtract(currentIndex).max(BigInteger.ZERO);
        }

        /**
         * 获取总子网数量
         * @return 总数量
         */
        public BigInteger getTotalCount() {
            return maxSubnets;
        }
    }

    /**
     * 创建子网迭代器，支持增量加载
     * @param majorCidr 主网络 CIDR
     * @param newPrefix 目标前缀
     * @return 子网迭代器
     */
    public static SubnetIterator createSubnetIterator(String majorCidr, int newPrefix) {
        String[] parts = majorCidr.split("/");
        BigInteger majorNetwork = getNetworkAddress(parseIPv6(parts[0]), Integer.parseInt(parts[1]));
        int majorPrefix = Integer.parseInt(parts[1]);

        if (newPrefix < majorPrefix) throw new IllegalArgumentException("新前缀不能小于主网络前缀");
        if (newPrefix > 128) throw new IllegalArgumentException("前缀不能超过/128");

        BigInteger subnetSize = BigInteger.ONE.shiftLeft(128 - newPrefix);
        BigInteger maxSubnets = BigInteger.ONE.shiftLeft(newPrefix - majorPrefix);

        return new SubnetIterator(majorNetwork, subnetSize, newPrefix, maxSubnets);
    }

    /** 按子网数量等分 - 返回流式结果，避免内存峰值 */
    public static java.util.stream.Stream<IPv6Block> subnetByCount(String majorCidr, int subnetCount) {
        String[] parts = majorCidr.split("/");
        BigInteger majorNetwork = getNetworkAddress(parseIPv6(parts[0]), Integer.parseInt(parts[1]));
        int majorPrefix = Integer.parseInt(parts[1]);

        int bits = 0;
        while (bits < 128 && BigInteger.ONE.shiftLeft(bits).compareTo(BigInteger.valueOf(subnetCount)) < 0) {bits++;}
        int newPrefix = majorPrefix + bits;
        if (newPrefix > 128) throw new IllegalArgumentException("子网数量过多，前缀将超过/128");

        BigInteger subnetSize = BigInteger.ONE.shiftLeft(128 - newPrefix);
        return generateSubnetStream(majorNetwork, subnetSize, newPrefix, subnetCount);
    }

    /**
     * 按每子网所需地址数（如 /64 子网数）划分 - 返回流式结果，无数量限制
     * 使用迭代器构建流，支持任意大数量的子网，避免内存溢出
     *
     * @param majorCidr 主网络 CIDR
     * @param newPrefix 目标前缀
     * @return 子网流（建议使用 subnetByPrefixPaged 分页获取，避免 OOM）
     * @warning 直接 collect(Collectors.toList()) 可能导致 OOM，巨大数量时优先使用 subnetByPrefixPaged
     */
    public static java.util.stream.Stream<IPv6Block> subnetByPrefix(String majorCidr, int newPrefix) {
        SubnetIterator iterator = createSubnetIterator(majorCidr, newPrefix);
        return java.util.stream.StreamSupport.stream(
            java.util.Spliterators.spliteratorUnknownSize(iterator, java.util.Spliterator.ORDERED),
            false
        );
    }

    /**
     * 流式生成子网，支持分页，避免内存溢出
     * 使用迭代器构建流，支持任意大数量的子网
     * @param majorNetwork 主网络地址
     * @param subnetSize 子网大小
     * @param newPrefix 新前缀
     * @param count 子网数量
     * @return 子网流
     */
    private static java.util.stream.Stream<IPv6Block> generateSubnetStream(
            BigInteger majorNetwork, BigInteger subnetSize, int newPrefix, int count) {
        SubnetIterator iterator = new SubnetIterator(majorNetwork, subnetSize, newPrefix, BigInteger.valueOf(count));
        return java.util.stream.StreamSupport.stream(
            java.util.Spliterators.spliteratorUnknownSize(iterator, java.util.Spliterator.ORDERED),
            false
        );
    }

    /**
     * 流式生成子网（支持 BigInteger 数量）
     * 使用迭代器构建流，支持任意大数量的子网，无数量限制
     * @param majorNetwork 主网络地址
     * @param subnetSize 子网大小
     * @param newPrefix 新前缀
     * @param count 子网数量（BigInteger）
     * @return 子网流
     */
    private static java.util.stream.Stream<IPv6Block> generateSubnetStream(
            BigInteger majorNetwork, BigInteger subnetSize, int newPrefix, BigInteger count) {
        SubnetIterator iterator = new SubnetIterator(majorNetwork, subnetSize, newPrefix, count);
        return java.util.stream.StreamSupport.stream(
            java.util.Spliterators.spliteratorUnknownSize(iterator, java.util.Spliterator.ORDERED),
            false
        );
    }

    /**
     * 分页获取子网，避免内存溢出
     * @param majorCidr 主网络 CIDR
     * @param newPrefix 目标前缀
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页后的子网列表
     */
    public static List<IPv6Block> subnetByPrefixPaged(String majorCidr, int newPrefix, int pageNum, int pageSize) {
        String[] parts = majorCidr.split("/");
        BigInteger majorNetwork = getNetworkAddress(parseIPv6(parts[0]), Integer.parseInt(parts[1]));
        int majorPrefix = Integer.parseInt(parts[1]);

        if (newPrefix < majorPrefix) throw new IllegalArgumentException("新前缀不能小于主网络前缀");
        if (newPrefix > 128) throw new IllegalArgumentException("前缀不能超过/128");

        BigInteger subnetSize = BigInteger.ONE.shiftLeft(128 - newPrefix);
        BigInteger maxSubnets = BigInteger.ONE.shiftLeft(newPrefix - majorPrefix);

        // 页边界必须用 BigInteger 计算：总数可能超过 long/Integer.MAX_VALUE
        // （如 /32 → /64 共 2^32 个子网），截断会导致后半部分永远无法翻页
        BigInteger start = BigInteger.valueOf((pageNum - 1) * (long) pageSize);
        if (start.compareTo(maxSubnets) >= 0) {
            return new ArrayList<>();
        }
        int count = maxSubnets.subtract(start).min(BigInteger.valueOf(pageSize)).intValue();

        List<IPv6Block> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BigInteger index = start.add(BigInteger.valueOf(i));
            result.add(new IPv6Block(
                    majorNetwork.add(subnetSize.multiply(index)),
                    newPrefix
            ));
        }
        return result;
    }

    /**
     * 获取子网总数（用于分页）
     */
    public static BigInteger getSubnetCount(String majorCidr, int newPrefix) {
        String[] parts = majorCidr.split("/");
        int majorPrefix = Integer.parseInt(parts[1]);
        if (newPrefix < majorPrefix) return BigInteger.ZERO;
        if (newPrefix > 128) return BigInteger.ZERO;
        return BigInteger.ONE.shiftLeft(newPrefix - majorPrefix);
    }

    // ---------- 路由汇总（超网） ----------

    public static String summarizeIPv6(String... cidrs) {
        if (cidrs.length == 0) return null;

        List<IPv6Block> blocks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String c : cidrs) {
            String[] p = c.split("/");
            if (p.length != 2) throw new IllegalArgumentException("无效格式: " + c);
            BigInteger ip = parseIPv6(p[0]);
            int prefix = Integer.parseInt(p[1]);
            if (prefix < 0 || prefix > 128) throw new IllegalArgumentException("无效前缀: " + prefix);
            IPv6Block block = new IPv6Block(ip, prefix);
            // 按网络地址+前缀去重，避免相同 CIDR 重复输入导致异常
            String key = block.network.toString(16) + "/" + prefix;
            if (!seen.add(key)) continue;
            blocks.add(block);
        }

        if (blocks.size() == 1) return blocks.get(0).toString();

        BigInteger minNetwork = null;
        BigInteger maxLast = null;
        for (IPv6Block block : blocks) {
            if (minNetwork == null || block.network.compareTo(minNetwork) < 0) minNetwork = block.network;
            if (maxLast == null || block.last.compareTo(maxLast) > 0) maxLast = block.last;
        }

        BigInteger xor = BigInteger.ZERO;
        BigInteger firstNetwork = blocks.get(0).network;
        for (IPv6Block block : blocks) {
            xor = xor.or(firstNetwork.xor(block.network));
        }
        int commonPrefix = 128 - xor.bitLength();

        // XOR 仅基于网络地址计算，遇到嵌套或混合前缀（如 2001:db8::/32 与 2001:db8::/48）时
        // 候选超网会过小而无法包含所有网段。逐级缩小前缀（放大超网）直到包含全部网段；
        // ::/0 覆盖整个地址空间，因此循环必然能找到结果。
        IPv6Block superBlock = new IPv6Block(minNetwork, commonPrefix);
        while (commonPrefix > 0) {
            boolean allContained = true;
            for (IPv6Block block : blocks) {
                if (!isIPv6Subset(block, superBlock)) {
                    allContained = false;
                    break;
                }
            }
            if (allContained) break;
            commonPrefix--;
            superBlock = new IPv6Block(minNetwork, commonPrefix);
        }

        return superBlock.toString();
    }

    private static boolean isIPv6Subset(IPv6Block block, IPv6Block superBlock) {
        return block.network.compareTo(superBlock.network) >= 0
                && block.last.compareTo(superBlock.last) <= 0;
    }

    // ---------- VLSM 变长子网划分 ----------

    /**
     * 根据主机数获取最小前缀
     */
    public static int getPrefixForHosts(BigInteger hosts) {
        if (hosts == null || hosts.compareTo(BigInteger.ONE) < 0) {
            throw new IllegalArgumentException("主机数必须至少为 1");
        }
        if (hosts.equals(BigInteger.ONE)) return 128;
        if (hosts.equals(BigInteger.valueOf(2))) return 127;
        int bits = 0;
        while (bits < 128 && BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE).compareTo(hosts) < 0) {
            bits++;
        }
        return 128 - bits;
    }

    /**
     * VLSM 变长子网划分 - 按主机数列表分配子网
     * 使用最佳适配 + 剩余空间递归分割 + 空闲块合并策略
     */
    public static List<IPv6Block> vlsm(String majorCidr, BigInteger... hostCounts) {
        if (hostCounts.length == 0) {
            throw new IllegalArgumentException("请提供至少一个主机需求");
        }

        String[] parts = majorCidr.split("/");
        if (parts.length != 2) throw new IllegalArgumentException("无效的 CIDR 格式: " + majorCidr);

        BigInteger majorNetwork = getNetworkAddress(parseIPv6(parts[0]), Integer.parseInt(parts[1]));
        int majorPrefix = Integer.parseInt(parts[1]);
        BigInteger majorSize = BigInteger.ONE.shiftLeft(128 - majorPrefix);

        List<BigInteger> hosts = new ArrayList<>();
        BigInteger totalRequired = BigInteger.ZERO;
        for (BigInteger h : hostCounts) {
            if (h.compareTo(BigInteger.ONE) < 0) {
                throw new IllegalArgumentException("主机数必须至少为 1");
            }
            hosts.add(h);
            int needPrefix = getPrefixForHosts(h);
            BigInteger needSize = BigInteger.ONE.shiftLeft(128 - needPrefix);
            totalRequired = totalRequired.add(needSize);
        }

        if (totalRequired.compareTo(majorSize) > 0) {
            throw new IllegalArgumentException(
                "总需求 (" + totalRequired + " 地址) 超过主网络容量 (" + majorSize + " 地址)");
        }

        // 确保列表非空后再排序（已有长度检查，但排序前再次确认更安全）
        if (!hosts.isEmpty()) {
            hosts.sort(Collections.reverseOrder());
        }

        List<IPv6Block> allocated = new ArrayList<>();
        // 空闲块使用按 (大小, 起始地址) 排序的 TreeSet 管理，
        // bestFit 查找（大小 >= 需求的最小块）从 O(n) 降至 O(log n)
        TreeSet<IPv6Block> free = new TreeSet<>(FREE_BLOCK_COMPARATOR);
        free.add(new IPv6Block(majorNetwork, majorPrefix));

        for (BigInteger required : hosts) {
            // 检查任务是否已取消
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("VLSM 计算已取消");
            }
            
            int needPrefix = getPrefixForHosts(required);
            BigInteger needSize = BigInteger.ONE.shiftLeft(128 - needPrefix);

            // bestFit：找到大小 >= needSize 的最小空闲块（TreeSet.ceiling 为 O(log n)）
            IPv6Block searchKey = new IPv6Block(BigInteger.ZERO, needPrefix); // size() == needSize
            IPv6Block block = free.ceiling(searchKey);

            if (block == null) {
                // 碎片化处理：合并相邻空闲块后重试
                mergeAdjacentBlocks(free);
                block = free.ceiling(searchKey);
            }

            if (block == null) {
                throw new IllegalArgumentException(
                    "无法分配 " + required + " 主机子网，空间碎片化严重");
            }

            IPv6Block subnet = new IPv6Block(block.network, needPrefix);
            allocated.add(subnet);
            free.remove(block);

            if (block.last.compareTo(subnet.last) > 0) {
                splitRemainder(free, subnet.last.add(BigInteger.ONE), block.last);
                mergeAdjacentBlocks(free);
            }
        }
        return allocated;
    }

    private static void mergeAdjacentBlocks(TreeSet<IPv6Block> free) {
        if (free.size() < 2) return;

        // 按起始地址排序
        List<IPv6Block> sorted = new ArrayList<>(free);
        sorted.sort((a, b) -> a.network.compareTo(b.network));

        // 使用经典合并区间算法
        List<IPv6Block> merged = new ArrayList<>();
        merged.add(sorted.get(0));

        for (int i = 1; i < sorted.size(); i++) {
            IPv6Block current = sorted.get(i);
            IPv6Block last = merged.get(merged.size() - 1);

            // 检查当前块是否与最后一个合并块相邻或重叠
            if (current.network.compareTo(last.last.add(BigInteger.ONE)) <= 0) {
                // 需要合并：找到合适的前缀长度
                BigInteger mergedStart = last.network;
                BigInteger mergedEnd = current.last.max(last.last);
                BigInteger mergedSize = mergedEnd.subtract(mergedStart).add(BigInteger.ONE);

                int bits = findLargestPowerOfTwo(mergedSize, mergedStart);
                BigInteger blockSize = BigInteger.ONE.shiftLeft(bits);
                
                // 创建合并后的块（使用最大对齐的2的幂次方）
                merged.set(merged.size() - 1, new IPv6Block(mergedStart, 128 - bits));
                
                // 处理未覆盖的剩余空间（如果合并范围大于对齐块大小）
                BigInteger mergedBlockEnd = mergedStart.add(blockSize).subtract(BigInteger.ONE);
                if (mergedBlockEnd.compareTo(mergedEnd) < 0) {
                    // 将剩余部分（mergedBlockEnd+1 到 mergedEnd）拆分为合适的块
                    splitRemainder(merged, mergedBlockEnd.add(BigInteger.ONE), mergedEnd);
                }
            } else {
                // 不相邻，添加为新块
                merged.add(current);
            }
        }

        // 将合并结果写回空闲集合
        free.clear();
        free.addAll(merged);
    }

    /**
     * 找到最大的2的幂次方，满足：
     * 1. 该幂次方 <= mergedSize
     * 2. mergedStart 对齐到该幂次方的边界
     */
    private static int findLargestPowerOfTwo(BigInteger mergedSize, BigInteger mergedStart) {
        int bits = 0;
        BigInteger tmp = mergedSize;
        while (tmp.compareTo(BigInteger.ONE) > 0) {
            tmp = tmp.shiftRight(1);
            bits++;
        }

        // 从最大可能的位数向下找，确保对齐
        while (bits >= 0) {
            BigInteger size = BigInteger.ONE.shiftLeft(bits);
            if (size.compareTo(mergedSize) > 0) {
                bits--;
                continue;
            }
            BigInteger alignmentMask = size.subtract(BigInteger.ONE);
            if (!mergedStart.and(alignmentMask).equals(BigInteger.ZERO)) {
                bits--;
                continue;
            }
            break;
        }

        // 如果找不到合适的对齐，返回最小单位（单个地址）
        return Math.max(bits, 0);
    }

    private static void splitRemainder(java.util.Collection<IPv6Block> free, BigInteger start, BigInteger end) {
        if (start.compareTo(end) > 0) return;

        BigInteger remainStart = start;
        BigInteger previousStart = null;
        int iterationCount = 0;
        final int MAX_ITERATIONS = 1000000; // 防止无限循环的安全限制

        while (remainStart.compareTo(end) <= 0) {
            // 安全检查：防止无限循环
            iterationCount++;
            if (iterationCount > MAX_ITERATIONS) {
                // 记录错误并终止，避免程序挂起
                System.err.println("splitRemainder 达到最大迭代次数限制，强制终止。start=" + start + ", end=" + end + ", remainStart=" + remainStart);
                break;
            }
            
            // 安全检查：确保 progress
            if (previousStart != null && remainStart.equals(previousStart)) {
                System.err.println("splitRemainder 无进展，强制终止。remainStart=" + remainStart + ", bits=0");
                break;
            }
            previousStart = remainStart;

            BigInteger remainingSize = end.subtract(remainStart).add(BigInteger.ONE);
            if (remainingSize.compareTo(BigInteger.ZERO) <= 0) break;

            int bits = 0;
            BigInteger tmp = remainingSize;
            while (tmp.compareTo(BigInteger.ONE) > 0) {
                tmp = tmp.shiftRight(1);
                bits++;
            }

            while (bits >= 0) {
                BigInteger size = BigInteger.ONE.shiftLeft(bits);
                if (size.compareTo(remainingSize) > 0) {
                    bits--;
                    continue;
                }
                // 检查起始地址对齐
                BigInteger alignmentMask = size.subtract(BigInteger.ONE);
                if (!remainStart.and(alignmentMask).equals(BigInteger.ZERO)) {
                    bits--;
                    continue;
                }
                break;
            }

            // 确保 bits 不为负
            if (bits < 0) bits = 0;

            BigInteger blockSize = BigInteger.ONE.shiftLeft(bits);
            
            // 安全检查：blockSize 必须大于 0
            if (blockSize.compareTo(BigInteger.ZERO) <= 0) {
                System.err.println("splitRemainder blockSize 为 0 或负数，强制终止。bits=" + bits);
                break;
            }
            
            // 安全检查：blockSize 不能超过剩余大小
            if (blockSize.compareTo(remainingSize) > 0) {
                blockSize = remainingSize;
                bits = 0;
            }

            free.add(new IPv6Block(remainStart, 128 - bits));
            remainStart = remainStart.add(blockSize);
        }
    }

    // ---------- IP 范围转 CIDR ----------

    /** 最大允许生成的 CIDR 块数量 */
    private static final int MAX_CIDR_BLOCKS = 10000;

    /**
     * 给定起始 IPv6 和结束 IPv6 地址，计算覆盖该地址段的最小 CIDR 块列表
     */
    public static List<String> ipRangeToCidr(String startIp, String endIp) {
        BigInteger start = parseIPv6(startIp);
        BigInteger end = parseIPv6(endIp);
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException("起始 IP 不能大于结束 IP");
        }

        List<String> result = new ArrayList<>();

        // 特殊情况：整个 IPv6 地址空间
        if (start.equals(BigInteger.ZERO) && end.equals(new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16))) {
            result.add("::/0");
            return result;
        }

        BigInteger current = start;
        while (current.compareTo(end) <= 0) {
            // 检查任务是否已取消
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("IP 范围转 CIDR 计算已取消");
            }
            
            // 检查结果数量是否超过限制
            if (result.size() >= MAX_CIDR_BLOCKS) {
                throw new IllegalArgumentException(
                    "生成的 CIDR 块数量超过限制（最大 " + MAX_CIDR_BLOCKS + " 个）。" +
                    "请缩小 IP 范围或使用分页模式。");
            }
            
            // 计算当前地址对齐到最大的 CIDR 块
            int maxPrefix = getMaxPrefixForAlignment(current, end);
            result.add(formatCompressed(current) + "/" + maxPrefix);
            current = current.add(BigInteger.ONE.shiftLeft(128 - maxPrefix));
        }

        return result;
    }

    /**
     * 根据起始地址和对端地址确定最大可用前缀
     * 返回能覆盖从 start 到 end 的最大范围的前缀（数值最大，范围最小）
     */
    private static int getMaxPrefixForAlignment(BigInteger start, BigInteger end) {
        // 计算范围大小
        BigInteger range = end.subtract(start).add(BigInteger.ONE);
        
        // 找到能覆盖这个范围的最小块大小（2的幂）
        int blockSizeBits = range.bitLength();
        // 如果范围是2的幂，需要减1
        if (range.bitCount() == 1 && !range.equals(BigInteger.ONE)) {
            blockSizeBits--;
        }
        
        // 计算对应的前缀
        int prefix = 128 - blockSizeBits;
        
        // 确保前缀在有效范围内
        if (prefix < 0) prefix = 0;
        
        // 检查是否对齐且块不超出范围；若当前块过大，增大前缀（缩小块）直到满足条件
        while (prefix <= 128) {
            int shift = 128 - prefix;
            BigInteger blockSize;
            
            // 处理特殊情况：prefix=0 时，整个地址空间
            if (shift >= 128) {
                // 覆盖整个地址空间：仅当 start=0 且 end=最大值时有效（入口处已单独处理），
                // 此处直接尝试更小的块
                prefix++;
                continue;
            }
            
            blockSize = BigInteger.ONE.shiftLeft(shift);
            
            // 检查块是否对齐
            if (start.and(blockSize.subtract(BigInteger.ONE)).equals(BigInteger.ZERO)) {
                // 对齐了，检查块是否超出范围
                BigInteger blockEnd = start.add(blockSize).subtract(BigInteger.ONE);
                if (blockEnd.compareTo(end) <= 0) {
                    return prefix;
                }
            }
            
            prefix++;
        }
        
        // 单地址（/128）始终满足 start <= end，正常流程不会走到这里
        return 128;
    }

    // ---------- 地址解析 ----------

    public static String parseIPv6Details(String addr) {
        BigInteger ip = parseIPv6(addr);
        StringBuilder sb = new StringBuilder();
        sb.append("IPv6 地址    : ").append(addr).append("\n");
        sb.append("完整展开     : ").append(formatFull(ip)).append("\n");
        sb.append("压缩格式     : ").append(formatCompressed(ip)).append("\n");
        sb.append("地址类型     : ").append(getAddressType(ip)).append("\n");
        sb.append("链路本地     : ").append(isLinkLocal(ip) ? "是" : "否").append("\n");
        sb.append("多播地址     : ").append(isMulticast(ip) ? "是" : "否").append("\n");
        sb.append("十进制数值   : ").append(ip).append("\n");
        
        // IPv4 映射地址检测
        if (isIPv4MappedAddress(ip)) {
            sb.append("IPv4 映射地址 : ").append(formatIPv4MappedAddress(ip)).append("\n");
            sb.append("映射的 IPv4   : ").append(getIPv4FromMappedAddress(ip)).append("\n");
        }
        
        return sb.toString();
    }

    // ---------- EUI-64 地址生成 ----------

    /**
     * 根据 MAC 地址生成 EUI-64 接口标识
     * @param mac MAC 地址（支持格式：XX:XX:XX:XX:XX:XX, XX-XX-XX-XX-XX-XX, XXXXXXXXXXXX）
     * @return EUI-64 接口标识（8 字节，格式：XXXX:XXXX:XXXX:XXXX）
     */
    public static String generateEUI64(String mac) {
        String cleanMac = mac.replaceAll("[:-]", "").toLowerCase();
        if (cleanMac.length() != 12) {
            throw new IllegalArgumentException("无效的 MAC 地址格式: " + mac);
        }
        
        // 将 MAC 地址转换为字节数组
        byte[] macBytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            macBytes[i] = (byte) Integer.parseInt(cleanMac.substring(i * 2, (i + 1) * 2), 16);
        }
        
        // 翻转第 2 位（U/L 位）- 将第一个字节的第 1 位（从 0 开始）设为 1
        macBytes[0] = (byte) (macBytes[0] ^ 0x02);
        
        // 构建 EUI-64 字节数组：[mac前3字节][FF][FE][mac后3字节]
        byte[] eui64Bytes = new byte[8];
        System.arraycopy(macBytes, 0, eui64Bytes, 0, 3);
        eui64Bytes[3] = (byte) 0xFF;
        eui64Bytes[4] = (byte) 0xFE;
        System.arraycopy(macBytes, 3, eui64Bytes, 5, 3);
        
        // 转换为十六进制字符串
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0 && i % 2 == 0) sb.append(":");
            sb.append(String.format("%02x", eui64Bytes[i]));
        }
        
        return sb.toString();
    }

    /**
     * 根据 MAC 地址和 IPv6 前缀生成完整的 IPv6 地址（使用 EUI-64）
     * @param prefix IPv6 前缀（如 2001:db8::/64）
     * @param mac MAC 地址
     * @return 完整的 IPv6 地址
     */
    public static String generateIPv6FromMAC(String prefix, String mac) {
        String[] prefixParts = prefix.split("/");
        if (prefixParts.length != 2) {
            throw new IllegalArgumentException("无效的前缀格式: " + prefix);
        }
        
        String networkAddr = prefixParts[0];
        int prefixLen = Integer.parseInt(prefixParts[1]);
        
        if (prefixLen > 64) {
            throw new IllegalArgumentException("前缀长度必须 <= 64 才能生成 EUI-64 地址");
        }
        
        BigInteger network = parseIPv6(networkAddr);
        network = getNetworkAddress(network, prefixLen);
        
        // 生成 EUI-64 接口标识
        String eui64 = generateEUI64(mac);
        // EUI-64 只有 64 位（4组），需要转换为完整的 IPv6 地址格式（添加前缀 ::）
        BigInteger interfaceId = parseIPv6("::" + eui64);
        
        // 组合网络地址和接口标识
        // 网络地址左移 (128 - prefixLen) 位，然后与接口标识的低 (128 - prefixLen) 位进行 OR
        int hostBits = 128 - prefixLen;
        BigInteger ip = network.or(interfaceId.and(BigInteger.ONE.shiftLeft(hostBits).subtract(BigInteger.ONE)));
        
        return formatCompressed(ip);
    }

    // ---------- IPv6 多播地址解析 ----------

    /**
     * 解析 IPv6 多播地址的详细信息
     * @param addr IPv6 多播地址
     * @return 多播地址详细信息
     */
    public static String parseMulticastAddress(String addr) {
        BigInteger ip = parseIPv6(addr);
        
        if (!isMulticast(ip)) {
            throw new IllegalArgumentException("不是多播地址: " + addr);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("多播地址     : ").append(addr).append("\n");
        sb.append("完整展开     : ").append(formatFull(ip)).append("\n");
        sb.append("压缩格式     : ").append(formatCompressed(ip)).append("\n");
        
        // 解析范围 (Scope) - 第 13-16 位（0-based 12-15）
        int scope = ip.shiftRight(112).intValue() & 0x0F;
        String scopeName = switch(scope) {
            case 0 -> "保留";
            case 1 -> "节点本地 (Node-Local)";
            case 2 -> "链路本地 (Link-Local)";
            case 3 -> "子网本地 (Subnet-Local)";
            case 4 -> "管理本地 (Admin-Local)";
            case 5 -> "站点本地 (Site-Local)";
            case 8 -> "组织本地 (Organization-Local)";
            case 14 -> "全局 (Global)";
            case 15 -> "保留";
            default -> "未知范围 (0x" + Integer.toHexString(scope) + ")";
        };
        sb.append("范围 (Scope)  : 0x").append(Integer.toHexString(scope)).append(" - ").append(scopeName).append("\n");
        
        // 解析标志 (Flags) - 第 9-12 位（0-based 8-11）
        int flags = ip.shiftRight(116).intValue() & 0x0F;
        sb.append("标志 (Flags)  : 0x").append(Integer.toHexString(flags)).append("\n");
        
        // 详细标志解析
        // 多播标志 4 位为 0|R|P|T（权重 R=0x4, P=0x2, T=0x1），见 RFC 4291/3306/3956
        boolean isTransient = (flags & 0x01) != 0;
        boolean isPrefixBased = (flags & 0x02) != 0;
        sb.append("  • 短暂标志 (T) : ").append(isTransient ? "是 (临时地址)" : "否 (永久地址)").append("\n");
        sb.append("  • 前缀标志 (P) : ").append(isPrefixBased ? "是 (基于前缀, RFC 3306)" : "否 (非基于前缀)").append("\n");
        
        // 解析组 ID (Group ID) - 低 112 位
        BigInteger groupId = ip.and(new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16));
        sb.append("组 ID (Group ID) : ").append(groupId.toString(16)).append("\n");
        
        // 知名组地址识别
        if (groupId.compareTo(BigInteger.ZERO) == 0) {
            sb.append("组类型       : 保留组地址\n");
        } else if (groupId.compareTo(BigInteger.ONE) == 0) {
            sb.append("组类型       : 所有节点组 (All Nodes)\n");
        } else if (groupId.compareTo(BigInteger.valueOf(2)) == 0) {
            sb.append("组类型       : 所有路由器组 (All Routers)\n");
        } else if (groupId.compareTo(BigInteger.valueOf(3)) == 0) {
            sb.append("组类型       : DHCPv6 中继代理组\n");
        } else if (groupId.compareTo(BigInteger.valueOf(4)) == 0) {
            sb.append("组类型       : DHCPv6 服务器组\n");
        } else if (groupId.compareTo(BigInteger.valueOf(5)) == 0) {
            sb.append("组类型       : 邻居发现组\n");
        } else if (groupId.compareTo(BigInteger.valueOf(6)) == 0) {
            sb.append("组类型       : 无状态地址自动配置组\n");
        } else if (groupId.compareTo(BigInteger.valueOf(9)) == 0) {
            sb.append("组类型       : 多点播送 DNS 组\n");
        } else if (groupId.compareTo(BigInteger.valueOf(10)) == 0) {
            sb.append("组类型       : 网络时间协议组\n");
        } else {
            if (groupId.bitLength() <= 32) {
                sb.append("组类型       : IPv4 组播地址映射 (RFC 2710)\n");
            } else {
                sb.append("组类型       : 应用程序专用组\n");
            }
        }
        
        return sb.toString();
    }

    // ---------- DHCPv6 前缀委派 ----------

    /**
     * 计算 DHCPv6 前缀委派信息
     * @param delegatedPrefix 委派的前缀（如 2001:db8:100::/48）
     * @param prefixLength 每个子网的前缀长度（如 64）
     * @return 前缀委派详细信息
     */
    public static String calculatePrefixDelegation(String delegatedPrefix, int prefixLength) {
        String[] parts = delegatedPrefix.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("无效的前缀格式: " + delegatedPrefix);
        }
        
        String networkAddr = parts[0];
        int delegatedLen = Integer.parseInt(parts[1]);
        
        if (delegatedLen < 0 || delegatedLen > 128) {
            throw new IllegalArgumentException("委派前缀长度必须在 0-128 之间");
        }
        if (prefixLength < delegatedLen || prefixLength > 128) {
            throw new IllegalArgumentException("子网前缀长度必须在 " + delegatedLen + "-128 之间");
        }
        
        BigInteger network = parseIPv6(networkAddr);
        network = getNetworkAddress(network, delegatedLen);
        
        // 计算可委派的子网数量
        BigInteger subnetCount = BigInteger.ONE.shiftLeft(prefixLength - delegatedLen);
        
        // 计算每个子网的大小
        BigInteger subnetSize = BigInteger.ONE.shiftLeft(128 - prefixLength);
        
        // 计算第一个和最后一个子网
        IPv6Block firstSubnet = new IPv6Block(network, prefixLength);
        BigInteger lastNetwork = network.add(subnetCount.subtract(BigInteger.ONE).multiply(subnetSize));
        IPv6Block lastSubnet = new IPv6Block(lastNetwork, prefixLength);
        
        StringBuilder sb = new StringBuilder();
        sb.append("委派前缀     : ").append(formatCompressed(network)).append("/").append(delegatedLen).append("\n");
        sb.append("子网前缀长度 : /").append(prefixLength).append("\n");
        sb.append("可委派子网数 : ").append(subnetCount).append("\n");
        sb.append("每个子网大小 : ").append(subnetSize).append(" 地址\n");
        sb.append("第一个子网   : ").append(firstSubnet.toString()).append("\n");
        sb.append("最后一个子网 : ").append(lastSubnet.toString()).append("\n");
        
        // 计算地址范围
        IPv6Block delegatedBlock = new IPv6Block(network, delegatedLen);
        sb.append("委派地址范围 : ").append(formatCompressed(delegatedBlock.network))
          .append(" ~ ").append(formatCompressed(delegatedBlock.last)).append("\n");
        
        return sb.toString();
    }

    // ---------- IPv4 映射 IPv6 地址 ----------

    /**
     * 判断是否为 IPv4 映射 IPv6 地址（::ffff:0:0/96）
     * @param addr IPv6 地址
     * @return 是否为 IPv4 映射地址
     */
    public static boolean isIPv4MappedAddress(BigInteger addr) {
        // ::ffff:0:0/96 的掩码
        BigInteger mask = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFF00000000", 16);
        BigInteger network = new BigInteger("00000000000000000000FFFF00000000", 16);
        return addr.and(mask).equals(network);
    }

    /**
     * 判断是否为 IPv4 兼容 IPv6 地址（::/96，已废弃）
     * @param addr IPv6 地址
     * @return 是否为 IPv4 兼容地址
     */
    public static boolean isIPv4CompatibleAddress(BigInteger addr) {
        BigInteger mask = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFF00000000", 16);
        return addr.and(mask).equals(BigInteger.ZERO);
    }

    /**
     * 从 IPv4 映射 IPv6 地址中提取 IPv4 地址
     * @param addr IPv6 地址
     * @return IPv4 地址（如 192.0.2.1）
     */
    public static String getIPv4FromMappedAddress(BigInteger addr) {
        if (!isIPv4MappedAddress(addr) && !isIPv4CompatibleAddress(addr)) {
            throw new IllegalArgumentException("不是 IPv4 映射或兼容的 IPv6 地址");
        }
        
        // 获取低 32 位
        long ipv4Long = addr.and(new BigInteger("FFFFFFFF", 16)).longValue();
        
        // 转换为点分十进制格式
        int octet1 = (int) ((ipv4Long >> 24) & 0xFF);
        int octet2 = (int) ((ipv4Long >> 16) & 0xFF);
        int octet3 = (int) ((ipv4Long >> 8) & 0xFF);
        int octet4 = (int) (ipv4Long & 0xFF);
        
        return String.format("%d.%d.%d.%d", octet1, octet2, octet3, octet4);
    }

    /**
     * 将 IPv4 地址转换为 IPv4 映射 IPv6 地址
     * @param ipv4 IPv4 地址
     * @return IPv4 映射 IPv6 地址（如 ::ffff:192.0.2.1）
     */
    public static String formatIPv4MappedAddress(String ipv4) {
        // 解析 IPv4 地址
        String[] octets = ipv4.split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("无效的 IPv4 地址: " + ipv4);
        }
        
        long ipv4Long = 0;
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(octets[i]);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("无效的 IPv4 地址: " + ipv4);
            }
            ipv4Long = (ipv4Long << 8) | octet;
        }
        
        // 构建 IPv6 地址：::ffff: + IPv4 地址
        BigInteger network = new BigInteger("00000000000000000000FFFF00000000", 16);
        BigInteger ip = network.or(BigInteger.valueOf(ipv4Long));
        
        return formatCompressed(ip);
    }

    /**
     * 将 IPv6 地址格式化为 IPv4 映射地址表示（如果是）
     * @param addr IPv6 地址
     * @return IPv4 映射地址表示（如 ::ffff:192.0.2.1）或原始格式
     */
    public static String formatIPv4MappedAddress(BigInteger addr) {
        if (isIPv4MappedAddress(addr)) {
            String ipv4 = getIPv4FromMappedAddress(addr);
            return "::ffff:" + ipv4;
        } else if (isIPv4CompatibleAddress(addr)) {
            String ipv4 = getIPv4FromMappedAddress(addr);
            return "::" + ipv4;
        }
        return formatCompressed(addr);
    }
}
