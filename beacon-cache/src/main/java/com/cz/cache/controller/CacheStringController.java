package com.cz.cache.controller;

import com.cz.cache.application.CacheFacade;
import com.cz.common.util.Result;
import com.cz.common.vo.CacheStringWriteRequest;
import com.cz.common.vo.ResultVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字符串缓存资源控制器。
 *
 * <p>将字符串值读写与删除操作从通用缓存控制器中拆分出来，避免单控制器持续膨胀，
 * 同时以资源路径表达字符串缓存项这一独立能力。</p>
 */
@RestController
@RequestMapping("/cache/strings")
public class CacheStringController {

    private final CacheFacade cacheFacade;

    public CacheStringController(CacheFacade cacheFacade) {
        this.cacheFacade = cacheFacade;
    }

    /**
     * 读取指定逻辑 key 对应的字符串值。
     *
     * @param key 逻辑 key
     * @return 字符串值；未命中时返回 {@code null}
     */
    @GetMapping("/{key}")
    public ResultVO<String> getString(@PathVariable("key") String key) {
        return okData(cacheFacade.getString(key));
    }

    /**
     * 覆盖写入字符串值，并可选设置过期时间。
     *
     * @param key 逻辑 key
     * @param request 写入值与 TTL 请求体
     * @return 通用成功响应
     */
    @PutMapping("/{key}")
    public ResultVO<Void> putString(@PathVariable("key") String key,
                                    @RequestBody CacheStringWriteRequest request) {
        cacheFacade.set(key, request.getValue(), request.getTtlSeconds());
        return Result.ok();
    }

    /**
     * 删除指定逻辑 key 对应的字符串值，并返回删除前的值。
     *
     * @param key 逻辑 key
     * @return 删除前的字符串值；未命中时返回 {@code null}
     */
    @DeleteMapping("/{key}")
    public ResultVO<String> deleteString(@PathVariable("key") String key) {
        return okData(cacheFacade.popString(key));
    }

    private <T> ResultVO<T> okData(T data) {
        ResultVO<T> result = new ResultVO<>(0, "");
        result.setData(data);
        return result;
    }
}
