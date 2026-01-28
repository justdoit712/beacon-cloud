package com.cz.strategy.filter.impl;

import com.cz.common.model.model.StandardSubmit;
import com.cz.strategy.client.BeaconCacheClient;
import com.cz.strategy.filter.StrategyFilter;
import com.cz.strategy.util.DFAUtil;
import com.cz.strategy.util.HutoolDFAUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service(value = "hutoolDFADirtyWord")
@Slf4j
public class DirtyWordHutoolDFAStrategyFilter implements StrategyFilter {


    @Override
    public void strategy(StandardSubmit submit) {
        log.info("【策略模块-敏感词校验-hutoolDFADirtyWord】   校验ing…………");
        //1、 获取短信内容
        String text = submit.getText();

        //2、 调用DFA查看敏感词
        List<String> dirtyWords = HutoolDFAUtil.getDirtyWord(text);

        //4、 根据返回的set集合，判断是否包含敏感词
        if (dirtyWords != null && dirtyWords.size() > 0) {
            //5、 如果有敏感词，抛出异常 / 其他操作。。
            log.info("【策略模块-敏感词校验】   短信内容包含敏感词信息， dirtyWords = {}", dirtyWords);
            // 还需要做其他处理
        }
    }
}


