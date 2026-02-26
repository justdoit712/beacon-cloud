package com.cz.webmaster.controller.support;

import com.cz.webmaster.entity.SmsUser;
import org.apache.shiro.SecurityUtils;

public final class OperatorContextUtils {

    private OperatorContextUtils() {
    }

    public static Long currentOperatorId() {
        Object principal = SecurityUtils.getSubject().getPrincipal();
        if (!(principal instanceof SmsUser)) {
            return null;
        }
        SmsUser user = (SmsUser) principal;
        return user.getId() == null ? null : user.getId().longValue();
    }
}
