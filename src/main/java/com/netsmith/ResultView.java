package com.netsmith;

import java.util.List;

/**
 * 统一的结果展示契约：所有计算面板通过本接口输出计算结果，
 * 由 {@link SubnetGUI} 唯一实现并集中调度 TEXT/TABLE 卡片切换
 * （含 EDT 调度与窗口关闭保护），面板不感知输出区的布局细节。
 *
 * <p>三条输出路径对应 SubnetGUI 右侧输出区的两张卡片：</p>
 * <ul>
 *   <li>{@link #showText(String)} → TEXT 卡片（{@link ResultTablePanel} 文本展示）</li>
 *   <li>{@link #showTable(List)} → TABLE 卡片（{@link SubnetTablePanel}，整表驻留内存；
 *       超过 {@link SubnetTablePanel#MAX_MEMORY_DATA_SIZE} 会抛异常，海量数据应改用分页）</li>
 *   <li>{@link #showPagedTable(long, SubnetTablePanel.PagedDataProvider)} → TABLE 卡片
 *       （{@link SubnetTablePanel#setPagedData}，服务端分页，仅按需加载当前页）</li>
 * </ul>
 *
 * <p>实现类必须保证线程安全：可在任意线程调用，实现内部负责切回 EDT。</p>
 */
public interface ResultView {

    /** 展示纯文本结果（TEXT 卡片）。传空串表示清空结果区。 */
    void showText(String text);

    /**
     * 展示表格结果（TABLE 卡片，整表内存模式）。
     *
     * @param data 结果行；传空列表表示清空表格
     * @throws IllegalStateException 数据量超过内存上限时（由 SubnetTablePanel.setData 抛出）
     */
    void showTable(List<SubnetTablePanel.SubnetRow> data);

    /**
     * 展示服务端分页表格结果（TABLE 卡片）。
     *
     * @param totalCount 结果总条数
     * @param provider   按页取数的提供者，仅在用户翻页时被回调
     */
    void showPagedTable(long totalCount, SubnetTablePanel.PagedDataProvider provider);
}
