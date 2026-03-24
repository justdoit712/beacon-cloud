package com.cz.common.model;

import com.cz.common.util.JsonUtil;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.BeanUtils;

public class StandardReportContractTest {

    @Test
    public void shouldCopyApiKeyFromSubmitToReport() {
        StandardSubmit submit = new StandardSubmit();
        submit.setApiKey("ak_001");
        submit.setSequenceId(1001L);

        StandardReport report = new StandardReport();
        BeanUtils.copyProperties(submit, report);

        Assert.assertEquals("ak_001", report.getApiKey());
        Assert.assertEquals(Long.valueOf(1001L), report.getSequenceId());
    }

    @Test
    public void shouldSerializeApiKeyUsingCamelCase() {
        StandardReport report = new StandardReport();
        report.setApiKey("ak_001");

        String json = JsonUtil.toJson(report);

        Assert.assertTrue(json.contains("\"apiKey\":\"ak_001\""));
        Assert.assertFalse(json.contains("\"apikey\""));
    }
}
