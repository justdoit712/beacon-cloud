package com.cz.strategy.util;

import cn.hutool.dfa.WordTree;
import com.cz.common.model.constant.CacheConstant;
import com.cz.strategy.client.BeaconCacheClient;

import java.util.List;
import java.util.Set;

public class HutoolDFAUtil {

    private static WordTree wordTree = new WordTree();

    /**
     * 初始化敏感词树
     */
    static {
        // 获取Spring容器中的cacheClient
        BeaconCacheClient cacheClient = (BeaconCacheClient) SpringUtil.getBeanByClass(BeaconCacheClient.class);
        // 获取存储在Redis中的全部敏感词
        Set<String> dirtyWords = cacheClient.smember(CacheConstant.DIRTY_WORD);
        // 调用WordTree的add方法，将dfaMap的敏感词树构建
        wordTree.addWords(dirtyWords);
    }


    public static List<String> getDirtyWord(String text){
        return wordTree.matchAll(text);
    }

}
