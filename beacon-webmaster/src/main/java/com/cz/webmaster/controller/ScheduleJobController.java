package com.cz.webmaster.controller;

import com.cz.common.util.R;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.entity.ScheduleJob;
import com.cz.webmaster.service.ScheduleJobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/schedule/job", "/sys/job"})
public class ScheduleJobController {

    private final ScheduleJobService scheduleJobService;

    public ScheduleJobController(ScheduleJobService scheduleJobService) {
        this.scheduleJobService = scheduleJobService;
    }

    @GetMapping("/list")
    public ResultVO list(@RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "10") int limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        long total = scheduleJobService.count(keyword);
        List<ScheduleJob> rows = scheduleJobService.list(keyword, offset, limit);
        return R.ok(total, rows);
    }

    @GetMapping("/info/{jobId}")
    public Map<String, Object> info(@PathVariable("jobId") Long jobId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scheduleJob", scheduleJobService.findById(jobId));
        return result;
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody ScheduleJob scheduleJob) {
        try {
            boolean success = scheduleJobService.save(scheduleJob);
            return success ? R.ok("save success") : R.error("save failed");
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody ScheduleJob scheduleJob) {
        try {
            boolean success = scheduleJobService.update(scheduleJob);
            return success ? R.ok("update success") : R.error("update failed");
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @PostMapping("/del")
    public ResultVO del(@RequestBody List<Long> jobIds) {
        try {
            boolean success = scheduleJobService.deleteBatch(jobIds);
            return success ? R.ok("delete success") : R.error("delete failed");
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @PostMapping("/pause")
    public ResultVO pause(@RequestBody List<Long> jobIds) {
        try {
            boolean success = scheduleJobService.pauseBatch(jobIds);
            return success ? R.ok("pause success") : R.error("pause failed");
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @PostMapping("/resume")
    public ResultVO resume(@RequestBody List<Long> jobIds) {
        try {
            boolean success = scheduleJobService.resumeBatch(jobIds);
            return success ? R.ok("resume success") : R.error("resume failed");
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @PostMapping("/run")
    public ResultVO run(@RequestBody List<Long> jobIds) {
        try {
            boolean success = scheduleJobService.runBatch(jobIds);
            return success ? R.ok("run success") : R.error("run failed");
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }
}
