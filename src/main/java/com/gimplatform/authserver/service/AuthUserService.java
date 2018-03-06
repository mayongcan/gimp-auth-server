package com.gimplatform.authserver.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.gimplatform.core.entity.UserInfo;
import com.gimplatform.core.service.RoleInfoService;
import com.gimplatform.core.service.UserInfoService;

/**
 * 验证用户信息
 * @author zzd
 */
@Component
public class AuthUserService implements UserDetailsService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private RoleInfoService roleInfoService;

    /**
     * 根据用户名获取登录用户信息
     * @param username
     * @return
     * @throws UsernameNotFoundException
     */
    @Override
    public UserDetails loadUserByUsername(String userCode) throws UsernameNotFoundException {
        // 获取用户信息
        UserInfo userInfo = userInfoService.getByUserCode(userCode);
        if (userInfo == null) {
            logger.error("登录用户：" + userCode + "不存在！");
            throw new UsernameNotFoundException("登录用户不存在！");
        }
        Collection<SimpleGrantedAuthority> collection = new HashSet<SimpleGrantedAuthority>();

        List<String> userRoles = roleInfoService.getRolesNameByUser(userInfo);
        for (int i = 0; i < userRoles.size(); i++) {
            collection.add(new SimpleGrantedAuthority(userRoles.get(i)));
            logger.info("授权用户[" + userCode + "]角色：" + userRoles.get(i));
        }
        return new org.springframework.security.core.userdetails.User(userCode, userInfo.getPassword(), collection);
    }

}