package com.cz.webmaster.rebuild.loader;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.entity.ClientBalance;
import com.cz.webmaster.mapper.ClientBalanceMapper;
import com.cz.webmaster.rebuild.DomainRebuildLoader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code client_balance} 域缓存重建加载器。 */
@Component
public class ClientBalanceDomainRebuildLoader implements DomainRebuildLoader {

    private final ClientBalanceMapper clientBalanceMapper;

    public ClientBalanceDomainRebuildLoader(ClientBalanceMapper clientBalanceMapper) {
        this.clientBalanceMapper = clientBalanceMapper;
    }

    @Override
    public String domainCode() {
        return CacheDomainRegistry.CLIENT_BALANCE;
    }

    @Override
    public List<Object> loadSnapshot() {
        List<ClientBalance> rows = clientBalanceMapper.selectAllActive();
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(rows);
    }
}
