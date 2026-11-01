package com.netsmith.service;

import com.netsmith.SubnetCalculator;
import com.netsmith.SubnetTablePanel;
import com.netsmith.CidrValidator;
import com.netsmith.ValidationError;

import java.util.ArrayList;
import java.util.List;

public class IPv4SubnetService {

    public List<SubnetTablePanel.SubnetRow> subnetByCount(String majorCidr, int count) {
        CidrValidator.validateCidr(majorCidr);
        CidrValidator.validateSubnetCount(String.valueOf(count));
        List<SubnetCalculator.AddressBlock> subnets = SubnetCalculator.subnetByCount(majorCidr, count);
        return toRows(subnets);
    }

    public List<SubnetTablePanel.SubnetRow> subnetByHosts(String majorCidr, int hosts) {
        CidrValidator.validateCidr(majorCidr);
        CidrValidator.validateHostCount(String.valueOf(hosts));
        SubnetCalculator.SubnetByHostsResult result = SubnetCalculator.subnetByHosts(majorCidr, hosts);
        return toRows(result.subnets);
    }

    /**
     * 按每子网所需主机数划分，返回完整结果（含截断信息）
     */
    public SubnetByHostsResultEx subnetByHostsWithInfo(String majorCidr, int hosts) {
        CidrValidator.validateCidr(majorCidr);
        CidrValidator.validateHostCount(String.valueOf(hosts));
        SubnetCalculator.SubnetByHostsResult result = SubnetCalculator.subnetByHosts(majorCidr, hosts);
        return new SubnetByHostsResultEx(toRows(result.subnets), result.totalCount, result.truncated, result.prefix);
    }

    /**
     * 子网划分结果扩展类（含截断信息）
     */
    public static class SubnetByHostsResultEx {
        public final List<SubnetTablePanel.SubnetRow> rows;
        public final long totalCount;
        public final boolean truncated;
        public final int prefix;

        public SubnetByHostsResultEx(List<SubnetTablePanel.SubnetRow> rows, long totalCount, boolean truncated, int prefix) {
            this.rows = rows;
            this.totalCount = totalCount;
            this.truncated = truncated;
            this.prefix = prefix;
        }
    }

    public List<SubnetTablePanel.SubnetRow> vlsm(String majorCidr, int[] hosts) {
        CidrValidator.validateCidr(majorCidr);
        List<SubnetCalculator.AddressBlock> subnets = SubnetCalculator.vlsm(majorCidr, hosts);
        return toRows(subnets);
    }

    public List<SubnetTablePanel.SubnetRow> splitSupernet(String cidr, int newPrefix) {
        CidrValidator.validateCidr(cidr);
        CidrValidator.validatePrefix(String.valueOf(newPrefix), 0, 32);
        List<SubnetCalculator.AddressBlock> subnets = SubnetCalculator.splitSupernet(cidr, newPrefix);
        return toRows(subnets);
    }

    public long getSplitSupernetCount(String cidr, int newPrefix) {
        CidrValidator.validateCidr(cidr);
        CidrValidator.validatePrefix(String.valueOf(newPrefix), 0, 32);
        return SubnetCalculator.getSubnetCount(cidr, newPrefix);
    }

    public List<SubnetTablePanel.SubnetRow> splitSupernetPaged(String cidr, int newPrefix, int pageNum, int pageSize) {
        CidrValidator.validateCidr(cidr);
        CidrValidator.validatePrefix(String.valueOf(newPrefix), 0, 32);
        if (pageNum < 1) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 10000) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "每页大小必须在 1-10000 之间");
        }
        List<SubnetCalculator.AddressBlock> subnets = SubnetCalculator.splitSupernetPaged(cidr, newPrefix, pageNum, pageSize);
        return toRows(subnets);
    }

    public List<SubnetTablePanel.SubnetRow> planNetwork(String majorCidr, int deptCount, int hostsPerDept) {
        CidrValidator.validateCidr(majorCidr);
        CidrValidator.validateDeptCount(String.valueOf(deptCount));
        CidrValidator.validateHostCount(String.valueOf(hostsPerDept));
        int[] hostRequirements = new int[deptCount];
        for (int i = 0; i < deptCount; i++) {
            hostRequirements[i] = hostsPerDept;
        }
        List<SubnetCalculator.AddressBlock> subnets = SubnetCalculator.vlsm(majorCidr, hostRequirements);
        return toRows(subnets);
    }

    public String summarize(String... cidrs) {
        if (cidrs.length == 0) {
            throw new ValidationError(ValidationError.Field.CIDR, "请至少输入一个 CIDR");
        }
        return SubnetCalculator.summarize(cidrs);
    }

    public String getSubnetDetails(String cidr) {
        CidrValidator.validateCidr(cidr);
        return SubnetCalculator.getSubnetDetails(cidr);
    }

    public String parseIPDetails(String ip) {
        String trimmed = ip.trim();
        if (trimmed.contains("/")) {
            trimmed = trimmed.split("/")[0];
        }
        CidrValidator.validateIp(trimmed);
        return SubnetCalculator.parseIPDetails(trimmed);
    }

    public String detectOverlaps(String... cidrs) {
        if (cidrs.length < 2) {
            throw new ValidationError(ValidationError.Field.CIDR, "请至少输入两个 CIDR");
        }
        return SubnetCalculator.detectOverlaps(cidrs);
    }

    public List<SubnetTablePanel.SubnetRow> ipRangeToCidr(String startIp, String endIp) {
        CidrValidator.validateIpRange(startIp, endIp);
        List<String> cidrs = SubnetCalculator.ipRangeToCidr(startIp, endIp);
        List<SubnetTablePanel.SubnetRow> rows = new ArrayList<>();
        for (String cidr : cidrs) {
            String[] p = cidr.split("/");
            int prefix = Integer.parseInt(p[1]);
            long network = SubnetCalculator.getNetworkAddress(
                    SubnetCalculator.ipToLong(p[0]), prefix);
            long broadcast = SubnetCalculator.getBroadcastAddress(network, prefix);
            long mask = SubnetCalculator.getSubnetMask(prefix);
            rows.add(new SubnetTablePanel.SubnetRow(
                    cidr,
                    prefix,
                    String.valueOf(SubnetCalculator.getUsableHostCount(prefix)),
                    SubnetCalculator.longToIp(mask),
                    SubnetCalculator.longToIp(broadcast)
            ));
        }
        return rows;
    }

    /** 单次随机生成 IP 数量上限 */
    public static final int MAX_RANDOM_POOL_SIZE = 100000;

    /**
     * 整体洗牌策略的池大小阈值：可用地址数不超过该值时构造完整索引数组做部分 Fisher-Yates 洗牌
     * （结果均匀且严格不重复）；超过该值时改用拒绝采样（池远大于生成数量，冲突概率可忽略）。
     */
    private static final long SHUFFLE_POOL_THRESHOLD = 1_000_000L;

    /**
     * 从 CIDR 网段中随机抽取 count 个不重复的可用 IP 地址。
     * 可用地址口径与项目约定一致：/31 两端均可用（2 个）、/32 单地址（1 个），
     * 其余前缀排除网络地址与广播地址。
     *
     * @param cidr     网段，如 192.168.1.0/24
     * @param countStr 生成数量字符串，1 - {@value #MAX_RANDOM_POOL_SIZE}，且不超过网段可用地址数
     * @return 随机顺序的 IP 地址列表
     */
    public List<String> randomPoolIps(String cidr, String countStr) {
        String[] parts = CidrValidator.validateCidr(cidr);
        int count;
        try {
            count = Integer.parseInt(countStr == null ? "" : countStr.trim());
        } catch (NumberFormatException e) {
            throw new ValidationError(ValidationError.Field.HOST_COUNT, "请输入有效数量");
        }
        if (count < 1) {
            throw new ValidationError(ValidationError.Field.HOST_COUNT, "生成数量必须大于 0");
        }
        if (count > MAX_RANDOM_POOL_SIZE) {
            throw new ValidationError(ValidationError.Field.HOST_COUNT,
                    "生成数量过大，请控制在 " + MAX_RANDOM_POOL_SIZE + " 以内");
        }

        int prefix = Integer.parseInt(parts[1]);
        long network = SubnetCalculator.getNetworkAddress(SubnetCalculator.ipToLong(parts[0]), prefix);
        // 可用地址区间 [lo, hi]（闭区间）
        long lo, hi;
        if (prefix >= 32) {
            lo = hi = network;
        } else if (prefix == 31) {
            lo = network;
            hi = network + 1;
        } else {
            lo = network + 1;
            hi = SubnetCalculator.getBroadcastAddress(network, prefix) - 1;
        }
        long poolSize = hi - lo + 1;
        if (count > poolSize) {
            throw new ValidationError(ValidationError.Field.HOST_COUNT,
                    "生成数量超过网段可用地址数 " + poolSize);
        }

        long[] picked = new long[count];
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        if (poolSize <= SHUFFLE_POOL_THRESHOLD) {
            // 池较小时：完整索引数组 + 部分 Fisher-Yates 洗牌，均匀且严格不重复
            long[] idx = new long[(int) poolSize];
            for (long v = lo; v <= hi; v++) {
                idx[(int) (v - lo)] = v;
            }
            for (int i = 0; i < count; i++) {
                int j = rnd.nextInt(i, (int) poolSize);
                long tmp = idx[i];
                idx[i] = idx[j];
                idx[j] = tmp;
                picked[i] = idx[i];
            }
        } else {
            // 池巨大时：拒绝采样（count ≤ 10万 ≪ 池大小 > 100万，冲突率极低）
            java.util.LinkedHashSet<Long> seen = new java.util.LinkedHashSet<>(count * 2);
            while (seen.size() < count) {
                seen.add(lo + rnd.nextLong(poolSize));
            }
            int k = 0;
            for (Long v : seen) {
                picked[k++] = v;
            }
        }

        List<String> result = new ArrayList<>(count);
        for (long v : picked) {
            result.add(SubnetCalculator.longToIp(v));
        }
        return result;
    }

    private List<SubnetTablePanel.SubnetRow> toRows(List<SubnetCalculator.AddressBlock> blocks) {
        List<SubnetTablePanel.SubnetRow> rows = new ArrayList<>();
        for (SubnetCalculator.AddressBlock b : blocks) {
            rows.add(new SubnetTablePanel.SubnetRow(
                    b.toString(),
                    b.prefix,
                    String.valueOf(b.usableHosts()),
                    SubnetCalculator.longToIp(b.mask),
                    SubnetCalculator.longToIp(b.broadcast)
            ));
        }
        return rows;
    }
}
