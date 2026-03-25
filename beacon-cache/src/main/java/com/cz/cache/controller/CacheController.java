package com.cz.cache.controller;

import com.cz.cache.application.CacheFacade;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缓存访问控制器。
 *
 * <p>统一对外暴露缓存模块的基础读写、集合操作、批量删除、键扫描以及
 * 并发协调辅助接口。控制器负责完成逻辑 key 到物理 key 的命名空间映射、
 * 安全校验和请求级日志记录，具体 Redis 操作由底层客户端执行。</p>
 *
 * <p>该类面向内部服务提供通用缓存能力，不承载上层业务语义。</p>
 */
@RestController
public class CacheController {
    private final CacheFacade cacheFacade;

    public CacheController(CacheFacade cacheFacade) {
        this.cacheFacade = cacheFacade;
    }

    /**
     * 覆盖写入 Hash 全字段值。
     *
     * @param key 逻辑 key
     * @param map 需要写入的字段集合
     */
    @PostMapping(value = "/cache/hmset/{key}")
    public void hmset(@PathVariable(value = "key")String key, @RequestBody Map<String,Object> map){
        cacheFacade.hmset(key, map);
    }

    /**
     * 覆盖写入字符串值。
     *
     * @param key 逻辑 key
     * @param value 需要写入的值
     */
    @PostMapping(value = "/cache/set/{key}")
    public void set(@PathVariable(value = "key")String key, @RequestParam(value = "value")String value){
        cacheFacade.set(key, value);
    }

    /**
     * 向 Set 中追加对象成员。
     *
     * @param key 逻辑 key
     * @param value 需要追加的对象成员列表
     */
    @PostMapping(value = "/cache/sadd/{key}")
    public void sadd(@PathVariable(value = "key")String key, @RequestBody Map<String,Object>... value){
        cacheFacade.sadd(key, value);
    }

    /**
     * 读取 Hash 全字段内容。
     *
     * @param key 逻辑 key
     * @return Hash 字段映射
     */
    @GetMapping("/cache/hgetall/{key}")
    public Map hGetAll(@PathVariable(value = "key")String key){
        return cacheFacade.hGetAll(key);
    }

    /**
     * 读取 Hash 指定字段值。
     *
     * @param key 逻辑 key
     * @param field 字段名
     * @return 字段值；未命中时返回 {@code null}
     */
    @GetMapping("/cache/hget/{key}/{field}")
    public Object hget(@PathVariable(value = "key")String key,@PathVariable(value = "field")String field){
        return cacheFacade.hget(key, field);
    }

    /**
     * 读取 Set 全部成员。
     *
     * @param key 逻辑 key
     * @return Set 成员集合
     */
    @GetMapping("/cache/smember/{key}")
    public Set smember(@PathVariable(value = "key")String key){
        return cacheFacade.smember(key);
    }

    /**
     * 以 pipeline 方式批量写入字符串键值对。
     *
     * @param map 逻辑 key 到字符串值的映射
     */
    @PostMapping("/cache/pipeline/string")
    public void pipeline(@RequestBody Map<String,String> map){
        cacheFacade.pipeline(map);
    }

    /**
     * 读取指定逻辑 key 的字符串值。
     *
     * @param key 逻辑 key
     * @return key 对应的值；未命中时返回 {@code null}
     */
    @GetMapping("/cache/get/{key}")
    public Object get(@PathVariable(value = "key")String key){
        return cacheFacade.get(key);
    }

    /**
     * 仅当逻辑 key 不存在时写入值，并可选设置过期时间。
     *
     * <p>该接口主要用于分布式协调场景，例如缓存重建锁的申请。</p>
     *
     * @param key 逻辑 key
     * @param value 待写入值
     * @param ttlSeconds 过期时间（秒）；为 {@code null} 或小于等于 0 时表示不额外设置 TTL
     * @return true 表示写入成功，false 表示 key 已存在
     */
    @PostMapping(value = "/cache/setnx/{key}")
    public Boolean setIfAbsent(@PathVariable(value = "key") String key,
                               @RequestParam(value = "value") String value,
                               @RequestParam(value = "ttlSeconds", defaultValue = "300") Long ttlSeconds) {
        return cacheFacade.setIfAbsent(key, value, ttlSeconds);
    }

    /**
     * 原子读取并删除指定逻辑 key。
     *
     * <p>该接口主要用于一次性消费标记值，例如缓存重建结束后消费脏标记。</p>
     *
     * @param key 逻辑 key
     * @return 删除前的值；未命中时返回 {@code null}
     */
    @DeleteMapping(value = "/cache/pop/{key}")
    public Object pop(@PathVariable(value = "key") String key) {
        return cacheFacade.pop(key);
    }

    /**
     * 仅当当前值与期望值一致时删除指定逻辑 key。
     *
     * <p>该接口主要用于带令牌校验的安全释放场景，例如仅允许锁持有者释放重建锁。</p>
     *
     * @param key 逻辑 key
     * @param value 期望匹配的值
     * @return true 表示删除成功，false 表示 key 不存在或值不匹配
     */
    @DeleteMapping(value = "/cache/delete-if-match/{key}")
    public Boolean deleteIfValueMatches(@PathVariable(value = "key") String key,
                                        @RequestParam(value = "value") String value) {
        return cacheFacade.deleteIfValueMatches(key, value);
    }


    /**
     * 向 Set 中追加字符串成员。
     *
     * @param key 逻辑 key
     * @param value 需要追加的字符串成员列表
     */
    @PostMapping(value = "/cache/saddstr/{key}")
    public void saddStr(@PathVariable(value = "key")String key, @RequestBody String... value){
        cacheFacade.saddStr(key, value);
    }


    /**
     * 将临时集合与目标集合求交集，并返回交集结果。
     *
     * <p>执行过程会先把请求体中的成员写入临时 key，再与指定集合做交集，
     * 最后删除临时 key。</p>
     *
     * @param key 临时逻辑 key
     * @param sinterKey 参与交集计算的目标逻辑 key
     * @param value 需要写入临时集合的成员列表
     * @return 交集结果集合
     */
    @PostMapping(value = "/cache/sinterstr/{key}/{sinterKey}")
    public Set<Object> sinterStr(@PathVariable(value = "key")String key, @PathVariable String sinterKey,@RequestBody String... value){
        return cacheFacade.sinterStr(key, sinterKey, value);
    }

    /**
     * 向有序集合中写入成员及分值。
     *
     * @param key 逻辑 key
     * @param score 分值
     * @param member 成员值
     * @return true 表示写入成功
     */
    @PostMapping(value = "/cache/zadd/{key}/{score}/{member}")
    public Boolean zadd(@PathVariable(value = "key")String key,
                        @PathVariable(value = "score")Long score,
                        @PathVariable(value = "member")Object member){
        return cacheFacade.zadd(key, score, member);
    }

    /**
     * 统计有序集合在指定分值区间内的成员数量。
     *
     * @param key 逻辑 key
     * @param start 分值下界
     * @param end 分值上界
     * @return 命中的成员数量
     */
    @GetMapping(value = "/cache/zrangebyscorecount/{key}/{start}/{end}")
    public int zRangeByScoreCount(@PathVariable(value = "key") String key,
                                  @PathVariable(value = "start") Double start,
                                  @PathVariable(value = "end") Double end) {
        return cacheFacade.zRangeByScoreCount(key, start, end);
    }

    /**
     * 从有序集合中移除指定成员。
     *
     * @param key 逻辑 key
     * @param member 需要移除的成员
     */
    @DeleteMapping(value = "/cache/zremove/{key}/{member}")
    public void zRemove(@PathVariable(value = "key") String key,@PathVariable(value = "member") String member) {
        cacheFacade.zRemove(key, member);
    }

    /**
     * 对 Hash 指定字段执行原子自增。
     *
     * @param key 逻辑 key
     * @param field Hash 字段名
     * @param delta 增量值
     * @return 自增后的结果值
     */
    @PostMapping(value = "/cache/hincrby/{key}/{field}/{delta}")
    public Long hIncrBy(@PathVariable(value = "key") String key,
                        @PathVariable(value = "field") String field,
                        @PathVariable(value = "delta") Long delta){
        return cacheFacade.hIncrBy(key, field, delta);
    }

    /**
     * 删除单个逻辑 key（自动映射到当前命名空间物理 key）。
     */
    @DeleteMapping(value = "/cache/delete/{key}")
    public CacheDeleteResult delete(@PathVariable(value = "key") String key) {
        return cacheFacade.delete(key);
    }

    /**
     * 批量删除逻辑 key（自动映射到当前命名空间物理 key）。
     */
    @PostMapping(value = "/cache/delete/batch")
    public CacheDeleteResult deleteBatch(@RequestBody List<String> keys) {
        return cacheFacade.deleteBatch(keys);
    }

    /**
     * 按逻辑 pattern 扫描当前命名空间下的 key 列表。
     *
     * <p>调用前会先校验 pattern 是否命中允许列表，避免任意扫描 Redis。</p>
     *
     * @param pattern 逻辑 key pattern
     * @param count 单次 scan 建议批量大小
     * @return 命中的逻辑 key 集合
     */
    @GetMapping(value = "/cache/keys")
    public Set<String> keys(@RequestParam("pattern") String pattern,
                            @RequestParam(value = "count", defaultValue = "1000") Integer count){
        return cacheFacade.keys(pattern, count);
    }
}
