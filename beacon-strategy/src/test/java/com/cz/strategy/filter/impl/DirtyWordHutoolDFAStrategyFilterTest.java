package com.cz.strategy.filter.impl;

import cn.hutool.dfa.WordTree;
import com.cz.common.constant.CacheKeyConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.CacheFacade;
import com.cz.strategy.util.ErrorSendMsgUtil;
import com.cz.strategy.util.HutoolDFAUtil;
import com.cz.strategy.util.SpringUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DirtyWordHutoolDFAStrategyFilterTest {

    @Test
    public void shouldThrowAndSendFailureWhenDirtyWordMatched() {
        initWordTree(Collections.singleton("违禁词"));

        ErrorSendMsgUtil errorSendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        DirtyWordHutoolDFAStrategyFilter filter = new DirtyWordHutoolDFAStrategyFilter();
        ReflectionTestUtils.setField(filter, "errorSendMsgUtil", errorSendMsgUtil);
        ReflectionTestUtils.setField(filter, "rabbitTemplate", Mockito.mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class));

        StandardSubmit submit = new StandardSubmit();
        submit.setText("这是一条包含违禁词的短信");

        try {
            filter.strategy(submit);
            Assert.fail("expected StrategyException");
        } catch (StrategyException ex) {
            Assert.assertEquals(ExceptionEnums.ERROR_DIRTY_WORD.getCode(), ex.getCode());
        }

        Assert.assertTrue(submit.getErrorMsg().contains(ExceptionEnums.ERROR_DIRTY_WORD.getMsg()));
        verify(errorSendMsgUtil).sendWriteLog(submit);
        verify(errorSendMsgUtil).sendPushReport(submit);
    }

    @Test
    public void shouldPassWhenNoDirtyWordMatched() {
        initWordTree(Collections.singleton("违禁词"));

        ErrorSendMsgUtil errorSendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        DirtyWordHutoolDFAStrategyFilter filter = new DirtyWordHutoolDFAStrategyFilter();
        ReflectionTestUtils.setField(filter, "errorSendMsgUtil", errorSendMsgUtil);
        ReflectionTestUtils.setField(filter, "rabbitTemplate", Mockito.mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class));

        StandardSubmit submit = new StandardSubmit();
        submit.setText("这是一条正常通知短信");

        filter.strategy(submit);

        verify(errorSendMsgUtil, never()).sendWriteLog(submit);
        verify(errorSendMsgUtil, never()).sendPushReport(submit);
    }

    private static void initWordTree(java.util.Set<String> dirtyWords) {
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        CacheFacade cacheFacade = Mockito.mock(CacheFacade.class);
        when(applicationContext.getBean(CacheFacade.class)).thenReturn(cacheFacade);
        when(cacheFacade.sMembersString(CacheKeyConstants.DIRTY_WORD)).thenReturn(dirtyWords);

        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", applicationContext);

        WordTree wordTree = new WordTree();
        wordTree.addWords(dirtyWords);
        ReflectionTestUtils.setField(HutoolDFAUtil.class, "wordTree", wordTree);
    }
}
