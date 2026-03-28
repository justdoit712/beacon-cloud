package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.entity.ClientChannel;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.ClientChannelService;
import com.cz.webmaster.vo.ClientChannelVO;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户通道管理
 */
@RestController
@RequestMapping({"/sys/client-channel", "/sys/clientchannel"})
public class SysClientChannelController {

    private final ClientChannelService clientChannelService;

    public SysClientChannelController(ClientChannelService clientChannelService) {
        this.clientChannelService = clientChannelService;
    }

    @GetMapping("/list")
    public PageResultVO<?> list(@RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "10") int limit,
                         @RequestParam(value = "search", required = false) String keyword) {

        long total = clientChannelService.countByKeyword(keyword);
        List<ClientChannel> list = clientChannelService.findListByPage(keyword, offset, limit);

        List<ClientChannelVO> voList = new ArrayList<>();
        for (ClientChannel entity : list) {
            ClientChannelVO vo = new ClientChannelVO();
            BeanUtils.copyProperties(entity, vo);
            voList.add(vo);
        }

        return Result.ok(total, voList);
    }

    @GetMapping("/info/{id}")
    public ResultVO<ClientChannelVO> info(@PathVariable("id") Long id) {
        ClientChannel entity = clientChannelService.findById(id);

        ClientChannelVO vo = new ClientChannelVO();
        if (entity != null) {
            BeanUtils.copyProperties(entity, vo);
        }
        return Result.ok(vo);
    }

    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody ClientChannelVO clientChannelVO) {
        if (clientChannelVO == null
                || clientChannelVO.getClientId() == null
                || clientChannelVO.getChannelId() == null
                || !StringUtils.hasText(clientChannelVO.getExtendNumber())
                || clientChannelVO.getPrice() == null) {
            return Result.error("所属客户、所属通道、扩展号、价格不能为空");
        }

        ClientChannel entity = new ClientChannel();
        BeanUtils.copyProperties(clientChannelVO, entity);

        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            entity.setCreateId(currentUser.getId().longValue());
            entity.setUpdateId(currentUser.getId().longValue());
        }

        return clientChannelService.save(entity) ? Result.ok("新增成功") : Result.error("新增失败");
    }

    @PostMapping("/update")
    public ResultVO<?> update(@RequestBody ClientChannelVO clientChannelVO) {
        if (clientChannelVO == null || clientChannelVO.getId() == null) {
            return Result.error("客户通道id不能为空");
        }

        ClientChannel entity = new ClientChannel();
        BeanUtils.copyProperties(clientChannelVO, entity);

        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            entity.setUpdateId(currentUser.getId().longValue());
        }

        return clientChannelService.update(entity) ? Result.ok("修改成功") : Result.error("修改失败");
    }

    @PostMapping("/del")
    public ResultVO<?> delete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要删除的数据");
        }

        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        Long updateId = currentUser == null ? null : currentUser.getId().longValue();

        boolean result = clientChannelService.deleteBatch(ids, updateId);
        return result ? Result.ok("删除成功") : Result.error("删除失败");
    }
}


