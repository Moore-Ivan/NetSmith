package com.netsmith.service;

import com.netsmith.IPv6Calculator;
import com.netsmith.SubnetTablePanel;
import com.netsmith.IPv6Validator;
import com.netsmith.ValidationError;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class IPv6SubnetService {

    public List<SubnetTablePanel.SubnetRow> subnetByCount(String majorCidr, int count) {
        IPv6Validator.validateCidr(majorCidr);
        IPv6Validator.validateSubnetCount(String.valueOf(count), 1024);
        return IPv6Calculator.subnetByCount(majorCidr, count)
                .map(this::toIPv6Row)
                .collect(Collectors.toList());
    }

    /**
     * IPv6 大子网划分统一使用分页模式，不再提供"一次性加载全部"路径（避免 OOM）。
     * 请使用 subnetByPrefixPaged 或 subnetByPrefixIterator + getPageFromIterator 获取数据。
     */

    public java.util.stream.Stream<SubnetTablePanel.SubnetRow> subnetByPrefixStream(String majorCidr, int newPrefix) {
        IPv6Validator.validateCidr(majorCidr);
        IPv6Validator.validatePrefix(String.valueOf(newPrefix), 0, 128);
        return IPv6Calculator.subnetByPrefix(majorCidr, newPrefix)
                .map(this::toIPv6Row);
    }

    /**
     * 获取子网迭代器，支持增量加载和游标分页
     * @param majorCidr 主网络 CIDR
     * @param newPrefix 目标前缀
     * @return 子网迭代器
     */
    public IPv6Calculator.SubnetIterator subnetByPrefixIterator(String majorCidr, int newPrefix) {
        IPv6Validator.validateCidr(majorCidr);
        IPv6Validator.validatePrefix(String.valueOf(newPrefix), 0, 128);
        return IPv6Calculator.createSubnetIterator(majorCidr, newPrefix);
    }

    /**
     * 使用迭代器分页获取子网，支持任意大数量的子网
     * @param iterator 子网迭代器
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页后的子网列表
     */
    public List<SubnetTablePanel.SubnetRow> getPageFromIterator(IPv6Calculator.SubnetIterator iterator, int pageNum, int pageSize) {
        if (pageNum < 1) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 10000) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "每页大小必须在 1-10000 之间");
        }
        
        // 计算起始位置并跳转到该位置
        BigInteger startIndex = BigInteger.valueOf((pageNum - 1) * (long) pageSize);
        iterator.seek(startIndex);
        
        List<SubnetTablePanel.SubnetRow> rows = new ArrayList<>();
        for (int i = 0; i < pageSize && iterator.hasNext(); i++) {
            rows.add(toIPv6Row(iterator.next()));
        }
        return rows;
    }

    public BigInteger getIPv6SubnetCount(String majorCidr, int newPrefix) {
        IPv6Validator.validateCidr(majorCidr);
        IPv6Validator.validatePrefix(String.valueOf(newPrefix), 0, 128);
        return IPv6Calculator.getSubnetCount(majorCidr, newPrefix);
    }

    public List<SubnetTablePanel.SubnetRow> subnetByPrefixPaged(String majorCidr, int newPrefix, int pageNum, int pageSize) {
        IPv6Validator.validateCidr(majorCidr);
        IPv6Validator.validatePrefix(String.valueOf(newPrefix), 0, 128);
        if (pageNum < 1) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 10000) {
            throw new ValidationError(ValidationError.Field.UNKNOWN, "每页大小必须在 1-10000 之间");
        }
        List<IPv6Calculator.IPv6Block> subnets = IPv6Calculator.subnetByPrefixPaged(majorCidr, newPrefix, pageNum, pageSize);
        return toIPv6Rows(subnets);
    }

    public String getSubnetDetails(String cidr) {
        IPv6Validator.validateCidr(cidr);
        return IPv6Calculator.getSubnetDetails(cidr);
    }

    public String parseIPv6Details(String addr) {
        IPv6Validator.validateAddress(addr);
        return IPv6Calculator.parseIPv6Details(addr);
    }

    public String summarize(String... cidrs) {
        if (cidrs.length == 0) {
            throw new ValidationError(ValidationError.Field.CIDR, "请至少输入一个 IPv6 CIDR");
        }
        return IPv6Calculator.summarizeIPv6(cidrs);
    }

    /**
     * IPv6 VLSM 变长子网划分
     */
    public List<SubnetTablePanel.SubnetRow> vlsm(String majorCidr, java.math.BigInteger... hostCounts) {
        IPv6Validator.validateCidr(majorCidr);
        List<IPv6Calculator.IPv6Block> subnets = IPv6Calculator.vlsm(majorCidr, hostCounts);
        return toIPv6Rows(subnets);
    }

    /**
     * IPv6 IP 范围转 CIDR
     */
    public List<SubnetTablePanel.SubnetRow> ipRangeToCidr(String startIp, String endIp) {
        IPv6Validator.validateAddress(startIp);
        IPv6Validator.validateAddress(endIp);
        List<String> cidrs = IPv6Calculator.ipRangeToCidr(startIp, endIp);
        List<SubnetTablePanel.SubnetRow> rows = new ArrayList<>();
        for (String cidr : cidrs) {
            String[] p = cidr.split("/");
            int prefix = Integer.parseInt(p[1]);
            IPv6Calculator.IPv6Block block = new IPv6Calculator.IPv6Block(
                IPv6Calculator.parseIPv6(p[0]), prefix);
            BigInteger hosts = block.usableHosts();
            rows.add(new SubnetTablePanel.SubnetRow(
                    cidr,
                    prefix,
                    hosts.toString(),
                    "/" + prefix,
                    IPv6Calculator.formatCompressed(block.last)
            ));
        }
        return rows;
    }

    private List<SubnetTablePanel.SubnetRow> toIPv6Rows(List<IPv6Calculator.IPv6Block> blocks) {
        List<SubnetTablePanel.SubnetRow> rows = new ArrayList<>();
        for (IPv6Calculator.IPv6Block b : blocks) {
            rows.add(toIPv6Row(b));
        }
        return rows;
    }

    private SubnetTablePanel.SubnetRow toIPv6Row(IPv6Calculator.IPv6Block b) {
        BigInteger hosts = b.usableHosts();
        return new SubnetTablePanel.SubnetRow(
                b.toString(),
                b.prefix,
                hosts.toString(),
                "/" + b.prefix,
                IPv6Calculator.formatCompressed(b.last)
        );
    }
}
