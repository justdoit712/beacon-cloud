package com.cz.webmaster.service;

import com.cz.common.constant.WebMasterConstants;
import com.cz.webmaster.client.SearchClient;
import com.cz.webmaster.controller.support.ControllerValueUtils;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.SmsUser;
import org.apache.shiro.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EchartsQueryService {

    private final SearchClient searchClient;
    private final SmsRoleService roleService;
    private final ClientBusinessService clientBusinessService;

    public EchartsQueryService(SearchClient searchClient,
                               SmsRoleService roleService,
                               ClientBusinessService clientBusinessService) {
        this.searchClient = searchClient;
        this.roleService = roleService;
        this.clientBusinessService = clientBusinessService;
    }

    public Map<String, Integer> queryStateCountWithPermission(Map<String, Object> params) {
        Map<String, Object> queryParams = params == null ? new HashMap<String, Object>() : new HashMap<>(params);

        Object principal = SecurityUtils.getSubject().getPrincipal();
        if (!(principal instanceof SmsUser)) {
            return emptyStateCount();
        }
        SmsUser smsUser = (SmsUser) principal;

        Long clientId = ControllerValueUtils.parseLong(ControllerValueUtils.toStr(queryParams.get("clientID")));

        Set<String> roleNames = roleService.getRoleName(smsUser.getId());
        if (roleNames != null && !roleNames.contains(WebMasterConstants.ROOT)) {
            List<ClientBusiness> clients = clientBusinessService.findByUserId(smsUser.getId());
            if (clientId == null) {
                List<Long> list = new ArrayList<>();
                for (ClientBusiness client : clients) {
                    list.add(client.getId());
                }
                queryParams.put("clientID", list);
            } else {
                boolean allow = false;
                for (ClientBusiness client : clients) {
                    if (clientId.equals(client.getId())) {
                        allow = true;
                        break;
                    }
                }
                if (!allow) {
                    return emptyStateCount();
                }
            }
        }

        Map<String, Integer> stateCount = searchClient.countSmsState(queryParams);
        if (stateCount == null || stateCount.isEmpty()) {
            return emptyStateCount();
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("waiting", stateCount.getOrDefault("waiting", 0));
        result.put("success", stateCount.getOrDefault("success", 0));
        result.put("fail", stateCount.getOrDefault("fail", 0));
        return result;
    }

    private Map<String, Integer> emptyStateCount() {
        Map<String, Integer> result = new HashMap<>();
        result.put("waiting", 0);
        result.put("success", 0);
        result.put("fail", 0);
        return result;
    }
}
