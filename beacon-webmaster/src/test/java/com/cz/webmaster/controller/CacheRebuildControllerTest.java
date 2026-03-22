package com.cz.webmaster.controller;

import com.cz.common.vo.ResultVO;
import com.cz.webmaster.dto.CacheRebuildReport;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.CacheRebuildService;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

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
        CacheRebuildController controller = new CacheRebuildController(cacheRebuildService);

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

        ResultVO result = controller.rebuild("ALL");

        Assert.assertEquals(Integer.valueOf(0), result.getCode());
        Assert.assertTrue(result.getData() instanceof CacheRebuildReport);
        CacheRebuildReport data = (CacheRebuildReport) result.getData();
        Assert.assertEquals(Long.valueOf(77L), data.getOperator());
        Assert.assertEquals(Long.valueOf(77L), data.getReports().get(0).getOperator());
        verify(cacheRebuildService, times(1)).rebuildDomain("ALL");
    }
}
