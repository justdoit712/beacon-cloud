package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
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

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping({"/schedule/job", "/sys/job"})
public class ScheduleJobController {

    private final ScheduleJobService scheduleJobService;

    public ScheduleJobController(ScheduleJobService scheduleJobService) {
        this.scheduleJobService = scheduleJobService;
    }

    @GetMapping("/list")
    public PageResultVO<?> list(@RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "10") int limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        long total = scheduleJobService.count(keyword);
        List<ScheduleJob> rows = scheduleJobService.list(keyword, offset, limit);
        return Result.ok(total, rows);
    }

    @GetMapping("/info/{jobId}")
    public ResultVO<?> info(@PathVariable("jobId") Long jobId) {
        return Result.ok(Collections.singletonMap("scheduleJob", scheduleJobService.findById(jobId)));
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody ScheduleJob scheduleJob) {
        try {
            boolean success = scheduleJobService.save(scheduleJob);
            return success ? Result.ok("save success") : Result.error("save failed");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody ScheduleJob scheduleJob) {
        try {
            boolean success = scheduleJobService.update(scheduleJob);
            return success ? Result.ok("update success") : Result.error("update failed");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/del")
    public ResultVO del(@RequestBody List<Long> jobIds) {
        try {
            boolean success = scheduleJobService.deleteBatch(jobIds);
            return success ? Result.ok("delete success") : Result.error("delete failed");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/pause")
    public ResultVO pause(@RequestBody List<Long> jobIds) {
        try {
            boolean success = scheduleJobService.pauseBatch(jobIds);
            return success ? Result.ok("pause success") : Result.error("pause failed");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/resume")
    public ResultVO resume(@RequestBody List<Long> jobIds) {
        try {
            boolean success = scheduleJobService.resumeBatch(jobIds);
            return success ? Result.ok("resume success") : Result.error("resume failed");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/run")
    public ResultVO run(@RequestBody List<Long> jobIds) {
        try {
            boolean success = scheduleJobService.runBatch(jobIds);
            return success ? Result.ok("run success") : Result.error("run failed");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}


