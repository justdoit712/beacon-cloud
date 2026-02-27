package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.entity.Channel;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.ChannelService;
import com.cz.webmaster.vo.ChannelVO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/channel")
public class SysChannelController {

    private final ChannelService channelService;

    public SysChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping("/list")
    public ResultVO list(@RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "10") int limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        long total = channelService.countByKeyword(keyword);
        List<Channel> list = channelService.findListByPage(keyword, offset, limit);

        List<ChannelVO> voList = new ArrayList<>();
        for (Channel entity : list) {
            ChannelVO vo = new ChannelVO();
            BeanUtils.copyProperties(entity, vo);
            voList.add(vo);
        }
        return Result.ok(total, voList);
    }

    @GetMapping("/all")
    public Map<String, Object> all() {
        List<Channel> list = channelService.findAllActive();
        List<ChannelVO> voList = new ArrayList<>();
        for (Channel entity : list) {
            ChannelVO vo = new ChannelVO();
            BeanUtils.copyProperties(entity, vo);
            voList.add(vo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("msg", "");
        result.put("channelsites", voList);
        result.put("data", voList);
        return result;
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        Channel entity = channelService.findById(id);
        ChannelVO vo = new ChannelVO();
        if (entity != null) {
            BeanUtils.copyProperties(entity, vo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("channel", vo);
        return result;
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody ChannelVO channelVO) {
        if (channelVO == null
                || !StringUtils.hasText(channelVO.getChannelName())
                || channelVO.getChannelType() == null
                || !StringUtils.hasText(channelVO.getChannelArea())
                || channelVO.getChannelPrice() == null
                || channelVO.getProtocolType() == null) {
            return Result.error("通道名称、通道类型、通道地区、通道成本、协议类型不能为空");
        }

        Channel entity = new Channel();
        BeanUtils.copyProperties(channelVO, entity);

        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            entity.setCreateId(currentUser.getId().longValue());
            entity.setUpdateId(currentUser.getId().longValue());
        }
        return channelService.save(entity) ? Result.ok("新增成功") : Result.error("新增失败");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody ChannelVO channelVO) {
        if (channelVO == null || channelVO.getId() == null) {
            return Result.error("通道id不能为空");
        }

        Channel entity = new Channel();
        BeanUtils.copyProperties(channelVO, entity);

        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            entity.setUpdateId(currentUser.getId().longValue());
        }
        return channelService.update(entity) ? Result.ok("修改成功") : Result.error("修改失败");
    }

    @PostMapping("/del")
    public ResultVO delete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要删除的数据");
        }
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        Long updateId = currentUser == null ? null : currentUser.getId().longValue();
        return channelService.deleteBatch(ids, updateId) ? Result.ok("删除成功") : Result.error("删除失败");
    }
}

