package com.cz.webmaster.rebuild.loader;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.ClientBusinessExample;
import com.cz.webmaster.mapper.ClientBusinessMapper;
import com.cz.webmaster.rebuild.DomainRebuildLoader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code client_business} 域缓存重建加载器。
 */
@Component
public class ClientBusinessDomainRebuildLoader implements DomainRebuildLoader {

    private final ClientBusinessMapper clientBusinessMapper;

    public ClientBusinessDomainRebuildLoader(ClientBusinessMapper clientBusinessMapper) {
        this.clientBusinessMapper = clientBusinessMapper;
    }

    @Override
    public String domainCode() {
        return CacheDomainRegistry.CLIENT_BUSINESS;
    }

    @Override
    public List<Object> loadSnapshot() {
        ClientBusinessExample example = new ClientBusinessExample();
        example.createCriteria().andIsDeleteEqualTo((byte) 0);
        example.setOrderByClause("id asc");

        List<ClientBusiness> rows = clientBusinessMapper.selectByExample(example);
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(rows);
    }
}
