package com.gimplatform.authserver.component;

import java.util.Collection;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import com.gimplatform.authserver.service.AuthUserService;
import com.gimplatform.core.entity.UserInfo;
import com.gimplatform.core.entity.UserLogon;
import com.gimplatform.core.repository.UserLogonRepository;
import com.gimplatform.core.service.UserInfoService;
import com.gimplatform.core.utils.DateUtils;

@Component
public class UserAuthenticationProvider implements AuthenticationProvider {

    private final int MAX_FAILE_COUNT = 5;

    @Autowired
    private AuthUserService authUserService;

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private UserLogonRepository userLogonRepository;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // 获取登录用户名
        String userCode = authentication.getName();
        UserDetails loginUser = authUserService.loadUserByUsername(userCode);
        if (loginUser == null) {
            throw new UsernameNotFoundException("登录用户不存在！");
        }
        // 获取登录密码
        String password = (String) authentication.getCredentials();

        UserInfo userInfo = userInfoService.getByUserCode(userCode);
        // 判断当前用户是否被锁
        UserLogon userLogon = userLogonRepository.findByUserId(userInfo.getUserId());
        if (userLogon == null) {
            // 创建登录信息表
            userLogon = userInfoService.addUserLogon(userInfo, null, null);
        }
        if (DateUtils.isBetweenTwoDate(userLogon.getLockBeginDate(), userLogon.getLockEndDate(), new Date())) {
            throw new UsernameNotFoundException("当前账号已被锁定，请联系管理员！");
        }

        // 加密过程在这里体现
        String md5Password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!md5Password.equals(userInfo.getPassword())) {
            // 更新密码错误次数,如果次数超过5次，则锁定账号，锁定一天
            int faileCount = userLogon.getFaileCount() + 1;
            if (faileCount >= MAX_FAILE_COUNT) {
                userLogon.setFaileCount(userLogon.getFaileCount() + 1);
                userLogon.setLockBeginDate(new Date());
                userLogon.setLockEndDate(DateUtils.parseDate(DateUtils.getDate("yyyy-MM-dd") + " 23:59:59"));
                userLogon.setLockReason("登录密码错误超过限制次数");
            } else {
                userLogon.setFaileCount(userLogon.getFaileCount() + 1);
            }
            userLogonRepository.save(userLogon);
            int time = (MAX_FAILE_COUNT - faileCount);
            if (time <= 0) {
                throw new BadCredentialsException("登录失败次数过多，当前账号已被锁定，请联系管理员！");
            } else
                throw new BadCredentialsException("登录密码错误，你还有" + time + "次尝试机会！");
        }

        // 验证密码错误次数
        Collection<? extends GrantedAuthority> authorities = loginUser.getAuthorities();
        return new UsernamePasswordAuthenticationToken(loginUser, password, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

}
