package com.cz.webmaster.relam;

import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.SmsUserService;
import com.cz.webmaster.shiro.JwtToken;
import com.cz.webmaster.util.JwtUtil;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 自定义Realm (JWT版)
 * @author cz
 */
@Component
public class ShiroRealm extends AuthorizingRealm {

    @Autowired
    private SmsUserService userService;

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }

    /**
     * 认证
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        String jwt = (String) token.getPrincipal();
        String username = JwtUtil.getUsername(jwt);
        
        if (username == null) {
            throw new AuthenticationException("Token invalid");
        }

        SmsUser smsUser = userService.findByUsername(username);
        if (smsUser == null) {
            throw new AuthenticationException("User doesn't exist");
        }

        if (!JwtUtil.verify(jwt, username)) {
            throw new AuthenticationException("Token verify failed");
        }

        return new SimpleAuthenticationInfo(smsUser, jwt, "shiroRealm");
    }

    /**
     * 授权
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        return null;
    }
}