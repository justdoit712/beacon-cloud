package com.cz.webmaster.controller;

import com.cz.common.util.R;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.entity.ScheduleLog;
import com.cz.webmaster.service.ScheduleLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/schedule/log", "/sys/log"})
public class ScheduleLogController {

    @Autowired
    private ScheduleLogService scheduleLogService;

    @GetMapping("/list")
    public ResultVO list(@RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "10") int limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        long total = scheduleLogService.count(keyword);
        List<ScheduleLog> rows = scheduleLogService.list(keyword, offset, limit);
        return R.ok(total, rows);
    }

    @PostMapping("/del")
    public ResultVO del(@RequestBody List<Long> logIds) {
        try {
            boolean success = scheduleLogService.deleteBatch(logIds);
            if (success) {
                ResultVO resultVO = R.ok();
                resultVO.setMsg("delete success");
                return resultVO;
            }
            return R.error("delete failed");
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }
}
