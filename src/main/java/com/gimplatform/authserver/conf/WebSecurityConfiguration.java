package com.gimplatform.authserver.conf;

import javax.servlet.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.approval.ApprovalStore;
import org.springframework.security.oauth2.provider.approval.TokenApprovalStore;
import org.springframework.security.oauth2.provider.approval.TokenStoreUserApprovalHandler;
import org.springframework.security.oauth2.provider.request.DefaultOAuth2RequestFactory;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import com.gimplatform.authserver.component.UserAuthenticationProvider;

/**
 * 配置于web的security
 * @author zzd
 *
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {
	
	protected final Logger logger =  LoggerFactory.getLogger(this.getClass());

	@Autowired
	private RedisConnectionFactory redisConnection;
	
	@Autowired
	private ClientDetailsService clientDetailsService;

	@Autowired
	private UserAuthenticationProvider userAuthenticationProvider;
	
//	@Bean
//	public AuthUserService authUserService(){
//		return new AuthUserService();
//	}
	
	@Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth
        	.authenticationProvider(userAuthenticationProvider);	//自定义验证
//        	.userDetailsService(authUserService())				//自定义用户服务
//			.passwordEncoder(new Md5PasswordEncoder());			//使用MD5进行密码加密
    }
	
	/**
	 * 设置不拦截规则
	 */
	@Override  
    public void configure(WebSecurity web) throws Exception {  
        // 设置不拦截规则  
        web.ignoring().antMatchers("/static/**", "/kaptcha/**", "/user/**",  "/client/**", "/info/**", "/druidMonitor/**", "/**/*.html");
        //这里需要设置不拦截获取token的地址，否则跨域请求会产生401
        web.ignoring().antMatchers(HttpMethod.OPTIONS, "/oauth/token");
  
    }  
	
	/**
	 * 添加跨域过滤器，需要在spring security验证前添加到过滤器，否则无法正常获取登录token
	 * 注意：在这里添加后，就不需要在子服务里面在添加，否则会出现重复响应头的问题
	 * @return
	 */
	@Bean
    public Filter corsFilter() {
		logger.info("初始化跨域配置...");
		final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        final CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.addAllowedOrigin("*");
        corsConfiguration.addAllowedMethod("*");
        corsConfiguration.addAllowedHeader("*");
        source.registerCorsConfiguration("/**", corsConfiguration);
        return new CorsFilter(source);
    }

	/**
	 * 用来控制权限，角色，url等
	 */
    @Override
    protected void configure(HttpSecurity httpSecurity) throws Exception {
    	//添加跨域过滤器
    	httpSecurity.addFilterBefore(corsFilter(), ChannelProcessingFilter.class);
    	httpSecurity
    				.authorizeRequests()											//配置安全策略  
		        		.antMatchers(HttpMethod.OPTIONS, "/oauth/token")
		        		.permitAll()												//定义 请求不需要验证  
		        		.anyRequest()
		        		.authenticated();											//其余的所有请求都需要验证 
    }
    
    /**
     * 将token写入redis
     * @return
     */
	@Bean
	public TokenStore tokenStore() {
		return new RedisTokenStore(redisConnection);
	}

    @Override
    @Bean
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

	@Bean
	@Autowired
	public TokenStoreUserApprovalHandler userApprovalHandler(TokenStore tokenStore){
		TokenStoreUserApprovalHandler handler = new TokenStoreUserApprovalHandler();
		handler.setTokenStore(tokenStore);
		handler.setRequestFactory(new DefaultOAuth2RequestFactory(clientDetailsService));
		handler.setClientDetailsService(clientDetailsService);
		return handler;
	}
	
	@Bean
	@Autowired
	public ApprovalStore approvalStore(TokenStore tokenStore) throws Exception {
		TokenApprovalStore store = new TokenApprovalStore();
		store.setTokenStore(tokenStore);
		return store;
	}

//	/**
//	 * 转换Jwt格式token
//	 * @return
//	 */
//	@Bean
//    public JwtAccessTokenConverter accessTokenConverter() {
//        JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
//        converter.setSigningKey("gimplatform");
//        return converter;
//    }

//  /**
//   * 生成JWT格式token
//   * @return
//   */
//  @Bean
//  public TokenStore tokenStore() {
//      return new JwtTokenStore(accessTokenConverter());
//  }
	
}