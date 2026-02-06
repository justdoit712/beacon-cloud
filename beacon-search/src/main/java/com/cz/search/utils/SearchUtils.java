package com.cz.search.utils;

import com.cz.common.model.StandardReport;

import java.time.LocalDateTime;

/**
 * @author cz
 *
 */
public class SearchUtils {

    /**
     * 索引前缀
     */
    public static final String INDEX = "sms_submit_log_";

    /**
     * 获取年份信息
     * @return
     */
    public static String getYear(){
        return LocalDateTime.now().getYear() + "";
    }

    public static String getCurrYearIndex(){
        return INDEX + getYear();
    }


    // ThreadLocal操作
    private static ThreadLocal<StandardReport> reportThreadLocal = new ThreadLocal<>();


    public static void set(StandardReport report){
        reportThreadLocal.set(report);
    }

    public static StandardReport get(){
        return reportThreadLocal.get();
    }

    public static void remove(){
        reportThreadLocal.remove();
    }

}
