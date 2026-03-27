package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.client.SearchClient;
import com.cz.webmaster.controller.support.ControllerValueUtils;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.ClientBusinessService;
import com.cz.webmaster.service.SmsRoleService;
import com.cz.webmaster.vo.SearchSmsVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 短信搜索接口
 */
@RestController
@Slf4j
@RequestMapping("/sys/search")
public class SearchController {

    private final SmsRoleService roleService;
    private final SearchClient searchClient;
    private final ClientBusinessService clientBusinessService;

    public SearchController(SmsRoleService roleService,
                            SearchClient searchClient,
                            ClientBusinessService clientBusinessService) {
        this.roleService = roleService;
        this.searchClient = searchClient;
        this.clientBusinessService = clientBusinessService;
    }

    @GetMapping("/list")
    public PageResultVO<?> list(@RequestParam Map<String, Object> params) {
        SmsUser smsUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (smsUser == null) {
            log.info("【搜索短信信息】用户未登录");
            return Result.errorPage(ExceptionEnums.NOT_LOGIN);
        }

        Map<String, Object> queryParams = params == null ? new HashMap<String, Object>() : new HashMap<>(params);
        String clientIdStr = (String) queryParams.get("clientID");
        Long clientId = null;
        if (StringUtils.hasText(clientIdStr)) {
            clientId = ControllerValueUtils.parseLong(clientIdStr);
            if (clientId == null) {
                return Result.errorPage("clientID must be numeric");
            }
        }

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
                boolean allowed = false;
                for (ClientBusiness client : clients) {
                    if (clientId.equals(client.getId())) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    log.info("【搜索短信信息】用户权限不足, userId={}, clientId={}", smsUser.getId(), clientId);
                    return Result.errorPage(ExceptionEnums.SMS_NO_AUTHOR);
                }
            }
        }

        Map<String, Object> data = searchClient.findSmsByParameters(queryParams);
        long total = ControllerValueUtils.parseInt(data == null ? null : data.get("total"), 0);
        if (total <= 0) {
            return Result.ok(0L, new ArrayList<>());
        }

        Object rowsObj = data.get("rows");
        List<SearchSmsVO> rows = new ArrayList<>();
        if (rowsObj instanceof List) {
            for (Object item : (List<?>) rowsObj) {
                if (!(item instanceof Map)) {
                    continue;
                }
                SearchSmsVO vo = new SearchSmsVO();
                try {
                    BeanUtils.copyProperties(vo, (Map<?, ?>) item);
                } catch (Exception e) {
                    log.error("copy search row failed, row={}", item, e);
                }
                rows.add(vo);
            }
        }

        return Result.ok(total, rows);
    }
}


