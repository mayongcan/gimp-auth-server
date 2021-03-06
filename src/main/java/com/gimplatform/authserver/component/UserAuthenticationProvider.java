package com.gimplatform.authserver.component;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import com.gimplatform.authserver.service.AuthUserService;
import com.gimplatform.core.entity.UserInfo;
import com.gimplatform.core.entity.UserLogon;
import com.gimplatform.core.repository.UserLogonRepository;
import com.gimplatform.core.service.RoleInfoService;
import com.gimplatform.core.service.UserInfoService;
import com.gimplatform.core.utils.DateUtils;
import com.gimplatform.core.utils.StringUtils;

@Component
public class UserAuthenticationProvider implements AuthenticationProvider {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final int MAX_FAILE_COUNT = 5;

    @Autowired
    private AuthUserService authUserService;

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private UserLogonRepository userLogonRepository;

    @Autowired
    private RoleInfoService roleInfoService;

    @SuppressWarnings("unchecked")
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        boolean widthoutPassword = false;       //是否免密登录（短信验证通过后可登录）
        String openId = "";
        if(authentication.getDetails() != null) {
            Map<String, Object> detailMap = (Map<String, Object>) authentication.getDetails();
            widthoutPassword = MapUtils.getBooleanValue(detailMap, "widthoutPassword", false);
            openId = MapUtils.getString(detailMap, "openId", "");
        }
        if(StringUtils.isBlank(openId)) return loginNormal(authentication, widthoutPassword);
        else return loginByOpenId(authentication, openId);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
    
    /**
     * 普通的登录
     * @param authentication
     * @param widthoutPassword  是否免密登录
     * @return
     */
    private Authentication loginNormal(Authentication authentication, boolean widthoutPassword) {
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
        if (userLogon == null || userLogon.getUserId() == null) {
            // 判断记录是否存在，如果不存在，则创建登录信息表
            userLogon = userInfoService.addUserLogon(userInfo, null, null);
        }
        if (DateUtils.isBetweenTwoDate(userLogon.getLockBeginDate(), userLogon.getLockEndDate(), new Date())) {
            //容错，判断登录次数是否为5次，如果不是，则更新为5次
            if(userLogon.getFaileCount() < MAX_FAILE_COUNT) {
                userLogon.setFaileCount(MAX_FAILE_COUNT + 1);
                userLogonRepository.save(userLogon);
            }
            throw new UsernameNotFoundException("当前账号已被锁定，请联系管理员！");
        }

        //判断是否免密登录，如果是免密登录，则不需要判断密码，直接将数据库里面的密码赋值到变量中
        if(widthoutPassword) {
            password = userInfo.getPassword();
        }else {// 加密过程在这里体现
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
        }

        // 验证密码错误次数
        Collection<? extends GrantedAuthority> authorities = loginUser.getAuthorities();
        return new UsernamePasswordAuthenticationToken(loginUser, password, authorities);
    }
    
    /**
     * 使用openId登录
     * @param authentication
     * @param openId
     * @return
     */
    private Authentication loginByOpenId(Authentication authentication, String openId) {
        // 获取用户信息
        UserDetails loginUser = loadUserByUsername(openId);
        // 验证密码错误次数
        Collection<? extends GrantedAuthority> authorities = loginUser.getAuthorities();
        return new UsernamePasswordAuthenticationToken(loginUser, loginUser.getPassword(), authorities);
    }

    /**
     * 加载用户信息
     * @param openId
     * @return
     * @throws UsernameNotFoundException
     */
    private UserDetails loadUserByUsername(String openId) throws UsernameNotFoundException {
        // 获取用户信息
        UserInfo userInfo = userInfoService.findByOpenId(openId);
        if (userInfo == null) {
            logger.error("登录的openId：" + openId + "不存在！");
            throw new UsernameNotFoundException("第三方登录标识不存在！");
        }
        Collection<SimpleGrantedAuthority> collection = new HashSet<SimpleGrantedAuthority>();

        List<String> userRoles = roleInfoService.getRolesNameByUser(userInfo);
        for (int i = 0; i < userRoles.size(); i++) {
            collection.add(new SimpleGrantedAuthority(userRoles.get(i)));
            logger.info("授权用户[" + userInfo.getUserCode() + "]角色：" + userRoles.get(i));
        }
        return new org.springframework.security.core.userdetails.User(userInfo.getUserCode(), userInfo.getPassword(), collection);
    }

}
