package com.cz.api.filter;

import com.cz.common.model.StandardSubmit;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class CheckFilterContextTest {

    @Test
    public void shouldExecuteKnownFiltersInConfiguredOrder() {
        CheckFilterContext context = new CheckFilterContext();
        CheckFilter apiKey = Mockito.mock(CheckFilter.class);
        CheckFilter ip = Mockito.mock(CheckFilter.class);

        Map<String, CheckFilter> checkFiltersMap = new HashMap<>();
        checkFiltersMap.put("apikey", apiKey);
        checkFiltersMap.put("ip", ip);

        ReflectionTestUtils.setField(context, "checkFiltersMap", checkFiltersMap);
        ReflectionTestUtils.setField(context, "filters", "apikey,ip");

        StandardSubmit submit = new StandardSubmit();

        context.check(submit);

        InOrder inOrder = Mockito.inOrder(apiKey, ip);
        inOrder.verify(apiKey).check(submit);
        inOrder.verify(ip).check(submit);
    }

    @Test
    public void shouldIgnoreBlankFilterItems() {
        CheckFilterContext context = new CheckFilterContext();
        CheckFilter apiKey = Mockito.mock(CheckFilter.class);
        CheckFilter ip = Mockito.mock(CheckFilter.class);

        Map<String, CheckFilter> checkFiltersMap = new HashMap<>();
        checkFiltersMap.put("apikey", apiKey);
        checkFiltersMap.put("ip", ip);

        ReflectionTestUtils.setField(context, "checkFiltersMap", checkFiltersMap);
        ReflectionTestUtils.setField(context, "filters", "apikey, , ip ,,");

        StandardSubmit submit = new StandardSubmit();

        context.check(submit);

        verify(apiKey).check(submit);
        verify(ip).check(submit);
    }

    @Test
    public void shouldThrowClearErrorWhenFilterMissingAtRuntime() {
        CheckFilterContext context = new CheckFilterContext();
        CheckFilter apiKey = Mockito.mock(CheckFilter.class);

        Map<String, CheckFilter> checkFiltersMap = new HashMap<>();
        checkFiltersMap.put("apikey", apiKey);

        ReflectionTestUtils.setField(context, "checkFiltersMap", checkFiltersMap);
        ReflectionTestUtils.setField(context, "filters", "apikey,missing");

        try {
            context.check(new StandardSubmit());
            fail("expected IllegalStateException");
        } catch (IllegalStateException ex) {
            org.junit.Assert.assertTrue(ex.getMessage().contains("unknown check filter: missing"));
        }

        verify(apiKey).check(Mockito.any(StandardSubmit.class));
    }

    @Test
    public void shouldValidateConfiguredFiltersOnInit() {
        CheckFilterContext context = new CheckFilterContext();
        CheckFilter apiKey = Mockito.mock(CheckFilter.class);

        Map<String, CheckFilter> checkFiltersMap = new HashMap<>();
        checkFiltersMap.put("apikey", apiKey);

        ReflectionTestUtils.setField(context, "checkFiltersMap", checkFiltersMap);
        ReflectionTestUtils.setField(context, "filters", "apikey");

        context.validateFilters();

        verify(apiKey, never()).check(Mockito.any(StandardSubmit.class));
    }
}
