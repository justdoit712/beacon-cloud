package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.util.R;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.converter.ClientBusinessConverter;
import com.cz.webmaster.dto.ClientBusinessForm;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.ClientBusinessService;
import com.cz.webmaster.service.SmsRoleService;
import com.cz.webmaster.vo.ClientBusinessDetailVO;
import com.cz.webmaster.vo.ClientBusinessVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户业务信息 Controller
 */
@RestController
@Slf4j
@RequestMapping("/sys/clientbusiness")
public class ClientBusinessController {

    private final SmsRoleService roleService;
    private final ClientBusinessService clientBusinessService;

    public ClientBusinessController(SmsRoleService roleService,
                                    ClientBusinessService clientBusinessService) {
        this.roleService = roleService;
        this.clientBusinessService = clientBusinessService;
    }

    @GetMapping("/list")
    public ResultVO list(@RequestParam(value = "offset", defaultValue = "0") Integer offset,
                         @RequestParam(value = "limit", defaultValue = "10") Integer limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        int safeOffset = offset == null || offset < 0 ? 0 : offset;
        int safeLimit = limit == null || limit <= 0 ? 10 : limit;

        List<ClientBusiness> list = clientBusinessService.findByKeyword(keyword);
        long total = clientBusinessService.countByKeyword(keyword);

        int fromIndex = Math.min(safeOffset, list.size());
        int toIndex = Math.min(safeOffset + safeLimit, list.size());

        List<ClientBusinessDetailVO> rows = new ArrayList<>();
        for (ClientBusiness cb : list.subList(fromIndex, toIndex)) {
            rows.add(ClientBusinessConverter.toDetailVO(cb));
        }
        return R.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        ClientBusiness cb = clientBusinessService.findById(id);
        return Collections.singletonMap("clientbusiness", ClientBusinessConverter.toDetailVO(cb));
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody ClientBusinessForm form) {
        if (form == null || !StringUtils.hasText(form.getCorpname())) {
            return R.error("公司名称不能为空");
        }
        ClientBusiness cb = ClientBusinessConverter.toEntity(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            cb.setCreateId(currentUser.getId().longValue());
            cb.setUpdateId(currentUser.getId().longValue());
        }
        boolean success = clientBusinessService.save(cb);
        return success ? R.ok("新增成功") : R.error("新增失败");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody ClientBusinessForm form) {
        if (form == null || form.getId() == null) {
            return R.error("客户id不能为空");
        }
        ClientBusiness cb = ClientBusinessConverter.toEntity(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            cb.setUpdateId(currentUser.getId().longValue());
        }
        boolean success = clientBusinessService.update(cb);
        return success ? R.ok("修改成功") : R.error("修改失败");
    }

    @PostMapping("/del")
    public ResultVO delete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.error("请选择要删除的数据");
        }
        boolean success = clientBusinessService.deleteBatch(ids);
        return success ? R.ok("删除成功") : R.error("删除失败");
    }

    @GetMapping("/all")
    public Map<String, Object> all() {
        SmsUser smsUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (smsUser == null) {
            log.info("【获取客户信息】用户未登录");
            Map<String, Object> result = new HashMap<>();
            result.put("code", ExceptionEnums.NOT_LOGIN.getCode());
            result.put("msg", ExceptionEnums.NOT_LOGIN.getMsg());
            return result;
        }

        Integer userId = smsUser.getId();
        Set<String> roleNameSet = roleService.getRoleName(userId);

        List<ClientBusiness> list;
        if (roleNameSet != null && roleNameSet.contains(WebMasterConstants.ROOT)) {
            list = clientBusinessService.findAll();
        } else {
            list = clientBusinessService.findByUserId(userId);
        }

        List<ClientBusinessVO> data = new ArrayList<>();
        if (list != null) {
            for (ClientBusiness clientBusiness : list) {
                data.add(ClientBusinessConverter.toSimpleVO(clientBusiness));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("msg", "");
        result.put("data", data);
        result.put("sites", data);
        return result;
    }

    @GetMapping("/pay")
    public ResultVO pay(@RequestParam("jine") Long amount,
                        @RequestParam(value = "clientId", required = false) Long clientId) {
        if (amount == null || amount <= 0) {
            return R.error("jine must be greater than 0");
        }

        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser == null) {
            return R.error(ExceptionEnums.NOT_LOGIN.getMsg());
        }

        Set<String> roleNameSet = roleService.getRoleName(currentUser.getId());
        boolean isRoot = roleNameSet != null && roleNameSet.contains(WebMasterConstants.ROOT);

        ClientBusiness target;
        if (clientId == null) {
            List<ClientBusiness> scope = isRoot
                    ? clientBusinessService.findAll()
                    : clientBusinessService.findByUserId(currentUser.getId());
            if (scope == null || scope.isEmpty()) {
                return R.error("no available client to pay");
            }
            target = scope.get(0);
        } else {
            target = clientBusinessService.findById(clientId);
            if (target == null) {
                return R.error("client not found");
            }
            if (!isRoot) {
                List<ClientBusiness> scope = clientBusinessService.findByUserId(currentUser.getId());
                boolean allowed = false;
                for (ClientBusiness clientBusiness : scope) {
                    if (target.getId().equals(clientBusiness.getId())) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    return R.error(ExceptionEnums.SMS_NO_AUTHOR.getMsg());
                }
            }
        }

        long oldAmount = 0L;
        String balanceValue = target.getExtend4();
        if (StringUtils.hasText(balanceValue)) {
            try {
                oldAmount = Long.parseLong(balanceValue);
            } catch (NumberFormatException e) {
                log.warn("invalid current balance for clientId={}, value={}", target.getId(), balanceValue);
            }
        }
        long newAmount = oldAmount + amount;

        ClientBusiness update = new ClientBusiness();
        update.setId(target.getId());
        update.setExtend4(String.valueOf(newAmount));
        update.setUpdateId(currentUser.getId().longValue());
        boolean success = clientBusinessService.update(update);
        if (!success) {
            return R.error("pay failed");
        }

        ResultVO resultVO = R.ok("pay success");
        Map<String, Object> data = new HashMap<>();
        data.put("clientId", target.getId());
        data.put("corpname", target.getCorpname());
        data.put("amount", amount);
        data.put("balance", newAmount);
        resultVO.setData(data);
        return resultVO;
    }
}
