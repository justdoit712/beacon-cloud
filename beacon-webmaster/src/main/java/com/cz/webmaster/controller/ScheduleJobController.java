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
    public ResultVO<ScheduleJob> info(@PathVariable("jobId") Long jobId) {
        return Result.ok(scheduleJobService.findById(jobId));
    }

    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody ScheduleJob scheduleJob) {
        boolean success = scheduleJobService.save(scheduleJob);
        return success ? Result.ok("新增成功") : Result.error("新增失败");
    }

    @PostMapping("/update")
    public ResultVO<?> update(@RequestBody ScheduleJob scheduleJob) {
        boolean success = scheduleJobService.update(scheduleJob);
        return success ? Result.ok("修改成功") : Result.error("修改失败");
    }

    @PostMapping("/del")
    public ResultVO<?> del(@RequestBody List<Long> jobIds) {
        boolean success = scheduleJobService.deleteBatch(jobIds);
        return success ? Result.ok("删除成功") : Result.error("删除失败");
    }

    @PostMapping("/pause")
    public ResultVO<?> pause(@RequestBody List<Long> jobIds) {
        boolean success = scheduleJobService.pauseBatch(jobIds);
        return success ? Result.ok("暂停成功") : Result.error("暂停失败");
    }

    @PostMapping("/resume")
    public ResultVO<?> resume(@RequestBody List<Long> jobIds) {
        boolean success = scheduleJobService.resumeBatch(jobIds);
        return success ? Result.ok("恢复成功") : Result.error("恢复失败");
    }

    @PostMapping("/run")
    public ResultVO<?> run(@RequestBody List<Long> jobIds) {
        boolean success = scheduleJobService.runBatch(jobIds);
        return success ? Result.ok("执行成功") : Result.error("执行失败");
    }
}


