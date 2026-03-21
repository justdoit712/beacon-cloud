package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.controller.support.OperatorContextUtils;
import com.cz.webmaster.dto.CacheRebuildReport;
import com.cz.webmaster.service.CacheSyncService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 手工重建管理入口。
 */
@RestController
@RequestMapping({"/admin/cache", "/sys/cache"})
public class CacheRebuildController {

    private final CacheSyncService cacheSyncService;

    public CacheRebuildController(CacheSyncService cacheSyncService) {
        this.cacheSyncService = cacheSyncService;
    }

    @PostMapping("/rebuild")
    public ResultVO rebuild(@RequestParam("domain") String domain) {
        if (!StringUtils.hasText(domain)) {
            return Result.error("domain is required");
        }
        CacheRebuildReport report = cacheSyncService.rebuildDomain(domain);
        applyOperator(report, OperatorContextUtils.currentOperatorId());
        return Result.ok(report);
    }

    private void applyOperator(CacheRebuildReport report, Long operatorId) {
        if (report == null) {
            return;
        }
        report.setOperator(operatorId);
        List<CacheRebuildReport> childReports = report.getReports();
        if (childReports == null || childReports.isEmpty()) {
            return;
        }
        for (CacheRebuildReport childReport : childReports) {
            applyOperator(childReport, operatorId);
        }
    }
}
