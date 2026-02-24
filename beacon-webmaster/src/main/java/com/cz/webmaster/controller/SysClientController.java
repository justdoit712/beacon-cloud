package com.cz.webmaster.controller;

import com.cz.common.util.R;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.ClientBusinessService;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户信息管理接口
 * 对应前端 client.html / client.js
 */
@RestController
@RequestMapping("/sys/client")
public class SysClientController {

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
            rows.add(toView(cb));
        }
        return R.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        ClientBusiness cb = clientBusinessService.findById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("client", toView(cb));
        return result;
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody Map<String, Object> form) {
        if (form == null || !StringUtils.hasText(toStr(form.get("corpname")))) {
            return error("公司名称不能为空");
        }
        ClientBusiness cb = fromForm(form);
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
        ClientBusiness cb = fromForm(form);
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

    private ClientBusiness fromForm(Map<String, Object> form) {
        ClientBusiness cb = new ClientBusiness();
        Object idObj = form.get("id");
        if (idObj != null) {
            cb.setId(Long.parseLong(String.valueOf(idObj)));
        }
        cb.setCorpname(toStr(form.get("corpname")));
        cb.setClientLinkname(toStr(form.get("linkman")));
        cb.setClientPhone(toStr(form.get("mobile")));
        cb.setExtend2(toStr(form.get("address")));
        cb.setExtend3(toStr(form.get("email")));
        cb.setExtend4(toStr(form.get("customermanager")));
        return cb;
    }

    private Map<String, Object> toView(ClientBusiness cb) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (cb == null) {
            return data;
        }
        data.put("id", cb.getId());
        data.put("corpname", cb.getCorpname());
        data.put("address", cb.getExtend2());
        data.put("linkman", cb.getClientLinkname());
        data.put("mobile", cb.getClientPhone());
        data.put("email", cb.getExtend3());
        data.put("customermanager", cb.getExtend4());
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
