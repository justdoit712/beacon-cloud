package com.cz.webmaster.rebuild.loader;

import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.entity.Channel;
import com.cz.webmaster.mapper.ChannelMapper;
import com.cz.webmaster.rebuild.DomainRebuildLoader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code channel} 域缓存重建加载器。
 */
@Component
public class ChannelDomainRebuildLoader implements DomainRebuildLoader {

    private final ChannelMapper channelMapper;

    public ChannelDomainRebuildLoader(ChannelMapper channelMapper) {
        this.channelMapper = channelMapper;
    }

    @Override
    public String domainCode() {
        return CacheDomainRegistry.CHANNEL;
    }

    @Override
    public List<Object> loadSnapshot() {
        List<Channel> rows = channelMapper.findAllActive();
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(rows);
    }
}
