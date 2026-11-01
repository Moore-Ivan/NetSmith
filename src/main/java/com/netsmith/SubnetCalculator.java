package com.netsmith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * IPv4 子网计算器核心类
 * 支持：网段详情、等长子网划分、VLSM、超网、路由汇总、IP范围转CIDR
 */
public class SubnetCalculator {

    // ---------- 基础转换 ----------

    /** IP转长整型（不允许八位组带前导零，如 "01"——避免与部分工具的八进制解释产生歧义） */
    public static long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) throw new IllegalArgumentException("无效IP格式: " + ip);
        long result = 0;
        for (int i = 0; i < 4; i++) {
            if (parts[i].length() > 1 && parts[i].startsWith("0")) {
                throw new IllegalArgumentException("无效IP段（八位组不允许前导零）: " + parts[i]);
            }
            int octet = Integer.parseInt(parts[i]);
            if (octet < 0 || octet > 255) throw new IllegalArgumentException("无效IP段: " + octet);
            result = (result << 8) | octet;
        }
        return result & 0xFFFFFFFFL;
    }

    /** 长整型转IP */
    public static String longToIp(long ip) {
        ip = ip & 0xFFFFFFFFL;
        return ((ip >> 24) & 0xFF) + "." +
               ((ip >> 16) & 0xFF) + "." +
               ((ip >> 8) & 0xFF) + "." +
               (ip & 0xFF);
    }

    /** 获取子网掩码 */
    public static long getSubnetMask(int prefix) {
        if (prefix <= 0) return 0L;
        if (prefix >= 32) return 0xFFFFFFFFL;
        return (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
    }

    /** 获取广播地址 */
    public static long getBroadcastAddress(long network, int prefix) {
        return network | getWildcardMask(prefix);
    }

    /** 获取网络地址 */
    public static long getNetworkAddress(long ip, int prefix) {
        return ip & getSubnetMask(prefix);
    }

    /** 获取反掩码（通配符掩码） */
    public static long getWildcardMask(int prefix) {
        return ~getSubnetMask(prefix) & 0xFFFFFFFFL;
    }

    /**
     * 安全的位移操作，防止溢出
     * @param bits 位移位数（0-63）
     * @return 1L << bits，如果 bits >= 64 返回 Long.MAX_VALUE
     */
    private static long safeShiftLeft(int bits) {
        if (bits <= 0) return 1L;
        if (bits >= 63) return Long.MAX_VALUE;
        return 1L << bits;
    }

    /**
     * 安全的位移操作，防止溢出
     * @param bits 位移位数（0-63）
     * @param defaultValue 当 bits 超出范围时的默认值
     * @return 1L << bits，如果 bits >= 64 返回 defaultValue
     */
    private static long safeShiftLeft(int bits, long defaultValue) {
        if (bits <= 0) return 1L;
        if (bits >= 63) return defaultValue;
        return 1L << bits;
    }

    /** 可用主机数（prefix 合法范围 0-32） */
    public static long getUsableHostCount(int prefix) {
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("前缀必须在 0-32 之间: " + prefix);
        }
        if (prefix >= 32) return 1;
        if (prefix == 31) return 2;
        if (prefix == 0) return 0xFFFFFFFFL - 1;
        return safeShiftLeft(32 - prefix) - 2;
    }

    // ---------- 子网详情 ----------

    public static String getSubnetDetails(String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) throw new IllegalArgumentException("格式：IP/前缀");
        
        long ip = ipToLong(parts[0]);
        int prefix = Integer.parseInt(parts[1]);
        
        if (prefix < 0 || prefix > 32) throw new IllegalArgumentException("前缀 0-32");
        
        long network = getNetworkAddress(ip, prefix);
        long mask = getSubnetMask(prefix);
        long broadcast = getBroadcastAddress(network, prefix);
        long usable = getUsableHostCount(prefix);
        
        StringBuilder sb = new StringBuilder();
        sb.append("地址段：       ").append(parts[0]).append("/").append(prefix).append("\n");
        sb.append("网络地址：     ").append(longToIp(network)).append("\n");
        sb.append("广播地址：     ").append(longToIp(broadcast)).append("\n");
        sb.append("子网掩码：     ").append(longToIp(mask)).append("\n");
        sb.append("反掩码：       ").append(longToIp(getWildcardMask(prefix))).append("\n");
        sb.append("可用主机数：   ").append(usable).append("\n");
        return sb.toString();
    }

    // ---------- 等长子网划分 ----------

    public static class AddressBlock {
        public final long network;
        public final int prefix;
        public final long broadcast;
        public final long mask;
        
        public AddressBlock(long network, int prefix) {
            this.prefix = prefix;
            this.mask = getSubnetMask(prefix);
            this.network = getNetworkAddress(network, prefix);
            this.broadcast = getBroadcastAddress(this.network, prefix);
        }
        
        public long size() { 
            return safeShiftLeft(32 - prefix, 0x100000000L); 
        }
        
        public long usableHosts() { return getUsableHostCount(prefix); }
        
        @Override
        public String toString() {
            return longToIp(network) + "/" + prefix;
        }
    }

    /** 空闲块比较器：按块大小升序，大小相同按起始地址升序（用于 VLSM 的 bestFit 查找） */
    private static final Comparator<AddressBlock> FREE_BLOCK_COMPARATOR = (a, b) -> {
        int c = Long.compare(a.size(), b.size());
        if (c != 0) return c;
        return Long.compare(a.network, b.network);
    };

    /** 按子网数量划分（允许/31、/32子网） */
    public static List<AddressBlock> subnetByCount(String majorCidr, int subnetCount) {
        String[] parts = majorCidr.split("/");
        long majorNetwork = getNetworkAddress(ipToLong(parts[0]), Integer.parseInt(parts[1]));
        int majorPrefix = Integer.parseInt(parts[1]);

        int bits = 0;
        while (bits < 32 && safeShiftLeft(bits) < subnetCount) {bits++;}
        int newPrefix = majorPrefix + bits;
        if (newPrefix > 32) throw new IllegalArgumentException("子网数量过多，前缀将超过/32");

        long subnetSize = safeShiftLeft(32 - newPrefix, 0x100000000L);
        List<AddressBlock> subnets = new ArrayList<>();
        for (int i = 0; i < subnetCount; i++) {
            subnets.add(new AddressBlock(majorNetwork + (long)i * subnetSize, newPrefix));
        }
        return subnets;
    }

    /**
     * 子网划分结果（含总数信息）
     */
    public static class SubnetByHostsResult {
        public final List<AddressBlock> subnets;
        public final long totalCount;
        public final boolean truncated;
        public final int prefix;

        public SubnetByHostsResult(List<AddressBlock> subnets, long totalCount, boolean truncated, int prefix) {
            this.subnets = subnets;
            this.totalCount = totalCount;
            this.truncated = truncated;
            this.prefix = prefix;
        }
    }

    /** 按每子网所需主机数划分 */
    public static SubnetByHostsResult subnetByHosts(String majorCidr, int hostsPerSubnet) {
        String[] parts = majorCidr.split("/");
        long majorNetwork = getNetworkAddress(ipToLong(parts[0]), Integer.parseInt(parts[1]));
        int majorPrefix = Integer.parseInt(parts[1]);

        // 特殊处理：RFC 3021 支持 /31（2主机）和 /32（1主机）
        int newPrefix;
        if (hostsPerSubnet == 1) {
            newPrefix = 32; // /32 - 单个主机
        } else if (hostsPerSubnet == 2) {
            newPrefix = 31; // /31 - 点对点链路
        } else {
            int bits = 0;
            while (bits < 32 && safeShiftLeft(bits) - 2 < hostsPerSubnet) {bits++;}
            newPrefix = 32 - bits;
        }

        if (newPrefix < majorPrefix) throw new IllegalArgumentException("子网太大，无法容纳所需主机数");

        long subnetSize = safeShiftLeft(32 - newPrefix, 0x100000000L);
        long maxSubnets = safeShiftLeft(newPrefix - majorPrefix);
        int count = maxSubnets > 1024 ? 1024 : (int) maxSubnets;
        boolean truncated = maxSubnets > 1024;

        List<AddressBlock> subnets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            subnets.add(new AddressBlock(majorNetwork + (long)i * subnetSize, newPrefix));
        }
        return new SubnetByHostsResult(subnets, maxSubnets, truncated, newPrefix);
    }

    // ---------- VLSM 变长子网划分 ----------

    /** 根据主机数获取最小前缀 */
    public static int getPrefixForHosts(int hosts) {
        if (hosts < 1) {
            throw new IllegalArgumentException("主机数必须至少为 1");
        }
        if (hosts == 1) return 32;
        if (hosts == 2) return 31;
        // 计算需求大小并检查是否超过范围
        int bits = 0;
        while (bits < 32 && safeShiftLeft(bits) - 2 < hosts) bits++;
        int needPrefix = 32 - bits;
        long needSize = safeShiftLeft(32 - needPrefix, 0x100000000L);
        if (needSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("单个子网需求超过 2^31 地址");
        }
        return needPrefix;
    }

    /** VLSM 变长子网划分 */
    public static List<AddressBlock> vlsm(String majorCidr, int... hostCounts) {
        if (hostCounts.length == 0) {
            throw new IllegalArgumentException("请提供至少一个主机需求");
        }

        String[] parts = majorCidr.split("/");
        if (parts.length != 2) throw new IllegalArgumentException("无效的 CIDR 格式: " + majorCidr);

        long majorNetwork = getNetworkAddress(ipToLong(parts[0]), Integer.parseInt(parts[1]));
        int majorPrefix = Integer.parseInt(parts[1]);
        // 安全计算主网络大小，防止位移溢出
        long majorSize = safeShiftLeft(32 - majorPrefix, 0x100000000L);

        List<Integer> hosts = new ArrayList<>();
        long totalRequired = 0;
        for (int h : hostCounts) {
            if (h < 1) throw new IllegalArgumentException("主机数必须至少为 1");
            hosts.add(h);
            int needPrefix = getPrefixForHosts(h);
            // 使用安全位移操作
            long needSize = safeShiftLeft(32 - needPrefix, 0x100000000L);
            totalRequired += needSize;
        }

        if (totalRequired > majorSize) {
            throw new IllegalArgumentException("总需求 (" + totalRequired + " 地址) 超过主网络容量 (" + majorSize + " 地址)");
        }

        hosts.sort(Collections.reverseOrder());

        List<AddressBlock> allocated = new ArrayList<>();
        // 空闲块使用按 (大小, 起始地址) 排序的 TreeSet 管理，
        // bestFit 查找（大小 >= 需求的最小块）从 O(n) 降至 O(log n)
        TreeSet<AddressBlock> free = new TreeSet<>(FREE_BLOCK_COMPARATOR);
        free.add(new AddressBlock(majorNetwork, majorPrefix));

        for (int required : hosts) {
            // 检查任务是否已取消
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("VLSM 计算已取消");
            }
            
            int needPrefix = getPrefixForHosts(required);
            long needSize = safeShiftLeft(32 - needPrefix, 0x100000000L);

            // bestFit：找到大小 >= needSize 的最小空闲块（TreeSet.ceiling 为 O(log n)）
            AddressBlock searchKey = new AddressBlock(0, needPrefix); // size() == needSize
            AddressBlock block = free.ceiling(searchKey);

            if (block == null) {
                throw new IllegalArgumentException("无法分配 " + required + " 主机子网，空间碎片化严重");
            }

            AddressBlock subnet = new AddressBlock(block.network, needPrefix);
            allocated.add(subnet);
            free.remove(block);

            long subEnd = subnet.broadcast;
            if (subEnd < block.broadcast) {
                splitRemainder(free, subEnd + 1, block.broadcast);
            }
        }
        return allocated;
    }

    private static void splitRemainder(java.util.Collection<AddressBlock> free, long start, long end) {
        if (start > end) return;
        
        long remainStart = start;
        while (remainStart <= end) {
            long remainingSize = end - remainStart + 1;
            if (remainingSize <= 0) break;
            
            int bits = 0;
            long tmp = remainingSize;
            while (tmp > 1) { tmp >>= 1; bits++; }
            
            while (bits >= 0) {
                long size = safeShiftLeft(bits, 0x100000000L);
                if (size > remainingSize) {
                    bits--;
                    continue;
                }
                if ((remainStart & (size - 1)) != 0) {
                    bits--;
                    continue;
                }
                break;
            }
            
            if (bits < 0) {
                bits = 0;
            }
            
            long blockSize = safeShiftLeft(bits, 0x100000000L);
            free.add(new AddressBlock(remainStart, 32 - bits));
            remainStart += blockSize;
        }
    }

    // ---------- 超网拆分 ----------

    /**
     * 超网拆分 - 将一个大网段拆分为多个小网段
     * @param majorCidr 主网络 CIDR
     * @param newPrefix 目标前缀（必须大于主网络前缀）
     * @return 子网列表（最多1024个）
     */
    public static List<AddressBlock> splitSupernet(String majorCidr, int newPrefix) {
        String[] parts = majorCidr.split("/");
        long majorNetwork = getNetworkAddress(ipToLong(parts[0]), Integer.parseInt(parts[1]));
        int majorPrefix = Integer.parseInt(parts[1]);

        if (newPrefix < majorPrefix) throw new IllegalArgumentException("新前缀必须大于主网络前缀");
        if (newPrefix > 32) throw new IllegalArgumentException("前缀不能超过/32");
        
        int bits = newPrefix - majorPrefix;
        
        // 计算子网总数，截断到 1024（与分页函数保持一致）
        long totalSubnets = safeShiftLeft(bits);
        int count = (int) Math.min(totalSubnets, 1024L);

        List<AddressBlock> subnets = new ArrayList<>();
        long subnetSize = safeShiftLeft(32 - newPrefix, 0x100000000L);
        for (int i = 0; i < count; i++) {
            subnets.add(new AddressBlock(majorNetwork + (long)i * subnetSize, newPrefix));
        }
        return subnets;
    }

    /**
     * 分页获取超网拆分结果
     * @param majorCidr 主网络 CIDR
     * @param newPrefix 目标前缀
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页后的子网列表
     */
    public static List<AddressBlock> splitSupernetPaged(String majorCidr, int newPrefix, int pageNum, int pageSize) {
        String[] parts = majorCidr.split("/");
        long majorNetwork = getNetworkAddress(ipToLong(parts[0]), Integer.parseInt(parts[1]));
        int majorPrefix = Integer.parseInt(parts[1]);

        if (newPrefix < majorPrefix) throw new IllegalArgumentException("新前缀必须大于主网络前缀");
        if (newPrefix > 32) throw new IllegalArgumentException("前缀不能超过/32");

        int bits = newPrefix - majorPrefix;
        long totalSubnets = safeShiftLeft(bits);

        long start = (pageNum - 1) * (long) pageSize;
        long end = Math.min(start + pageSize, totalSubnets);

        if (start >= totalSubnets) {
            return new ArrayList<>();
        }

        List<AddressBlock> subnets = new ArrayList<>();
        long subnetSize = safeShiftLeft(32 - newPrefix, 0x100000000L);
        for (long i = start; i < end; i++) {
            subnets.add(new AddressBlock(majorNetwork + i * subnetSize, newPrefix));
        }
        return subnets;
    }

    /**
     * 获取超网拆分的子网总数
     */
    public static long getSupernetSplitCount(String majorCidr, int newPrefix) {
        return getSubnetCount(majorCidr, newPrefix);
    }

    /**
     * 获取子网总数（用于分页）
     */
    public static long getSubnetCount(String majorCidr, int newPrefix) {
        String[] parts = majorCidr.split("/");
        int majorPrefix = Integer.parseInt(parts[1]);
        if (newPrefix < majorPrefix || newPrefix > 32) return 0;
        int bits = newPrefix - majorPrefix;
        return safeShiftLeft(bits);
    }

    // ---------- 网络规划 ----------

    public static class NetworkPlan {
        public final int hosts;
        public final int prefix;
        public final String subnet;
        
        public NetworkPlan(int hosts, int prefix, String subnet) {
            this.hosts = hosts;
            this.prefix = prefix;
            this.subnet = subnet;
        }
    }

    public static List<NetworkPlan> planNetwork(String majorCidr, int... hostRequirements) {
        List<AddressBlock> blocks = vlsm(majorCidr, hostRequirements);
        List<NetworkPlan> plan = new ArrayList<>();
        for (AddressBlock block : blocks) {
            int hosts = (int) getUsableHostCount(block.prefix);
            plan.add(new NetworkPlan(hosts, block.prefix, block.toString()));
        }
        return plan;
    }

    // ---------- 路由汇总（超网） ----------

    /**
     * 将多个 CIDR 网段汇总为最小的超网
     */
    public static String summarize(String... cidrs) {
        if (cidrs.length == 0) return null;

        List<AddressBlock> blocks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String c : cidrs) {
            String[] p = c.split("/");
            if (p.length != 2) throw new IllegalArgumentException("无效格式: " + c);
            long ip = ipToLong(p[0]);
            int prefix = Integer.parseInt(p[1]);
            if (prefix < 0 || prefix > 32) throw new IllegalArgumentException("无效前缀: " + prefix);
            AddressBlock block = new AddressBlock(ip, prefix);
            // 按网络地址+前缀去重，避免相同 CIDR 重复输入导致异常
            String key = block.network + "/" + prefix;
            if (!seen.add(key)) continue;
            blocks.add(block);
        }

        if (blocks.size() == 1) return blocks.get(0).toString();

        long minNetwork = Long.MAX_VALUE;
        long maxBroadcast = Long.MIN_VALUE;
        for (AddressBlock block : blocks) {
            if (block.network < minNetwork) minNetwork = block.network;
            if (block.broadcast > maxBroadcast) maxBroadcast = block.broadcast;
        }

        long xor = 0;
        long firstNetwork = blocks.get(0).network;
        for (AddressBlock block : blocks) {
            xor |= firstNetwork ^ block.network;
        }
        int commonPrefix = 32;
        long temp = xor;
        while (temp != 0) {
            temp >>>= 1;
            commonPrefix--;
        }

        // XOR 仅基于网络地址计算，遇到嵌套或混合前缀（如 10.0.0.0/8 与 10.0.0.0/24）时
        // 候选超网会过小而无法包含所有网段。逐级缩小前缀（放大超网）直到包含全部网段；
        // /0 覆盖整个地址空间，因此循环必然能找到结果。
        AddressBlock superBlock = new AddressBlock(minNetwork, commonPrefix);
        while (commonPrefix > 0) {
            boolean allContained = true;
            for (AddressBlock block : blocks) {
                if (block.network < superBlock.network || block.broadcast > superBlock.broadcast) {
                    allContained = false;
                    break;
                }
            }
            if (allContained) break;
            commonPrefix--;
            superBlock = new AddressBlock(minNetwork, commonPrefix);
        }

        return superBlock.toString();
    }

    // ---------- IP 范围转 CIDR ----------

    /** 最大允许生成的 CIDR 块数量 */
    private static final int MAX_CIDR_BLOCKS = 10000;

    /**
     * 给定起始 IP 和结束 IP，计算包含该范围的最小 CIDR 块列表
     * 支持起始IP为0.0.0.0的情况（如范围0.0.0.0-255.255.255.255应生成/0）
     */
    public static List<String> ipRangeToCidr(String startIp, String endIp) {
        long start = ipToLong(startIp);
        long end = ipToLong(endIp);
        if (start > end) throw new IllegalArgumentException("起始 IP 不能大于结束 IP");

        List<String> result = new ArrayList<>();
        
        if (start == 0 && end == 0xFFFFFFFFL) {
            result.add("0.0.0.0/0");
            return result;
        }

        while (start <= end) {
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
            
            int maxPrefix;
            
            if (start == 0) {
                long rangeSize = end - start + 1;
                int highestBitPos = 63 - Long.numberOfLeadingZeros(rangeSize);
                maxPrefix = 32 - highestBitPos;
            } else {
                int trailingZeros = Long.numberOfTrailingZeros(start);
                maxPrefix = 32 - trailingZeros;
            }

            while (maxPrefix < 32 &&
                   (start | getWildcardMask(maxPrefix)) > end) {
                maxPrefix++;
            }

            result.add(longToIp(start) + "/" + maxPrefix);
            // 使用安全位移操作
            start += safeShiftLeft(32 - maxPrefix, 0x1000000000L);
        }
        return result;
    }

    // ---------- 子网反向查询 ----------

    /**
     * 给定 IP 地址和子网掩码，返回 CIDR 格式
     */
    public static String getCidrFromMask(String ip, String maskStr) {
        long ipLong = ipToLong(ip);
        long mask = ipToLong(maskStr);
        
        long invMask = ~mask & 0xFFFFFFFFL;
        if (invMask != 0 && (invMask & (invMask + 1)) != 0) {
            throw new IllegalArgumentException("非法的子网掩码（1 位不连续）: " + maskStr);
        }
        
        int prefix = Integer.bitCount((int) mask);
        long network = ipLong & mask;
        return longToIp(network) + "/" + prefix;
    }

    /**
     * 检查 IP 是否在 CIDR 范围内
     */
    public static boolean isIpInCidr(String ip, String cidr) {
        String[] parts = cidr.split("/");
        long network = ipToLong(parts[0]);
        int prefix = Integer.parseInt(parts[1]);
        long ipLong = ipToLong(ip);
        return getNetworkAddress(ipLong, prefix) == getNetworkAddress(network, prefix);
    }

    /**
     * IP 地址解析详情
     */
    public static String parseIPDetails(String ip) {
        long ipLong = ipToLong(ip);
        StringBuilder sb = new StringBuilder();
        sb.append("IP 地址   : ").append(ip).append("\n");
        sb.append("十进制数值 : ").append(ipLong).append("\n");
        sb.append("二进制表示 : ").append(String.format("%32s", Long.toBinaryString(ipLong)).replace(' ', '0')).append("\n");
        return sb.toString();
    }

    /** 重叠检测允许的最大 CIDR 数量：检测为 O(n²) 复杂度，超限会拖垮 UI 并可能 OOM */
    public static final int MAX_OVERLAP_INPUT = 1000;

    /**
     * 检测多个 CIDR 是否存在重叠
     */
    public static String detectOverlaps(String... cidrs) {
        if (cidrs.length > MAX_OVERLAP_INPUT) {
            throw new IllegalArgumentException(
                "CIDR 数量过多（" + cidrs.length + " 个），重叠检测为 O(n²) 复杂度，请控制在 "
                + MAX_OVERLAP_INPUT + " 个以内");
        }
        // 验证所有 CIDR 的有效性，并在错误消息中标注位置，便于用户定位第几个输入有误
        for (int i = 0; i < cidrs.length; i++) {
            try {
                CidrValidator.validateCidr(cidrs[i]);
            } catch (ValidationError e) {
                throw new ValidationError(e.getField(), "第 " + (i + 1) + " 个 CIDR 无效: " + e.getMessage());
            }
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("重叠检测结果:\n");
        sb.append("────────────────────────────────────\n");
        
        boolean hasOverlap = false;
        for (int i = 0; i < cidrs.length; i++) {
            for (int j = i + 1; j < cidrs.length; j++) {
                if (isOverlap(cidrs[i], cidrs[j])) {
                    sb.append(String.format("  ✓ 重叠: %s 与 %s\n", cidrs[i], cidrs[j]));
                    hasOverlap = true;
                }
            }
        }
        
        if (!hasOverlap) {
            sb.append("  ✓ 未发现重叠网段\n");
        }
        
        sb.append("────────────────────────────────────");
        return sb.toString();
    }

    /**
     * 检测两个 CIDR 是否重叠
     */
    public static boolean isOverlap(String cidr1, String cidr2) {
        String[] p1 = cidr1.split("/");
        String[] p2 = cidr2.split("/");
        
        long n1 = getNetworkAddress(ipToLong(p1[0]), Integer.parseInt(p1[1]));
        int pr1 = Integer.parseInt(p1[1]);
        long b1 = getBroadcastAddress(n1, pr1);
        
        long n2 = getNetworkAddress(ipToLong(p2[0]), Integer.parseInt(p2[1]));
        int pr2 = Integer.parseInt(p2[1]);
        long b2 = getBroadcastAddress(n2, pr2);
        
        return !(b1 < n2 || b2 < n1);
    }
}
