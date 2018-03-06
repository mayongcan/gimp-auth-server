package com.gimplatform.authserver;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;

/**
 * 认证授权服务器启动类
 * @author zzd
 */
@SpringBootApplication
@EnableEurekaClient
@EnableResourceServer
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AuthServerApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }
}
