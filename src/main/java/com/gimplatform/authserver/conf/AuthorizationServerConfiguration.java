package com.gimplatform.authserver.conf;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.approval.UserApprovalHandler;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;

import com.gimplatform.authserver.service.AuthUserService;

/**
 * 认证服务器
 * @author zzd
 *
 */
@Configuration
@EnableAuthorizationServer
class AuthorizationServerConfiguration extends AuthorizationServerConfigurerAdapter {
	
	protected final Logger logger =  LoggerFactory.getLogger(this.getClass());

	@Autowired
	private UserApprovalHandler userApprovalHandler;

	@Autowired
	private TokenStore tokenStore;

    @Autowired
    private DataSource dataSource;

//	@Autowired
//	private JwtAccessTokenConverter accessTokenConverter;

	@Autowired
	@Qualifier("authenticationManagerBean")
    private AuthenticationManager authenticationManager;
	
    @Autowired
    private AuthUserService authUserService;


	@Override
	public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
		endpoints.tokenStore(tokenStore)
				.userApprovalHandler(userApprovalHandler)
				.authenticationManager(authenticationManager)
				.userDetailsService(authUserService);
//				.accessTokenConverter(accessTokenConverter);
	}
    
    @Override
    public void configure(AuthorizationServerSecurityConfigurer oauthServer) throws Exception {
        oauthServer
                .tokenKeyAccess("permitAll()");//公开/oauth/token的接口
    }

	@Override
	public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
		logger.info("初始化认证服务客户端...");
		//设置从数据库中读取clientDetails信息
		clients.jdbc(dataSource);
//		clients.inMemory()
//	        .withClient("gimp_web")			//客户端ID
//            .authorizedGrantTypes("password", "authorization_code", "refresh_token", "implicit")
//            .authorities("ROLE_CLIENT", "ROLE_TRUSTED_CLIENT")
//            .scopes("read", "write", "trust")			//授权用户的操作权限
//            .secret("gimp_web")							//密码
//            .accessTokenValiditySeconds(6000)			//token有效期为120秒
//            .refreshTokenValiditySeconds(7200);			//刷新token有效期为600秒
	}
	
	@Bean
    @Primary
    public DefaultTokenServices tokenServices() {
        DefaultTokenServices defaultTokenServices = new DefaultTokenServices();
        defaultTokenServices.setTokenStore(tokenStore);
        defaultTokenServices.setSupportRefreshToken(true);
        return defaultTokenServices;
    }
}
