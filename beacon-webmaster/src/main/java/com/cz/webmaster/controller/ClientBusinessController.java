package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.converter.ClientBusinessConverter;
import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.dto.ClientBusinessForm;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.BalanceCommandService;
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
 * 客户业务信息 Controller。
 */
@RestController
@Slf4j
@RequestMapping("/sys/clientbusiness")
public class ClientBusinessController {

    private final SmsRoleService roleService;
    private final ClientBusinessService clientBusinessService;
    private final BalanceCommandService balanceCommandService;

    public ClientBusinessController(SmsRoleService roleService,
                                    ClientBusinessService clientBusinessService,
                                    BalanceCommandService balanceCommandService) {
        this.roleService = roleService;
        this.clientBusinessService = clientBusinessService;
        this.balanceCommandService = balanceCommandService;
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
        return Result.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        ClientBusiness cb = clientBusinessService.findById(id);
        return Collections.singletonMap("clientbusiness", ClientBusinessConverter.toDetailVO(cb));
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody ClientBusinessForm form) {
        if (form == null || !StringUtils.hasText(form.getCorpname())) {
            return Result.error("鍏徃鍚嶇О涓嶈兘涓虹┖");
        }
        ClientBusiness cb = ClientBusinessConverter.toEntity(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            cb.setCreateId(currentUser.getId().longValue());
            cb.setUpdateId(currentUser.getId().longValue());
        }
        boolean success = clientBusinessService.save(cb);
        return success ? Result.ok("鏂板鎴愬姛") : Result.error("鏂板澶辫触");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody ClientBusinessForm form) {
        if (form == null || form.getId() == null) {
            return Result.error("瀹㈡埛id涓嶈兘涓虹┖");
        }
        ClientBusiness cb = ClientBusinessConverter.toEntity(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            cb.setUpdateId(currentUser.getId().longValue());
        }
        boolean success = clientBusinessService.update(cb);
        return success ? Result.ok("淇敼鎴愬姛") : Result.error("淇敼澶辫触");
    }

    @PostMapping("/del")
    public ResultVO delete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("璇烽€夋嫨瑕佸垹闄ょ殑鏁版嵁");
        }
        boolean success = clientBusinessService.deleteBatch(ids);
        return success ? Result.ok("鍒犻櫎鎴愬姛") : Result.error("鍒犻櫎澶辫触");
    }

    @GetMapping("/all")
    public Map<String, Object> all() {
        SmsUser smsUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (smsUser == null) {
            log.info("銆愯幏鍙栧鎴蜂俊鎭€戠敤鎴锋湭鐧诲綍");
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

    /**
     * 客户充值入口。
     *
     * <p>当前实现统一委托给余额命令服务完成充值，
     * 不再通过“先读旧余额、再计算新余额、最后调用通用 update”的方式修改余额。</p>
     */
    @GetMapping("/pay")
    public ResultVO pay(@RequestParam("jine") Long amount,
                        @RequestParam(value = "clientId", required = false) Long clientId) {
        if (amount == null || amount <= 0) {
            return Result.error("jine must be greater than 0");
        }

        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser == null) {
            return Result.error(ExceptionEnums.NOT_LOGIN.getMsg());
        }

        Set<String> roleNameSet = roleService.getRoleName(currentUser.getId());
        boolean isRoot = roleNameSet != null && roleNameSet.contains(WebMasterConstants.ROOT);

        ClientBusiness target;
        if (clientId == null) {
            List<ClientBusiness> scope = isRoot
                    ? clientBusinessService.findAll()
                    : clientBusinessService.findByUserId(currentUser.getId());
            if (scope == null || scope.isEmpty()) {
                return Result.error("no available client to pay");
            }
            target = scope.get(0);
        } else {
            target = clientBusinessService.findById(clientId);
            if (target == null) {
                return Result.error("client not found");
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
                    return Result.error(ExceptionEnums.SMS_NO_AUTHOR.getMsg());
                }
            }
        }

        BalanceCommandResult commandResult = balanceCommandService.rechargeAndSync(
                target.getId(),
                amount,
                currentUser.getId().longValue(),
                null
        );
        if (!commandResult.isSuccess()) {
            return Result.error(commandResult.getCode(), commandResult.getMessage());
        }

        ResultVO resultVO = Result.ok("pay success");
        Map<String, Object> data = new HashMap<>();
        data.put("clientId", target.getId());
        data.put("corpname", target.getCorpname());
        data.put("amount", amount);
        data.put("balance", commandResult.getBalance());
        resultVO.setData(data);
        return resultVO;
    }
}
