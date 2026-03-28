package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.converter.ClientBusinessConverter;
import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.dto.ClientBalanceRechargeCommand;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client business controller.
 */
@RestController
@Slf4j
@RequestMapping({"/sys/client-business", "/sys/clientbusiness"})
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
    public PageResultVO<?> list(@RequestParam(value = "offset", defaultValue = "0") Integer offset,
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
    public ResultVO<ClientBusinessDetailVO> info(@PathVariable("id") Long id) {
        ClientBusiness cb = clientBusinessService.findById(id);
        return Result.ok(ClientBusinessConverter.toDetailVO(cb));
    }

    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody ClientBusinessForm form) {
        if (form == null || !StringUtils.hasText(form.getCorpname())) {
            return Result.error("公司名称不能为空");
        }
        ClientBusiness cb = ClientBusinessConverter.toEntity(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            cb.setCreateId(currentUser.getId().longValue());
            cb.setUpdateId(currentUser.getId().longValue());
        }
        boolean success = clientBusinessService.save(cb);
        return success ? Result.ok("新增成功") : Result.error("新增失败");
    }

    @PostMapping("/update")
    public ResultVO<?> update(@RequestBody ClientBusinessForm form) {
        if (form == null || form.getId() == null) {
            return Result.error("客户id不能为空");
        }
        ClientBusiness cb = ClientBusinessConverter.toEntity(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            cb.setUpdateId(currentUser.getId().longValue());
        }
        boolean success = clientBusinessService.update(cb);
        return success ? Result.ok("修改成功") : Result.error("修改失败");
    }

    @PostMapping("/del")
    public ResultVO<?> delete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要删除的数据");
        }
        boolean success = clientBusinessService.deleteBatch(ids);
        return success ? Result.ok("删除成功") : Result.error("删除失败");
    }

    @GetMapping("/all")
    public ResultVO<?> all() {
        SmsUser smsUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (smsUser == null) {
            log.info("clientbusiness all denied: not login");
            return Result.error(ExceptionEnums.NOT_LOGIN);
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

        return Result.ok(data);
    }

    /**
     * Client recharge endpoint.
     */
    @GetMapping("/pay")
    public ResultVO<?> pay(@RequestParam("jine") Long amount,
                           @RequestParam(value = "clientId", required = false) Long clientId) {
        if (amount == null || amount <= 0) {
            return Result.error("充值金额必须大于0");
        }

        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser == null) {
            return Result.error(ExceptionEnums.NOT_LOGIN);
        }

        Set<String> roleNameSet = roleService.getRoleName(currentUser.getId());
        boolean isRoot = roleNameSet != null && roleNameSet.contains(WebMasterConstants.ROOT);

        ClientBusiness target;
        if (clientId == null) {
            List<ClientBusiness> scope = isRoot
                    ? clientBusinessService.findAll()
                    : clientBusinessService.findByUserId(currentUser.getId());
            if (scope == null || scope.isEmpty()) {
                return Result.error("当前无可充值客户");
            }
            target = scope.get(0);
        } else {
            target = clientBusinessService.findById(clientId);
            if (target == null) {
                return Result.error("客户不存在");
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
                    return Result.error(ExceptionEnums.SMS_NO_AUTHOR);
                }
            }
        }

        try {
            ClientBalanceRechargeCommand command = new ClientBalanceRechargeCommand();
            command.setClientId(target.getId());
            command.setAmount(amount);
            command.setOperatorId(currentUser.getId().longValue());
            command.setRequestId(null);

            BalanceCommandResult commandResult = balanceCommandService.rechargeAndSync(command);
            if (!commandResult.isSuccess()) {
                return Result.error(commandResult.getCode(), commandResult.getMessage());
            }

            ResultVO<Map<String, Object>> resultVO = new ResultVO<>(0, "充值成功");
            Map<String, Object> data = new HashMap<>();
            data.put("clientId", target.getId());
            data.put("corpname", target.getCorpname());
            data.put("amount", amount);
            data.put("balance", commandResult.getBalance());
            resultVO.setData(data);
            return resultVO;
        } catch (IllegalArgumentException ex) {
            return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), ex.getMessage());
        }
    }
}
