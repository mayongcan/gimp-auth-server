package com.gimplatform.authserver.restful;

import java.security.Principal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson.JSONObject;
import com.gimplatform.core.entity.LogInfo;
import com.gimplatform.core.entity.UserInfo;
import com.gimplatform.core.entity.UserLogon;
import com.gimplatform.core.repository.LogInfoRepository;
import com.gimplatform.core.repository.UserLogonRepository;
import com.gimplatform.core.service.SmsinfoService;
import com.gimplatform.core.service.UserInfoService;
import com.gimplatform.core.utils.BeanUtils;
import com.gimplatform.core.utils.DateUtils;
import com.gimplatform.core.utils.RedisUtils;
import com.gimplatform.core.utils.RestfulRetUtils;
import com.gimplatform.core.utils.StringUtils;
import com.gimplatform.core.utils.UserAgentUtils;

/**
 * 用户相关的Restful接口
 * @author zzd
 */
@RestController
@RequestMapping("user")
public class UsersRestful {

    protected static final Logger logger = LogManager.getLogger(UsersRestful.class);

    @Autowired
    private SmsinfoService smsinfoService;

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private UserLogonRepository userLogonRepository;

    @Autowired
    private LogInfoRepository logInfoRepository;

    @RequestMapping("/user")
    public Principal user(Principal user) {
        return user;
    }

    /**
     * 验证登录IP
     * @param request
     * @return
     */
    @RequestMapping(value = "/checkLoginIp", method = RequestMethod.POST)
    public JSONObject checkLoginIp(HttpServletRequest request, @RequestBody Map<String, Object> params) {
        JSONObject json = new JSONObject();
        try {
            UserInfo userInfo = userInfoService.getByUserCode(MapUtils.getString(params, "userCode"));
            if (userInfo == null)
                json = RestfulRetUtils.getErrorNoUser();
            else {
                UserLogon userLogon = userLogonRepository.findByUserId(userInfo.getUserId());
                String ipAddr = UserAgentUtils.getIpAddress(request);
                boolean isInList = false;
                // 校验IP白名单
                if (!StringUtils.isBlank(userLogon.getAccessIpaddress())) {
                    String[] arrayList = userLogon.getAccessIpaddress().split(",");
                    for (String addr : arrayList) {
                        if (StringUtils.isNotBlank(addr) && addr.equals(ipAddr)) {
                            isInList = true;
                            break;
                        }
                    }
                } else {
                    isInList = true;
                }
                // 是否在IP白名单
                if (isInList) {
                    // 判断用户是否使用了不同的IP登录
                    if (StringUtils.isNotBlank(userLogon.getLastLogonIp()) && !userLogon.getLastLogonIp().equals(ipAddr)) {
                        logger.warn("用户:" + userInfo.getUserCode() + "上次登录IP:" + userLogon.getLastLogonIp() + ",当前登录IP:" + ipAddr);
                    }
                    //获取前端的参数
                    UserLogon tmpUserLogon = (UserLogon) BeanUtils.mapToBean(params, UserLogon.class);
                    BeanUtils.mergeBean(tmpUserLogon, userLogon);
                    // 写入登录信息
                    userLogon.setFaileCount(0);
                    userLogon.setLockBeginDate(null);
                    userLogon.setLockEndDate(null);
                    userLogon.setLockReason("");
                    userLogon.setLastLogonDate(new Date());
                    userLogon.setLastLogonIp(ipAddr);
                    userLogonRepository.save(userLogon);
                    // 写入登录日志
                    LogInfo logInfo = new LogInfo();
                    logInfo.setLogTitle("用户登录");
                    logInfo.setCreateBy(userInfo.getUserId());
                    logInfo.setCreateDate(DateUtils.dateFormat(new Date()));
                    logInfo.setLogType(LogInfo.TYPE_ACCESS);
                    logInfo.setRemoteAddr(ipAddr);
                    logInfo.setUserAgent(request.getHeader("user-agent"));
                    // logInfo.setReqeustUri(request.getRequestURI());
                    logInfo.setReqeustUri("/authServer/oauth/token");
                    logInfo.setParams(StringUtils.mapToString(request.getParameterMap()));
                    logInfo.setMethod(request.getMethod());
                    logInfoRepository.save(logInfo);
                    json = RestfulRetUtils.getRetSuccess();
                } else {
                    json = RestfulRetUtils.getErrorMsg("51001", "当前用户登录的IP不在白名单内，禁止登录！");
                }
            }
        } catch (Exception e) {
            json = RestfulRetUtils.getErrorMsg("51001", "验证登录IP失败");
            logger.error(e.getMessage(), e);
        }
        return json;
    }

    /**
     * 获取短信验证码
     * @param request
     * @return
     */
    @RequestMapping(value = "/getSmsCode", method = RequestMethod.POST)
    public JSONObject getSmsCode(HttpServletRequest request, @RequestBody Map<String, Object> params) {
        JSONObject json = new JSONObject();
        try {
            String userCode = MapUtils.getString(params, "userCode");
            String phone = MapUtils.getString(params, "phone");
            if (StringUtils.isBlank(userCode) || StringUtils.isBlank(phone)) {
                json = RestfulRetUtils.getErrorParams();
            } else {
                UserInfo userInfo = userInfoService.getByUserCode(userCode);
                if (userInfo == null)
                    json = RestfulRetUtils.getErrorMsg("31001", "当前输入的账号不存在");
                else {
                    if (!phone.equals(userInfo.getMobile()))
                        json = RestfulRetUtils.getErrorMsg("31001", "当前账号绑定的手机号码有误，请重新输入！");
                    else {
                        smsinfoService.sendSms(phone);
                        json = RestfulRetUtils.getRetSuccess();
                    }
                }
            }
        } catch (Exception e) {
            json = RestfulRetUtils.getErrorMsg("31001", "获取短信验证码错误");
            logger.error(e.getMessage(), e);
        }
        return json;
    }

    /**
     * 不验证用户账号
     * @param request
     * @return
     */
    @RequestMapping(value = "/getSmsCodeByPhone", method = RequestMethod.POST)
    public JSONObject getSmsCodeByPhone(HttpServletRequest request, @RequestBody Map<String, Object> params) {
        JSONObject json = new JSONObject();
        try {
            String phone = MapUtils.getString(params, "phone");
            if (StringUtils.isBlank(phone)) {
                json = RestfulRetUtils.getErrorParams();
            } else {
                logger.info("获取验证码 手机号码：" + phone);
                smsinfoService.sendSms(phone);
                json = RestfulRetUtils.getRetSuccess();
            }
            logger.info("结束getSmsCodeByPhone");
        } catch (Exception e) {
            json = RestfulRetUtils.getErrorMsg("31001", "获取短信验证码错误");
            logger.error(e.getMessage(), e);
        }
        return json;
    }

    /**
     * 验证短信验证码
     * @param request
     * @return
     */
    @RequestMapping(value = "/checkSmsVerifyCode", method = RequestMethod.POST)
    public JSONObject checkSmsVerifyCode(HttpServletRequest request, @RequestBody Map<String, Object> params) {
        JSONObject json = new JSONObject();
        try {
            String phone = MapUtils.getString(params, "phone");
            String smsCode = MapUtils.getString(params, "smsCode");
            if (StringUtils.isBlank(smsCode) || StringUtils.isBlank(phone)) {
                json = RestfulRetUtils.getErrorParams();
            } else {
                logger.info("验证手机短信验证码：" + phone + " " + smsCode);
                int ret = smsinfoService.verifyCode(phone, smsCode);
                if (ret == 0) {
                    json = RestfulRetUtils.getErrorMsg("31001", "验证码错误");
                } else {
                    json = RestfulRetUtils.getRetSuccess();
                }
                logger.info("结束checkSmsVerifyCode");
            }
        } catch (Exception e) {
            json = RestfulRetUtils.getErrorMsg("31001", "验证码错误");
            logger.error(e.getMessage(), e);
        }
        return json;
    }

    /**
     * 用户密码修改接口
     * @param request
     * @return
     */
    @RequestMapping(value = "/resetPasswd", method = RequestMethod.POST)
    public JSONObject resetPasswd(HttpServletRequest request, @RequestBody Map<String, Object> params) {
        JSONObject json = new JSONObject();
        try {
            String userCode = MapUtils.getString(params, "userCode");
            String phone = MapUtils.getString(params, "phone");
            String password = MapUtils.getString(params, "password");
            json = userInfoService.updatePasswordByUserCodeAndMobile(userCode, phone, password);
        } catch (Exception e) {
            json = RestfulRetUtils.getErrorMsg("31001", "密码重置失败");
            logger.error(e.getMessage(), e);
        }
        return json;
    }

    /**
     * 检查token是否超时，如果超时就刷新token，同时获取新的token
     * @param request
     * @return
     */
    @RequestMapping(value = "/checkToken", method = RequestMethod.POST)
    public JSONObject checkToken(HttpServletRequest request, @RequestBody Map<String, Object> params) {
        JSONObject json = RestfulRetUtils.getRetSuccess("success");
        try {
            String userCode = MapUtils.getString(params, "userCode");
            String access_token = MapUtils.getString(params, "access_token");
            String refresh_token = MapUtils.getString(params, "refresh_token");
            Long expires_in = MapUtils.getLong(params, "expires_in", 0L);
            String prefix = "GIMP:TOKEN:" + userCode;
            // logger.info("expires=[" + expires_in + "]");
            // 获取在redis中的当前token
            String curToken = RedisUtils.hget(prefix, "CUR_TOKEN");
            Date accessTime = DateUtils.parseDate(RedisUtils.hget(prefix, "ACCESS_TIME"));
            // 如果当前token为null，则表示第一次请求，这是需要写入
            if (StringUtils.isBlank(curToken) || accessTime == null) {
                writeTokenInfo(prefix, access_token, access_token, refresh_token, expires_in);
            } else {
                // 判断当前的缓存数据是否已过时(访问时长超过5分钟，则超时),已过时则表示第一次请求
                if (DateUtils.getSecondsOfTwoDate(accessTime, new Date()) > 300) {
                    writeTokenInfo(prefix, access_token, access_token, refresh_token, expires_in);
                } else {
                    // 判断缓冲中的token是否和当前传送过来的token一致
                    if (StringUtils.isBlank(access_token) || access_token.equals(curToken)) {
                        // 如果一致，则需要判断当前的token是否差不多过时(token超时少于5分钟)
                        if (StringUtils.isBlank(access_token) || expires_in < 300L) {
                            // 通知前段刷新token
                            Map<String, Object> retMap = new HashMap<String, Object>();
                            retMap.put("refresh", "1");
                            json = RestfulRetUtils.getRetSuccess(retMap);
                        }
                    } else {
                        String oldToken = RedisUtils.hget(prefix, "OLD_TOKEN");
                        // 判断当前的token是否和oldToken一致
                        if (access_token.equals(oldToken)) {
                            logger.info("token被刷新，获取token缓存，user=[" + userCode + "] token=[" + access_token + "] curToken=[" + curToken + "] oldToken=[" + oldToken + "]");
                            String refToken = RedisUtils.hget(prefix, "REF_TOKEN");
                            String expiresIn = RedisUtils.hget(prefix, "EXPIRES_IN");
                            // 如果是，则返回相应内容给客户端
                            Map<String, Object> retMap = new HashMap<String, Object>();
                            retMap.put("access_token", curToken);
                            retMap.put("refresh_token", refToken);
                            retMap.put("expires_in", expiresIn);
                            json = RestfulRetUtils.getRetSuccess(retMap);
                        } else {
                            if (curToken.equals(oldToken)) {
                                logger.info("redis中的token一致，刷新缓存，user=[" + userCode + "] token=[" + access_token + "] curToken=[" + curToken + "] oldToken=[" + oldToken + "]");
                                writeTokenInfo(prefix, access_token, access_token, refresh_token, expires_in);
                            } else {
                                // 判断上一次是否relogin
                                String relogin = RedisUtils.hget(prefix, "RELOGIN");
                                if ("1".equals(relogin)) {
                                    logger.info("已重新登陆，刷新token缓存，user=[" + userCode + "] token=[" + access_token + "] curToken=[" + curToken + "] oldToken=[" + oldToken + "]");
                                    writeTokenInfo(prefix, access_token, access_token, refresh_token, expires_in);
                                } else {
                                    logger.info("token被刷新，缓存不一致，要求重新登录，user=[" + userCode + "] token=[" + access_token + "] curToken=[" + curToken + "] oldToken=[" + oldToken + "]");
                                    // 记录重登次数
                                    RedisUtils.hset(prefix, "RELOGIN", "1", 0);

                                    // 否则通知客户端重新登录
                                    Map<String, Object> retMap = new HashMap<String, Object>();
                                    retMap.put("relogin", "1");
                                    json = RestfulRetUtils.getRetSuccess(retMap);
                                }
                            }
                        }
                    }
                }
            }
            // 写入接口访问时间
            RedisUtils.hset(prefix, "ACCESS_TIME", DateUtils.getDate("yyyy-MM-dd HH:mm:ss"), 0);
        } catch (Exception e) {
            json = RestfulRetUtils.getErrorMsg("31001", "检查token失败");
            logger.error(e.getMessage(), e);
        }
        return json;
    }

    /**
     * 更新授权缓存
     * @param request
     * @return
     */
    @RequestMapping(value = "/uploadTokenCache", method = RequestMethod.POST)
    public JSONObject uploadTokenCache(HttpServletRequest request, @RequestBody Map<String, Object> params) {
        JSONObject json = RestfulRetUtils.getRetSuccess();
        try {
            String userCode = MapUtils.getString(params, "userCode");
            String oldToken = MapUtils.getString(params, "oldToken");
            String access_token = MapUtils.getString(params, "access_token");
            String refresh_token = MapUtils.getString(params, "refresh_token");
            Long expires_in = MapUtils.getLong(params, "expires_in", 0L);
            String prefix = "GIMP:TOKEN:" + userCode;
            logger.info("更新授权缓存，user=[" + userCode + "] oldToken=[" + oldToken + "] access_token=[" + access_token + "]");
            writeTokenInfo(prefix, access_token, oldToken, refresh_token, expires_in);
        } catch (Exception e) {
            json = RestfulRetUtils.getErrorMsg("31001", "更新授权缓存失败");
            logger.error(e.getMessage(), e);
        }
        return json;
    }
    
    /**
     * 根据openId查找用户
     * @param request
     * @return
     */
    @RequestMapping(value = "/findUserIdByOpenId", method = RequestMethod.GET)
    public JSONObject findUserIdByOpenId(HttpServletRequest request, @RequestParam Map<String, Object> params) {
        JSONObject json = new JSONObject();
        try {
            String openId = MapUtils.getString(params, "openId", "");
            if(StringUtils.isBlank(openId)) return RestfulRetUtils.getRetSuccess();
            else {
                UserInfo userInfo = userInfoService.findByOpenId(openId);
                if(userInfo == null) return RestfulRetUtils.getRetSuccess();
                else return RestfulRetUtils.getRetSuccess(userInfo);
            }
        } catch (Exception e) {
            json = RestfulRetUtils.getErrorMsg("51001", "获取列表失败");
            logger.error(e.getMessage(), e);
        }
        return json;
    }

    private void writeTokenInfo(String prefix, String access_token, String oldToken, String refresh_token, Long expires_in) {
        RedisUtils.hset(prefix, "CUR_TOKEN", access_token, 0);
        RedisUtils.hset(prefix, "REF_TOKEN", refresh_token, 0);
        RedisUtils.hset(prefix, "EXPIRES_IN", expires_in + "", 0);
        RedisUtils.hset(prefix, "OLD_TOKEN", oldToken, 0);
        RedisUtils.hset(prefix, "RELOGIN", "0", 0);
    }

}
