package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.dto.CacheRebuildReport;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.CacheRebuildService;
import com.cz.webmaster.service.SmsRoleService;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CacheRebuildControllerTest {

    @After
    public void tearDown() {
        ThreadContext.unbindSubject();
    }

    @Test
    public void shouldReturnStructuredReportAndInjectOperator() {
        CacheRebuildService cacheRebuildService = Mockito.mock(CacheRebuildService.class);
        SmsRoleService roleService = Mockito.mock(SmsRoleService.class);
        CacheRebuildController controller = new CacheRebuildController(cacheRebuildService, roleService);

        CacheRebuildReport childReport = new CacheRebuildReport();
        childReport.setDomain("client_business");
        childReport.setStatus("SKELETON");

        CacheRebuildReport report = new CacheRebuildReport();
        report.setDomain("ALL");
        report.setTrigger("MANUAL");
        report.setStatus("SKELETON");
        report.setReports(Collections.singletonList(childReport));

        when(cacheRebuildService.rebuildDomain("ALL")).thenReturn(report);

        SmsUser currentUser = new SmsUser();
        currentUser.setId(77);
        Subject subject = Mockito.mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(currentUser);
        ThreadContext.bind(subject);
        Set<String> roleNames = new HashSet<>();
        roleNames.add(WebMasterConstants.ROOT);
        when(roleService.getRoleName(77)).thenReturn(roleNames);

        ResultVO result = controller.rebuild("ALL");

        Assert.assertEquals(Integer.valueOf(0), result.getCode());
        Assert.assertTrue(result.getData() instanceof CacheRebuildReport);
        CacheRebuildReport data = (CacheRebuildReport) result.getData();
        Assert.assertEquals(Long.valueOf(77L), data.getOperator());
        Assert.assertEquals(Long.valueOf(77L), data.getReports().get(0).getOperator());
        verify(cacheRebuildService, times(1)).rebuildDomain("ALL");
    }

    @Test
    public void shouldReturnNotLoginWhenOperatorMissing() {
        CacheRebuildService cacheRebuildService = Mockito.mock(CacheRebuildService.class);
        SmsRoleService roleService = Mockito.mock(SmsRoleService.class);
        CacheRebuildController controller = new CacheRebuildController(cacheRebuildService, roleService);

        Subject subject = Mockito.mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(null);
        ThreadContext.bind(subject);

        ResultVO result = controller.rebuild("ALL");

        Assert.assertEquals(Integer.valueOf(-102), result.getCode());
        verify(cacheRebuildService, times(0)).rebuildDomain(Mockito.anyString());
    }

    @Test
    public void shouldReturnNoAuthorWhenOperatorIsNotRoot() {
        CacheRebuildService cacheRebuildService = Mockito.mock(CacheRebuildService.class);
        SmsRoleService roleService = Mockito.mock(SmsRoleService.class);
        CacheRebuildController controller = new CacheRebuildController(cacheRebuildService, roleService);

        SmsUser currentUser = new SmsUser();
        currentUser.setId(88);
        Subject subject = Mockito.mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(currentUser);
        ThreadContext.bind(subject);
        when(roleService.getRoleName(88)).thenReturn(Collections.singleton("普通用户"));

        ResultVO result = controller.rebuild("ALL");

        Assert.assertEquals(Integer.valueOf(-105), result.getCode());
        verify(cacheRebuildService, times(0)).rebuildDomain(Mockito.anyString());
    }
}
