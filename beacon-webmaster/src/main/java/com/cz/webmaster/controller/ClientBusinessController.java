package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.ClientBusinessService;
import com.cz.webmaster.service.SmsRoleService;
import com.cz.webmaster.vo.ClientBusinessVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
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
public class ClientBusinessController {


    @Autowired
    private SmsRoleService roleService;

    @Autowired
    private ClientBusinessService clientBusinessService;


    @GetMapping("/sys/clientbusiness/all")
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


}
