package com.netsmith;

import com.netsmith.service.AclService;
import com.netsmith.service.IPv4SubnetService;
import com.netsmith.service.IPv6SubnetService;
import com.netsmith.service.NatService;

import java.util.List;

public class SubnetController {

    private static final IPv4SubnetService ipv4Service = new IPv4SubnetService();
    private static final IPv6SubnetService ipv6Service = new IPv6SubnetService();
    private static final AclService aclService = new AclService();
    private static final NatService natService = new NatService();

    public interface ResultCallback {
        void onResult(String result);
    }

    public interface TableCallback {
        void onTableResult(List<SubnetTablePanel.SubnetRow> rows);
    }

    public interface ErrorCallback {
        void onError(ValidationError error);
    }

    public static void getSubnetDetails(String cidr, ResultCallback callback) {
        try {
            String result = ipv4Service.getSubnetDetails(cidr);
            callback.onResult(result);
        } catch (ValidationError e) {
            callback.onResult("错误: " + e.getMessage());
        }
    }

    public static void subnetByCount(String majorCidr, String countStr, TableCallback callback) {
        try {
            int count = CidrValidator.validateSubnetCount(countStr);
            List<SubnetTablePanel.SubnetRow> rows = ipv4Service.subnetByCount(majorCidr, count);
            callback.onTableResult(rows);
        } catch (ValidationError e) {
            throw e;
        }
    }

    public static void subnetByHosts(String majorCidr, String hostStr, TableCallback callback) {
        try {
            int hosts = CidrValidator.validateHostCount(hostStr);
            List<SubnetTablePanel.SubnetRow> rows = ipv4Service.subnetByHosts(majorCidr, hosts);
            callback.onTableResult(rows);
        } catch (ValidationError e) {
            throw e;
        }
    }

    public static List<SubnetTablePanel.SubnetRow> subnetByCountSync(String majorCidr, String countStr) {
        return subnetByCountSync(majorCidr, countStr, Integer.MAX_VALUE);
    }

    public static List<SubnetTablePanel.SubnetRow> subnetByCountSync(String majorCidr, String countStr, int maxCount) {
        int count = CidrValidator.validateSubnetCount(countStr, maxCount);
        return ipv4Service.subnetByCount(majorCidr, count);
    }

    public static List<SubnetTablePanel.SubnetRow> subnetByHostsSync(String majorCidr, String hostStr) {
        int hosts = CidrValidator.validateHostCount(hostStr);
        return ipv4Service.subnetByHosts(majorCidr, hosts);
    }

    /**
     * 按主机数划分子网（同步），返回完整结果含截断信息
     */
    public static IPv4SubnetService.SubnetByHostsResultEx subnetByHostsWithInfoSync(String majorCidr, String hostStr) {
        int hosts = CidrValidator.validateHostCount(hostStr);
        return ipv4Service.subnetByHostsWithInfo(majorCidr, hosts);
    }

    public static void vlsm(String majorCidr, String hostList, TableCallback callback) {
        try {
            int[] hosts = CidrValidator.validateHostList(hostList);
            List<SubnetTablePanel.SubnetRow> rows = ipv4Service.vlsm(majorCidr, hosts);
            callback.onTableResult(rows);
        } catch (ValidationError e) {
            throw e;
        }
    }

    public static List<SubnetTablePanel.SubnetRow> vlsmSync(String majorCidr, String hostList) {
        int[] hosts = CidrValidator.validateHostList(hostList);
        return ipv4Service.vlsm(majorCidr, hosts);
    }

    public static void summarize(String input, ResultCallback callback) {
        try {
            String[] cidrs = CidrValidator.validateCidrList(input, 1);
            String summary = ipv4Service.summarize(cidrs);

            StringBuilder sb = new StringBuilder();
            sb.append("═".repeat(55)).append("\n");
            sb.append("   路由汇总结果\n");
            sb.append("═".repeat(55)).append("\n");
            sb.append("  输入网段:\n");
            for (String c : cidrs) {
                sb.append(String.format("    • %s\n", c));
            }
            sb.append("─".repeat(55)).append("\n");
            sb.append(String.format("  汇总路由: %s\n", summary));
            sb.append("═".repeat(55)).append("\n");
            callback.onResult(sb.toString());
        } catch (ValidationError e) {
            callback.onResult("错误: " + e.getMessage());
        }
    }

    public static void parseIPDetails(String ip, ResultCallback callback) {
        try {
            String result = ipv4Service.parseIPDetails(ip);
            callback.onResult(result);
        } catch (ValidationError e) {
            callback.onResult("错误: " + e.getMessage());
        }
    }

    public static void ipRangeToCidr(String startIp, String endIp, TableCallback callback) {
        try {
            List<SubnetTablePanel.SubnetRow> rows = ipv4Service.ipRangeToCidr(startIp, endIp);
            callback.onTableResult(rows);
        } catch (ValidationError e) {
            throw e;
        }
    }

    public static void detectOverlaps(String input, ResultCallback callback) {
        try {
            String[] cidrs = CidrValidator.validateCidrList(input, 2);
            String result = ipv4Service.detectOverlaps(cidrs);
            callback.onResult(result);
        } catch (ValidationError e) {
            callback.onResult("错误: " + e.getMessage());
        }
    }

    public static void splitSupernet(String cidr, String prefixStr, TableCallback callback) {
        try {
            int newPrefix = CidrValidator.validatePrefix(prefixStr, 0, 32);
            List<SubnetTablePanel.SubnetRow> rows = ipv4Service.splitSupernet(cidr, newPrefix);
            callback.onTableResult(rows);
        } catch (ValidationError e) {
            throw e;
        }
    }

    public static List<SubnetTablePanel.SubnetRow> splitSupernetSync(String cidr, int newPrefix) {
        return ipv4Service.splitSupernet(cidr, newPrefix);
    }

    public static long getSplitSupernetCount(String cidr, int newPrefix) {
        return ipv4Service.getSplitSupernetCount(cidr, newPrefix);
    }

    public static List<SubnetTablePanel.SubnetRow> splitSupernetPagedSync(
            String cidr, int newPrefix, int pageNum, int pageSize) {
        return ipv4Service.splitSupernetPaged(cidr, newPrefix, pageNum, pageSize);
    }

    public static void planNetwork(String majorCidr, String deptCount, String hostsPerDept, TableCallback callback) {
        try {
            int deptCountInt = CidrValidator.validateDeptCount(deptCount);
            int hostsPerDeptInt = CidrValidator.validateHostCount(hostsPerDept);
            List<SubnetTablePanel.SubnetRow> rows = ipv4Service.planNetwork(majorCidr, deptCountInt, hostsPerDeptInt);
            callback.onTableResult(rows);
        } catch (ValidationError e) {
            throw e;
        }
    }

    public static List<SubnetTablePanel.SubnetRow> planNetworkSync(String majorCidr, String deptCount, String hostsPerDept) {
        int deptCountInt = CidrValidator.validateDeptCount(deptCount);
        int hostsPerDeptInt = CidrValidator.validateHostCount(hostsPerDept);
        return ipv4Service.planNetwork(majorCidr, deptCountInt, hostsPerDeptInt);
    }

    public static void getIPv6SubnetDetails(String cidr, ResultCallback callback) {
        try {
            String result = ipv6Service.getSubnetDetails(cidr);
            callback.onResult(result);
        } catch (ValidationError e) {
            callback.onResult("错误: " + e.getMessage());
        }
    }

    public static void ipv6SubnetByCount(String majorCidr, String countStr, TableCallback callback) {
        try {
            int count = IPv6Validator.validateSubnetCount(countStr, 1024);
            List<SubnetTablePanel.SubnetRow> rows = ipv6Service.subnetByCount(majorCidr, count);
            callback.onTableResult(rows);
        } catch (ValidationError e) {
            throw e;
        }
    }

    /**
     * IPv6 按前缀划分子网已统一强制使用分页模式（ipv6SubnetByPrefixPagedSync），
     * 不再提供"一次性加载全部"的同步方法，避免大数据量导致 OOM。
     */

    public static List<SubnetTablePanel.SubnetRow> ipv6SubnetByCountSync(String majorCidr, String countStr) {
        int count = IPv6Validator.validateSubnetCount(countStr, 1024);
        return ipv6Service.subnetByCount(majorCidr, count);
    }

    /**
     * 流式获取 IPv6 子网（返回 Stream，由调用方决定是否收集）
     * @param majorCidr 主网络 CIDR
     * @param prefixStr 新前缀
     * @return 子网行流
     */
    public static java.util.stream.Stream<SubnetTablePanel.SubnetRow> ipv6SubnetByPrefixStream(String majorCidr, String prefixStr) {
        int newPrefix = IPv6Validator.validatePrefix(prefixStr, 0, 128);
        return ipv6Service.subnetByPrefixStream(majorCidr, newPrefix);
    }

    /**
     * 获取 IPv6 子网迭代器，支持增量加载和游标分页
     * @param majorCidr 主网络 CIDR
     * @param prefixStr 新前缀
     * @return 子网迭代器
     */
    public static IPv6Calculator.SubnetIterator ipv6SubnetByPrefixIterator(String majorCidr, String prefixStr) {
        int newPrefix = IPv6Validator.validatePrefix(prefixStr, 0, 128);
        return ipv6Service.subnetByPrefixIterator(majorCidr, newPrefix);
    }

    public static java.math.BigInteger getIPv6SubnetCount(String majorCidr, int newPrefix) {
        return ipv6Service.getIPv6SubnetCount(majorCidr, newPrefix);
    }

    public static List<SubnetTablePanel.SubnetRow> ipv6SubnetByPrefixPagedSync(
            String majorCidr, int newPrefix, int pageNum, int pageSize) {
        return ipv6Service.subnetByPrefixPaged(majorCidr, newPrefix, pageNum, pageSize);
    }

    public static void parseIPv6Details(String addr, ResultCallback callback) {
        try {
            String result = ipv6Service.parseIPv6Details(addr);
            callback.onResult(result);
        } catch (ValidationError e) {
            callback.onResult("错误: " + e.getMessage());
        }
    }

    public static void summarizeIPv6(String input, ResultCallback callback) {
        try {
            String[] cidrs = IPv6Validator.validateCidrList(input, 1);
            String summary = ipv6Service.summarize(cidrs);

            StringBuilder sb = new StringBuilder();
            sb.append("═".repeat(55)).append("\n");
            sb.append("   IPv6 路由汇总结果\n");
            sb.append("═".repeat(55)).append("\n");
            sb.append("  输入网段:\n");
            for (String c : cidrs) {
                sb.append(String.format("    • %s\n", c));
            }
            sb.append("─".repeat(55)).append("\n");
            sb.append(String.format("  汇总路由: %s\n", summary));
            sb.append("═".repeat(55)).append("\n");
            callback.onResult(sb.toString());
        } catch (ValidationError e) {
            callback.onResult("错误: " + e.getMessage());
        }
    }

    /**
     * IPv6 VLSM 变长子网划分（同步）
     */
    public static List<SubnetTablePanel.SubnetRow> ipv6VlsmSync(String majorCidr, String hostList) {
        java.math.BigInteger[] hosts = IPv6Validator.validateHostList(hostList);
        return ipv6Service.vlsm(majorCidr, hosts);
    }

    /**
     * IPv6 IP 范围转 CIDR（同步）
     */
    public static List<SubnetTablePanel.SubnetRow> ipv6RangeToCidrSync(String startIp, String endIp) {
        return ipv6Service.ipRangeToCidr(startIp, endIp);
    }

    /**
     * 随机 IP 地址池生成（同步）：从 CIDR 中随机抽取 N 个不重复的可用 IPv4
     */
    public static List<String> randomPoolIpsSync(String cidr, String countStr) {
        return ipv4Service.randomPoolIps(cidr, countStr);
    }

    /**
     * ACL 策略检查（同步）：按 Cisco 扩展 ACL 规则逐条匹配数据包
     */
    public static AclService.AclCheckResult aclCheckSync(String rulesText, String srcIp, String dstIp,
                                                         String protocol, String srcPort, String dstPort) {
        return aclService.check(rulesText, srcIp, dstIp, protocol, srcPort, dstPort);
    }

    /**
     * NAT 配置辅助（同步）：生成 NAT 转换表建议报告
     */
    public static String natReportSync(String internalCidr, String publicEgress) {
        return natService.buildNatReport(internalCidr, publicEgress);
    }

    public static IPv4SubnetService getIPv4Service() {
        return ipv4Service;
    }

    public static IPv6SubnetService getIPv6Service() {
        return ipv6Service;
    }
}
