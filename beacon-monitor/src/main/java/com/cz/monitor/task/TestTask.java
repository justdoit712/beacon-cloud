package com.cz.monitor.task;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author cz
 * @version V1.0.0
 */
@Component
@Slf4j
public class TestTask {

    @XxlJob("test")
    public void test(){
        // 编写任务逻辑
        log.info("Hello World!");
    }
}
