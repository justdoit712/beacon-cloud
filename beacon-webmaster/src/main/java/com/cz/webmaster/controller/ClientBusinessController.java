package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.util.R;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.ClientBusinessService;
import com.cz.webmaster.service.SmsRoleService;
import com.cz.webmaster.vo.ClientBusinessVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户信息Controller
 * @author cz
 * @description
 */
@RestController
@Slf4j
@RequestMapping("/sys/clientbusiness")
public class ClientBusinessController {


    @Autowired
    private SmsRoleService roleService;

    @Autowired
    private ClientBusinessService clientBusinessService;


    @GetMapping("/list")
    public ResultVO list(@RequestParam Map<String, Object> params) {
        int offset = parseInt(params.get("offset"), 0);
        int limit = parseInt(params.get("limit"), 10);
        String keyword = toStr(params.get("search"));

        List<ClientBusiness> list = clientBusinessService.findByKeyword(keyword);
        long total = clientBusinessService.countByKeyword(keyword);

        int fromIndex = Math.min(offset, list.size());
        int toIndex = Math.min(offset + limit, list.size());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ClientBusiness cb : list.subList(fromIndex, toIndex)) {
            rows.add(toDetailView(cb));
        }
        return R.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        ClientBusiness cb = clientBusinessService.findById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("clientbusiness", toDetailView(cb));
        return result;
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody Map<String, Object> form) {
        if (form == null || !StringUtils.hasText(toStr(form.get("corpname")))) {
            return error("公司名称不能为空");
        }
        ClientBusiness cb = fromDetailForm(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            cb.setCreateId(currentUser.getId().longValue());
            cb.setUpdateId(currentUser.getId().longValue());
        }
        boolean success = clientBusinessService.save(cb);
        return success ? success("新增成功") : error("新增失败");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody Map<String, Object> form) {
        if (form == null || form.get("id") == null) {
            return error("客户id不能为空");
        }
        ClientBusiness cb = fromDetailForm(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            cb.setUpdateId(currentUser.getId().longValue());
        }
        boolean success = clientBusinessService.update(cb);
        return success ? success("修改成功") : error("修改失败");
    }

    @PostMapping("/del")
    public ResultVO delete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return error("请选择要删除的数据");
        }
        boolean success = clientBusinessService.deleteBatch(ids);
        return success ? success("删除成功") : error("删除失败");
    }

    @GetMapping("/all")
    public Map<String, Object> all(){
        //1、拿到当前登录用户的信息
        SmsUser smsUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if(smsUser == null){
            log.info("【获取客户信息】 用户未登录！！");
            Map<String, Object> result = new HashMap<>();
            result.put("code", ExceptionEnums.NOT_LOGIN.getCode());
            result.put("msg", ExceptionEnums.NOT_LOGIN.getMsg());
            return result;
        }
        Integer userId = smsUser.getId();
        //2、查询当前用户的角色信息
        Set<String> roleNameSet = roleService.getRoleName(userId);

        //3、根据角色信息查询数据即可。
        List<ClientBusiness> list = null;
        if(roleNameSet != null && roleNameSet.contains(WebMasterConstants.ROOT)){
            // 查询全部即可
            list = clientBusinessService.findAll();
        }else{
            // 根据用户id查询指定的公司信息
            list = clientBusinessService.findByUserId(userId);
        }
        List<ClientBusinessVO> data = new ArrayList<>();
        for (ClientBusiness clientBusiness : list) {
            ClientBusinessVO vo = new ClientBusinessVO();
            BeanUtils.copyProperties(clientBusiness,vo);
            data.add(vo);
        }
        //4、响应数据
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("msg", "");
        result.put("data", data);
        result.put("sites", data);
        return result;
    }

    private ClientBusiness fromDetailForm(Map<String, Object> form) {
        ClientBusiness cb = new ClientBusiness();
        Object idObj = form.get("id");
        if (idObj != null) {
            cb.setId(Long.parseLong(String.valueOf(idObj)));
        }
        cb.setCorpname(toStr(form.get("corpname")));
        cb.setApikey(toStr(form.get("usercode")));
        cb.setIpAddress(toStr(form.get("ipaddress")));
        String isReturn = toStr(form.get("isreturnstatus"));
        if (isReturn != null) {
            cb.setIsCallback(Byte.parseByte(isReturn));
        }
        cb.setCallbackUrl(toStr(form.get("receivestatusurl")));
        cb.setClientPhone(toStr(form.get("mobile")));
        String priority = toStr(form.get("priority"));
        if (priority != null) {
            cb.setExtend1(priority);
        }
        String usertype = toStr(form.get("usertype"));
        if (usertype != null) {
            cb.setExtend2(usertype);
        }
        String state = toStr(form.get("state"));
        if (state != null) {
            cb.setExtend3(state);
        }
        String money = toStr(form.get("money"));
        if (money != null) {
            cb.setExtend4(money);
        }
        String pwd = toStr(form.get("pwd"));
        if (pwd != null) {
            cb.setClientLinkname(pwd);
        }
        return cb;
    }

    private Map<String, Object> toDetailView(ClientBusiness cb) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (cb == null) {
            return data;
        }
        data.put("id", cb.getId());
        data.put("corpname", cb.getCorpname());
        data.put("usercode", cb.getApikey());
        data.put("pwd", cb.getClientLinkname());
        data.put("ipaddress", cb.getIpAddress());
        data.put("isreturnstatus", cb.getIsCallback());
        data.put("receivestatusurl", cb.getCallbackUrl());
        data.put("mobile", cb.getClientPhone());
        data.put("priority", cb.getExtend1());
        data.put("usertype", cb.getExtend2());
        data.put("state", cb.getExtend3());
        data.put("money", cb.getExtend4());
        return data;
    }

    private ResultVO success(String msg) {
        ResultVO resultVO = R.ok();
        resultVO.setMsg(msg);
        return resultVO;
    }

    private ResultVO error(String msg) {
        return new ResultVO(-1, msg);
    }

    private String toStr(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
