package com.netsmith;

import org.junit.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.Assert.*;

/**
 * VLSM 变长子网划分回归测试
 * 用于验证 SubnetCalculator.vlsm 与 IPv6Calculator.vlsm 的行为正确性，
 * 防止性能优化（空闲块管理改为 TreeSet）引入行为回归。
 */
public class VlsmTest {

    // ---------- IPv4 VLSM ----------

    @Test
    public void ipv4Basic() {
        List<SubnetCalculator.AddressBlock> result =
            SubnetCalculator.vlsm("192.168.1.0/24", 50, 20, 10);
        assertEquals(3, result.size());
        assertEquals("192.168.1.0/26", result.get(0).toString());
        assertEquals("192.168.1.64/27", result.get(1).toString());
        assertEquals("192.168.1.96/28", result.get(2).toString());
        assertNoOverlapIPv4(result);
    }

    @Test
    public void ipv4Single() {
        List<SubnetCalculator.AddressBlock> result =
            SubnetCalculator.vlsm("10.0.0.0/24", 100);
        assertEquals(1, result.size());
        assertEquals("10.0.0.0/25", result.get(0).toString());
    }

    @Test
    public void ipv4FourEqual() {
        List<SubnetCalculator.AddressBlock> result =
            SubnetCalculator.vlsm("192.168.0.0/24", 50, 50, 50, 50);
        assertEquals(4, result.size());
        for (SubnetCalculator.AddressBlock block : result) {
            assertEquals(26, block.prefix);
        }
        assertEquals("192.168.0.0/26", result.get(0).toString());
        assertEquals("192.168.0.64/26", result.get(1).toString());
        assertEquals("192.168.0.128/26", result.get(2).toString());
        assertEquals("192.168.0.192/26", result.get(3).toString());
        assertNoOverlapIPv4(result);
    }

    @Test
    public void ipv4PointToPoint() {
        // 需求降序排列：2（/31）先于 1（/32）
        List<SubnetCalculator.AddressBlock> result =
            SubnetCalculator.vlsm("192.168.1.0/30", 1, 2);
        assertEquals(2, result.size());
        assertEquals("192.168.1.0/31", result.get(0).toString());
        assertEquals("192.168.1.2/32", result.get(1).toString());
        assertNoOverlapIPv4(result);
    }

    @Test
    public void ipv4Fragmented() {
        // 混合需求制造碎片，验证分裂逻辑
        List<SubnetCalculator.AddressBlock> result =
            SubnetCalculator.vlsm("10.0.0.0/16", 300, 200, 100, 50, 30, 10);
        assertEquals(6, result.size());
        assertNoOverlapIPv4(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv4OverCapacity() {
        SubnetCalculator.vlsm("192.168.1.0/24", 300);
    }

    // ---------- IPv6 VLSM ----------

    @Test
    public void ipv6Basic() {
        List<IPv6Calculator.IPv6Block> result =
            IPv6Calculator.vlsm("2001:db8::/48",
                BigInteger.valueOf(10000), BigInteger.valueOf(5000), BigInteger.valueOf(1000));
        assertEquals(3, result.size());
        assertEquals(114, result.get(0).prefix);
        assertEquals(115, result.get(1).prefix);
        assertEquals(118, result.get(2).prefix);
        assertNoOverlapIPv6(result);
    }

    @Test
    public void ipv6ManyFragmented() {
        // 大量小需求，考验分裂与合并逻辑
        List<IPv6Calculator.IPv6Block> result =
            IPv6Calculator.vlsm("2001:db8::/56",
                BigInteger.valueOf(100), BigInteger.valueOf(50), BigInteger.valueOf(30),
                BigInteger.valueOf(20), BigInteger.valueOf(10), BigInteger.valueOf(5));
        assertEquals(6, result.size());
        assertNoOverlapIPv6(result);
    }

    @Test
    public void ipv6EqualSizes() {
        // 相同主机需求，验证同 size 块的稳定选择
        List<IPv6Calculator.IPv6Block> result =
            IPv6Calculator.vlsm("2001:db8::/64",
                BigInteger.valueOf(50), BigInteger.valueOf(50), BigInteger.valueOf(50));
        assertEquals(3, result.size());
        assertEquals(122, result.get(0).prefix);
        assertEquals(122, result.get(1).prefix);
        assertEquals(122, result.get(2).prefix);
        assertNoOverlapIPv6(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipv6OverCapacity() {
        IPv6Calculator.vlsm("2001:db8::/126", BigInteger.valueOf(1000));
    }

    // ---------- 校验工具 ----------

    private void assertNoOverlapIPv4(List<SubnetCalculator.AddressBlock> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                SubnetCalculator.AddressBlock a = blocks.get(i);
                SubnetCalculator.AddressBlock b = blocks.get(j);
                assertTrue("重叠: " + a + " 与 " + b,
                    a.broadcast < b.network || b.broadcast < a.network);
            }
        }
    }

    private void assertNoOverlapIPv6(List<IPv6Calculator.IPv6Block> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                IPv6Calculator.IPv6Block a = blocks.get(i);
                IPv6Calculator.IPv6Block b = blocks.get(j);
                assertTrue("重叠: " + a + " 与 " + b,
                    a.last.compareTo(b.network) < 0 || b.last.compareTo(a.network) < 0);
            }
        }
    }
}
