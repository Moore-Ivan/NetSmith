package com.netsmith;

import java.math.BigInteger;

public class IPv6Validator {

    public static String[] validateCidr(String cidr) {
        if (cidr == null || cidr.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.IPV6_CIDR, "请输入 CIDR");
        }
        String trimmed = cidr.trim();
        if (!trimmed.contains("/")) {
            throw new ValidationError(ValidationError.Field.IPV6_CIDR, "IPv6 CIDR 格式: 地址/前缀");
        }
        String[] parts = trimmed.split("/");
        if (parts.length != 2) {
            throw new ValidationError(ValidationError.Field.IPV6_CIDR, "无效 CIDR 格式");
        }
        validateAddress(parts[0]);
        int prefix;
        try {
            prefix = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new ValidationError(ValidationError.Field.PREFIX, "前缀长度必须是有效数字");
        }
        if (prefix < 0 || prefix > 128) {
            throw new ValidationError(ValidationError.Field.PREFIX, "前缀必须在 0-128 之间");
        }
        return parts;
    }

    /**
     * 验证 CIDR 并检查子网前缀是否大于等于主网络前缀（用于子网划分）
     * @param cidr 要验证的 CIDR
     * @param majorPrefix 主网络前缀（子网前缀必须 >= 此值）
     * @return 解析后的 CIDR 部分数组
     */
    public static String[] validateSubnetCidr(String cidr, int majorPrefix) {
        String[] parts = validateCidr(cidr);
        int subnetPrefix = Integer.parseInt(parts[1].trim());
        if (subnetPrefix < majorPrefix) {
            throw new ValidationError(ValidationError.Field.PREFIX,
                "子网前缀 (" + subnetPrefix + ") 必须 >= 主网络前缀 (" + majorPrefix + ")");
        }
        return parts;
    }

    public static BigInteger validateAddress(String addr) {
        if (addr == null || addr.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.IPV6_ADDRESS, "请输入 IPv6 地址");
        }
        String trimmed = addr.trim();
        if (trimmed.contains("/")) {
            trimmed = trimmed.split("/")[0];
        }
        try {
            return IPv6Calculator.parseIPv6(trimmed);
        } catch (IllegalArgumentException e) {
            throw new ValidationError(ValidationError.Field.IPV6_ADDRESS, "无效 IPv6 地址: " + addr);
        }
    }

    /** 默认最大子网数量限制 */
    public static final int DEFAULT_MAX_SUBNET_COUNT = 1024;

    /**
     * 验证子网数量（使用默认最大限制 1024）
     */
    public static int validateSubnetCount(String countStr) {
        return validateSubnetCount(countStr, DEFAULT_MAX_SUBNET_COUNT);
    }

    /**
     * 验证子网数量（带最大限制）
     */
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
            throw new ValidationError(ValidationError.Field.SUBNET_COUNT, "子网数量过大，请控制在 " + maxCount + " 以内");
        }
        return count;
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

    public static String[] validateCidrList(String input, int minCount) {
        if (input == null || input.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.IPV6_CIDR, "请输入至少一个 CIDR");
        }
        String[] cidrs = input.split(",");
        if (cidrs.length < minCount) {
            throw new ValidationError(ValidationError.Field.IPV6_CIDR, "至少需要 " + minCount + " 个 CIDR 网段");
        }
        for (int i = 0; i < cidrs.length; i++) {
            cidrs[i] = cidrs[i].trim();
            if (cidrs[i].isEmpty()) {
                throw new ValidationError(ValidationError.Field.IPV6_CIDR, "CIDR 不能为空");
            }
            validateCidr(cidrs[i]);
        }
        return cidrs;
    }

    /**
     * 验证主机数列表（用于 VLSM），返回 BigInteger 数组
     */
    public static BigInteger[] validateHostList(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.HOST_LIST, "请输入主机数列表");
        }
        String[] parts = input.split(",");
        if (parts.length == 0) {
            throw new ValidationError(ValidationError.Field.HOST_LIST, "请提供至少一个主机需求");
        }
        BigInteger[] hosts = new BigInteger[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String trimmed = parts[i].trim();
            if (trimmed.isEmpty()) {
                throw new ValidationError(ValidationError.Field.HOST_LIST, "主机数不能为空");
            }
            try {
                // 支持大数字（如 2^64）
                if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                    hosts[i] = new BigInteger(trimmed.substring(2), 16);
                } else if (trimmed.contains("^")) {
                    // 支持 2^64 格式
                    String[] expParts = trimmed.split("\\^");
                    if (expParts.length == 2) {
                        BigInteger base = new BigInteger(expParts[0].trim());
                        int exp = Integer.parseInt(expParts[1].trim());
                        
                        if (exp <= 0) {
                            throw new ValidationError(ValidationError.Field.HOST_LIST, 
                                "指数必须大于 0: " + trimmed);
                        }
                        if (exp > 128) {
                            throw new ValidationError(ValidationError.Field.HOST_LIST, 
                                "指数过大，最大支持 2^128: " + trimmed);
                        }
                        
                        if (base.equals(BigInteger.TWO)) {
                            hosts[i] = BigInteger.ONE.shiftLeft(exp);
                        } else {
                            // 限制非2幂次的指数最大值，防止内存耗尽（如 3^64 会产生约 3.4e30 位的 BigInteger）
                            if (exp > 32) {
                                throw new ValidationError(ValidationError.Field.HOST_LIST, 
                                    "非 2 的幂次指数最大支持 32: " + trimmed);
                            }
                            hosts[i] = base.pow(exp);
                        }
                    } else {
                        throw new ValidationError(ValidationError.Field.HOST_LIST, "无效的指数格式: " + trimmed);
                    }
                } else {
                    hosts[i] = new BigInteger(trimmed);
                }
            } catch (NumberFormatException e) {
                throw new ValidationError(ValidationError.Field.HOST_LIST, "无效的主机数: " + trimmed);
            }
            if (hosts[i].compareTo(BigInteger.ONE) < 0) {
                throw new ValidationError(ValidationError.Field.HOST_LIST, "主机数必须至少为 1: " + trimmed);
            }
            if (hosts[i].bitLength() > 128) {
                throw new ValidationError(ValidationError.Field.HOST_LIST, 
                    "主机数过大，超过 IPv6 地址空间限制: " + trimmed);
            }
        }
        return hosts;
    }

    /**
     * 验证 MAC 地址格式
     * @param mac MAC 地址
     * @return 标准化的 MAC 地址（无分隔符，小写）
     */
    public static String validateMacAddress(String mac) {
        if (mac == null || mac.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.MAC_ADDRESS, "请输入 MAC 地址");
        }
        String trimmed = mac.trim();
        
        // 支持格式：XX:XX:XX:XX:XX:XX, XX-XX-XX-XX-XX-XX, XXXXXXXXXXXX
        String cleanMac = trimmed.replaceAll("[:-]", "").toLowerCase();
        
        if (cleanMac.length() != 12) {
            throw new ValidationError(ValidationError.Field.MAC_ADDRESS, 
                "无效的 MAC 地址格式，应为 12 位十六进制字符");
        }
        
        // 验证每个字符都是有效的十六进制
        for (char c : cleanMac.toCharArray()) {
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                throw new ValidationError(ValidationError.Field.MAC_ADDRESS, 
                    "MAC 地址包含无效字符: " + c);
            }
        }
        
        return cleanMac;
    }

    /**
     * 验证 IPv4 地址格式
     * @param ipv4 IPv4 地址
     * @return 验证后的 IPv4 地址
     */
    public static String validateIPv4Address(String ipv4) {
        if (ipv4 == null || ipv4.trim().isEmpty()) {
            throw new ValidationError(ValidationError.Field.IPV4_ADDRESS, "请输入 IPv4 地址");
        }
        String trimmed = ipv4.trim();
        
        String[] octets = trimmed.split("\\.");
        if (octets.length != 4) {
            throw new ValidationError(ValidationError.Field.IPV4_ADDRESS, 
                "无效的 IPv4 地址格式，应为点分十进制");
        }
        
        for (String octet : octets) {
            try {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) {
                    throw new ValidationError(ValidationError.Field.IPV4_ADDRESS, 
                        "IPv4 地址每个字节必须在 0-255 之间");
                }
            } catch (NumberFormatException e) {
                throw new ValidationError(ValidationError.Field.IPV4_ADDRESS, 
                    "IPv4 地址包含无效字符: " + octet);
            }
        }
        
        return trimmed;
    }
}