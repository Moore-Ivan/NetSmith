package com.netsmith;

import java.util.regex.Pattern;

public class CidrValidator {

    private static final Pattern CIDR_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])/([0-9]|[1-2][0-9]|3[0-2])$");

    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])$");

    public static String[] validateCidr(String cidr) {
        if (cidr == null || cidr.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.CIDR, "请输入 CIDR");
        }
        String trimmed = cidr.trim();
        if (!CIDR_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationError(ValidationError.Field.CIDR, "无效 CIDR 格式，正确格式如: 192.168.1.0/24");
        }
        String[] parts = trimmed.split("/");
        validateIpOctets(parts[0]);
        int prefix = Integer.parseInt(parts[1]);
        if (prefix < 0 || prefix > 32) {
            throw new ValidationError(ValidationError.Field.CIDR, "前缀必须在 0-32 之间");
        }
        return parts;
    }

    public static void validateIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.IP, "请输入 IP 地址");
        }
        String trimmed = ip.trim();
        if (trimmed.contains("/")) {
            trimmed = trimmed.split("/")[0];
        }
        if (!IP_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationError(ValidationError.Field.IP, "无效 IP 格式，正确格式如: 192.168.1.1");
        }
        validateIpOctets(trimmed);
    }

    private static void validateIpOctets(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new ValidationError(ValidationError.Field.IP, "IP 必须有4个八位组");
        }
        for (String p : parts) {
            int val = Integer.parseInt(p);
            if (val < 0 || val > 255) {
                throw new ValidationError(ValidationError.Field.IP, "八位组必须在 0-255 之间: " + p);
            }
        }
    }

    public static int validateSubnetCount(String countStr) {
        return validateSubnetCount(countStr, Integer.MAX_VALUE);
    }

    public static int validateSubnetCount(String countStr, int maxCount) {
        if (countStr == null || countStr.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.SUBNET_COUNT, "请输入子网数量");
        }
        int count;
        try {
            count = Integer.parseInt(countStr.trim());
        } catch (NumberFormatException e) {
            throw new ValidationError(ValidationError.Field.SUBNET_COUNT, "请输入有效数字");
        }
        if (count <= 0) {
            throw new ValidationError(ValidationError.Field.SUBNET_COUNT, "子网数量必须大于 0");
        }
        if (count > maxCount) {
            throw new ValidationError(ValidationError.Field.SUBNET_COUNT, 
                "子网数量过大，请控制在 " + maxCount + " 以内，或使用按前缀划分方式");
        }
        return count;
    }

    public static int validateHostCount(String hostStr) {
        if (hostStr == null || hostStr.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.HOST_COUNT, "请输入主机数");
        }
        int hosts;
        try {
            hosts = Integer.parseInt(hostStr.trim());
        } catch (NumberFormatException e) {
            throw new ValidationError(ValidationError.Field.HOST_COUNT, "请输入有效数字");
        }
        if (hosts <= 0) {
            throw new ValidationError(ValidationError.Field.HOST_COUNT, "主机数必须大于 0");
        }
        return hosts;
    }

    public static int validatePrefix(String prefixStr, int min, int max) {
        if (prefixStr == null || prefixStr.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.PREFIX, "请输入前缀长度");
        }
        int prefix;
        try {
            prefix = Integer.parseInt(prefixStr.trim());
        } catch (NumberFormatException e) {
            throw new ValidationError(ValidationError.Field.PREFIX, "请输入有效数字");
        }
        if (prefix < min || prefix > max) {
            throw new ValidationError(ValidationError.Field.PREFIX, "前缀必须在 " + min + "-" + max + " 之间");
        }
        return prefix;
    }

    public static int validateDeptCount(String deptStr) {
        if (deptStr == null || deptStr.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.DEPT_COUNT, "请输入部门数量");
        }
        int count;
        try {
            count = Integer.parseInt(deptStr.trim());
        } catch (NumberFormatException e) {
            throw new ValidationError(ValidationError.Field.DEPT_COUNT, "请输入有效数字");
        }
        if (count <= 0) {
            throw new ValidationError(ValidationError.Field.DEPT_COUNT, "部门数量必须大于 0");
        }
        if (count > 1000) {
            throw new ValidationError(ValidationError.Field.DEPT_COUNT, "部门数量超过上限 1000");
        }
        return count;
    }

    public static String[] validateCidrList(String input, int minCount) {
        if (input == null || input.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.CIDR, "请输入至少一个 CIDR");
        }
        String[] cidrs = input.split(",");
        if (cidrs.length < minCount) {
            throw new ValidationError(ValidationError.Field.CIDR, "至少需要 " + minCount + " 个 CIDR 网段");
        }
        for (int i = 0; i < cidrs.length; i++) {
            cidrs[i] = cidrs[i].trim();
            if (cidrs[i].isEmpty()) {
                throw new ValidationError(ValidationError.Field.CIDR, "CIDR 不能为空");
            }
            validateCidr(cidrs[i]);
        }
        return cidrs;
    }

    public static int[] validateHostList(String hostStr) {
        if (hostStr == null || hostStr.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.HOST_LIST, "请输入主机数列表");
        }
        String[] parts = hostStr.trim().split(",");
        if (parts.length == 0 || parts[0].isEmpty()) {
            throw new ValidationError(ValidationError.Field.HOST_LIST, "请输入至少一个主机数");
        }
        int[] hosts = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                hosts[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                throw new ValidationError(ValidationError.Field.HOST_LIST, "第" + (i + 1) + "个主机数不是有效数字: " + parts[i].trim());
            }
            if (hosts[i] <= 0) {
                throw new ValidationError(ValidationError.Field.HOST_LIST, "主机数必须大于 0");
            }
        }
        return hosts;
    }

    public static long[] validateIpRange(String startIp, String endIp) {
        if (startIp == null || startIp.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.IP_START, "请输入起始 IP");
        }
        if (endIp == null || endIp.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.IP_END, "请输入结束 IP");
        }
        long start, end;
        try {
            start = SubnetCalculator.ipToLong(startIp.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationError(ValidationError.Field.IP_START, "无效的起始 IP: " + e.getMessage());
        }
        try {
            end = SubnetCalculator.ipToLong(endIp.trim());
        } catch (IllegalArgumentException e) {
            // 结束 IP 非法时错误必须归属到结束 IP 字段，避免用户被误导去改起始 IP
            throw new ValidationError(ValidationError.Field.IP_END, "无效的结束 IP: " + e.getMessage());
        }
        if (start > end) {
            throw new ValidationError(ValidationError.Field.IP_START, "起始 IP 不能大于结束 IP");
        }
        return new long[]{start, end};
    }
}