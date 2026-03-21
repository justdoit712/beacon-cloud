package com.cz.webmaster.rebuild.loader;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.rebuild.DomainRebuildLoader;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code client_channel} 域缓存重建加载器。
 *
 * <p>当前阶段仅完成加载器注册，占位后续全量快照查询实现。</p>
 */
@Component
public class ClientChannelDomainRebuildLoader implements DomainRebuildLoader {

    @Override
    public String domainCode() {
        return CacheDomainRegistry.CLIENT_CHANNEL;
    }

    @Override
    public List<Object> loadSnapshot() {
        throw new UnsupportedOperationException("client_channel rebuild loader snapshot not implemented yet");
    }
}
