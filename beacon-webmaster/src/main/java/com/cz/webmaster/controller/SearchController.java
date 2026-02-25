package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.util.R;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.client.SearchClient;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.ClientBusinessService;
import com.cz.webmaster.service.SmsRoleService;
import com.cz.webmaster.vo.SearchSmsVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 应对页面的一个搜索功能
 *
 * @author cz
 * @description
 */
@RestController
@Slf4j
@RequestMapping("/sys/search")
public class SearchController {

    @Autowired
    private SmsRoleService roleService;

    @Autowired
    private SearchClient searchClient;

    @Autowired
    private ClientBusinessService clientBusinessService;

    @GetMapping("/list")
    public ResultVO list(@RequestParam Map map) {
        //1、判断当前登录用户的角色，能否查询对应的客户信息
        //1.1 查看用户是否登录
        SmsUser smsUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (smsUser == null) {
            log.info("【获取客户信息】 用户未登录！！");
            return R.error(ExceptionEnums.NOT_LOGIN);
        }
        //尝试从前端传来的请求参数 map 中获取用户想查的“客户ID”
        //前端可能会传 clientID，也可能不传
        String clientIDStr = (String) map.get("clientID");
        Long clientID = null;
        //如果传了，就把它由字符串转成 Long 类型
        //如果没传，clientID 就是 null，也就是代表用户想查“全部”
        if (!StringUtils.isEmpty(clientIDStr)) {
            clientID = Long.parseLong(clientIDStr);
        }
        //1.2 拿到用户的id标识，查看用户的角色是否是管理员
        Set<String> roleNames = roleService.getRoleName(smsUser.getId());

        if (roleNames != null && !roleNames.contains(WebMasterConstants.ROOT)) {
            //1.3 如果不是管理员，需要查询当前用户对应的公司信息，匹配参数中的公司id是否一致
            // 查询当前登录用户所包含的全部客户id（公司信息）
            List<ClientBusiness> clients = clientBusinessService.findByUserId(smsUser.getId());
            // 查看请求参数中携带的clientID
            if (clientID == null) {
                // 客户没有传递clientID，查询当前用户锁拥有的全部客户的短信信息
                List<Long> list = new ArrayList<>();
                for (ClientBusiness client : clients) {
                    list.add(client.getId());
                }
                map.put("clientID", list);
            } else {
                boolean flag = false;
                // 客户传递了clientID，判断当前登录用户是否包含当前clientID
                for (ClientBusiness client : clients) {
                    if (client.getId() == clientID) {
                        // 满足当前登录用户的操作，可以查询
                        flag = true;
                        break;
                    }
                }
                if (!flag) {
                    // 请求参数不满足当前登录用户的信息
                    log.info("【搜索短信信息】 用户权限不足！！");
                    return R.error(ExceptionEnums.SMS_NO_AUTHOR);
                }
            }
        }

        //2、调用搜索模块查询数据，返回total和rows
        Map<String, Object> data = searchClient.findSmsByParameters(map);

        //3、判断返回的total，如果total为0，正常返回
        Long total = Long.parseLong(data.get("total") + "");
        if (total == 0) {
            return R.ok(0L, new ArrayList<>());
        }
        //4、如果数据正常，做返回数据的封装，声明SearchSmsVO的实体类
        List<Map> list = (List<Map>) data.get("rows");
        List<SearchSmsVO> rows = new ArrayList<>();
        // 遍历集合，封装数据
        for (Map row : list) {
            SearchSmsVO vo = new SearchSmsVO();
            try {
                BeanUtils.copyProperties(vo,row);
            } catch (Exception e) {
                e.printStackTrace();
            }
            rows.add(vo);
        }

        //5、响应数据
        return R.ok(total, rows);
    }


}
