package com.cz.webmaster.rebuild;

import java.util.List;

/**
 * 域级缓存重建加载器契约。
 *
 * <p>负责定义“某个缓存域如何提供全量重建快照”的统一接口。
 * 加载器只负责从真源侧组织重建所需的全量数据，不负责 Redis 写入、
 * 锁控制、报告聚合等流程编排。</p>
 *
 * <p>返回结果中的每个元素都应满足当前缓存域既有的运行时同步入参约定，
 * 以便后续重建引擎复用统一的同步门面完成 key 构建与写入。</p>
 */
public interface DomainRebuildLoader {

    /**
     * 返回当前加载器负责的缓存域编码。
     *
     * @return 缓存域编码
     */
    String domainCode();

    /**
     * 加载当前缓存域的全量重建快照。
     *
     * <p>返回结果中的元素将作为后续重建阶段的统一输入。
     * 例如 Hash 型域可返回实体或 Map，集合型域可返回按 key 聚合后的快照载荷。</p>
     *
     * @return 全量快照列表
     */
    List<Object> loadSnapshot();
}
