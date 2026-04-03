package com.cz.api.advice;

import com.cz.api.vo.SmsSendResultVO;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.ApiException;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;

public class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    public void shouldMapBizExceptionToBusinessCode() {
        SmsSendResultVO result = handler.handleBizException(new ApiException(ExceptionEnums.ERROR_APIKEY));

        Assert.assertEquals(ExceptionEnums.ERROR_APIKEY.getCode(), result.getCode());
        Assert.assertEquals(ExceptionEnums.ERROR_APIKEY.getMsg(), result.getMsg());
    }

    @Test
    public void shouldMapBadJsonToParameterError() {
        SmsSendResultVO result = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("bad json")
        );

        Assert.assertEquals(ExceptionEnums.PARAMETER_ERROR.getCode(), result.getCode());
        Assert.assertEquals("请求体格式错误", result.getMsg());
    }

    @Test
    public void shouldMapIllegalArgumentToParameterError() {
        SmsSendResultVO result = handler.handleIllegalArgumentException(new IllegalArgumentException("state must not be null"));

        Assert.assertEquals(ExceptionEnums.PARAMETER_ERROR.getCode(), result.getCode());
        Assert.assertEquals("state must not be null", result.getMsg());
    }

    @Test
    public void shouldMapUnknownExceptionToUnknownError() {
        SmsSendResultVO result = handler.handleUnknownException(new RuntimeException("boom"));

        Assert.assertEquals(ExceptionEnums.UNKNOWN_ERROR.getCode(), result.getCode());
        Assert.assertEquals(ExceptionEnums.UNKNOWN_ERROR.getMsg(), result.getMsg());
    }
}
