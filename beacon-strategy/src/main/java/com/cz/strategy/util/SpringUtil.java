package com.cz.strategy.util;



import com.cz.common.model.constant.CacheConstant;
import com.cz.strategy.client.BeaconCacheClient;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.google.common.collect.ConcurrentHashMultiset.create;

@Component
public class SpringUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringUtil.applicationContext = applicationContext;
    }

    public static Object getBeanByName(String beanName){
        return SpringUtil.applicationContext.getBean(beanName);
    }

    public static Object getBeanByClass(Class clazz){
        return SpringUtil.applicationContext.getBean(clazz);
    }
}

