package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.controller.support.OperatorContextUtils;
import com.cz.webmaster.dto.CacheRebuildReport;
import com.cz.webmaster.service.CacheRebuildService;
import com.cz.webmaster.service.SmsRoleService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 缓存手工重建管理控制器。
 *
 * <p>对外提供缓存手工重建入口，负责完成请求参数校验、登录态校验、
 * 管理员权限校验，并将重建结果以统一响应结构返回给调用方。</p>
 */
@RestController
@RequestMapping({"/admin/cache", "/sys/cache"})
public class CacheRebuildController {

    private final CacheRebuildService cacheRebuildService;
    private final SmsRoleService roleService;

    /**
     * 创建缓存手工重建控制器。
     *
     * @param cacheRebuildService 缓存重建协调服务
     * @param roleService 角色查询服务
     */
    public CacheRebuildController(CacheRebuildService cacheRebuildService,
                                 SmsRoleService roleService) {
        this.cacheRebuildService = cacheRebuildService;
        this.roleService = roleService;
    }

    /**
     * 手工触发指定缓存域重建。
     *
     * <p>该接口仅允许已登录且具备管理员角色的用户访问。
     * 当传入 {@code ALL} 时，表示由协调层决定当前可执行的域范围。</p>
     *
     * @param domain 缓存域编码或 {@code ALL}
     * @return 包含结构化重建报告的统一响应对象
     */
    @PostMapping("/rebuild")
    public ResultVO<?> rebuild(@RequestParam("domain") String domain) {
        if (!StringUtils.hasText(domain)) {
            return Result.error("缓存域不能为空");
        }
        Long operatorId = OperatorContextUtils.currentOperatorId();
        if (operatorId == null) {
            return Result.error(ExceptionEnums.NOT_LOGIN);
        }
        Integer operator = toInteger(operatorId);
        if (operator == null) {
            return Result.error(ExceptionEnums.CACHE_REBUILD_NO_AUTHOR);
        }
        Set<String> roleNames = roleService.getRoleName(operator);
        if (roleNames == null || !roleNames.contains(WebMasterConstants.ROOT)) {
            return Result.error(ExceptionEnums.CACHE_REBUILD_NO_AUTHOR);
        }

        CacheRebuildReport report = cacheRebuildService.rebuildDomain(domain);
        applyOperator(report, operatorId);
        return Result.ok(report);
    }

    /**
     * 将长整型操作人 id 安全转换为整型用户 id。
     *
     * @param value 长整型操作人 id
     * @return 可安全转换时返回整型 id，否则返回 {@code null}
     */
    private Integer toInteger(Long value) {
        if (value == null || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            return null;
        }
        return value.intValue();
    }

    /**
     * 为重建报告及其子报告补齐操作人信息。
     *
     * @param report 当前重建报告
     * @param operatorId 当前操作人 id
     */
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
