package com.gimplatform.authserver.restful;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.google.code.kaptcha.Constants;
import com.google.code.kaptcha.Producer;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;

/**
 * 验证码restful类
 * @author zzd
 *
 */
@Controller
@RequestMapping("kaptcha")
public class KaptchaRestful {
	
	protected final Logger logger =  LoggerFactory.getLogger(this.getClass());
    
	@Resource
	private Producer kaptchaProducer;
	
	/**
	 * 配置参数
	 * @return
	 */
	@Bean(name="kaptchaProducer")
    public DefaultKaptcha getKaptchaBean(){
        DefaultKaptcha defaultKaptcha = new DefaultKaptcha();
        Properties properties = new Properties();
//        properties.setProperty("kaptcha.border", "yes");
//        properties.setProperty("kaptcha.border.color", "232,232,232");
//        properties.setProperty("kaptcha.border.thickness", "5");
//        properties.setProperty("kaptcha.background.clear.from", "white");
//        properties.setProperty("kaptcha.background.clear.to", "white");
//        properties.setProperty("kaptcha.textproducer.font.color", "50,50,50");
//        properties.setProperty("kaptcha.noise.impl", "com.google.code.kaptcha.impl.NoNoise");
//        properties.setProperty("kaptcha.image.width", "125");
//        properties.setProperty("kaptcha.image.height", "35");
//        properties.setProperty("kaptcha.session.key", "code");
//        properties.setProperty("kaptcha.textproducer.char.length", "5");
//        properties.setProperty("kaptcha.textproducer.font.names", "Arial");
//        properties.setProperty("kaptcha.textproducer.font.size", "22");     
//        properties.setProperty("kaptcha.textproducer.char.space", "8");    
//        properties.setProperty("kaptcha.obscurificator.impl", "com.google.code.kaptcha.impl.ShadowGimpy"); 
        properties.setProperty("kaptcha.border", "yes");
        properties.setProperty("kaptcha.border.color", "248,249,251");
        properties.setProperty("kaptcha.border.thickness", "1");
        properties.setProperty("kaptcha.background.clear.from","248,249,251");
        properties.setProperty("kaptcha.background.clear.to", "248,249,251");
        properties.setProperty("kaptcha.textproducer.font.color", "88,88,88");
        properties.setProperty("kaptcha.noise.impl", "com.google.code.kaptcha.impl.NoNoise");
        properties.setProperty("kaptcha.image.width", "125");
        properties.setProperty("kaptcha.image.height", "35");
        properties.setProperty("kaptcha.session.key", "code");
        properties.setProperty("kaptcha.textproducer.char.length", "5");
        properties.setProperty("kaptcha.textproducer.font.names", "Arial");
        properties.setProperty("kaptcha.textproducer.font.size", "22");     
        properties.setProperty("kaptcha.textproducer.char.space", "8");    
        properties.setProperty("kaptcha.obscurificator.impl", "com.google.code.kaptcha.impl.ShadowGimpy");
        Config config = new Config(properties);
        defaultKaptcha.setConfig(config);
        return defaultKaptcha;
    }
    
    /**
     * 获取验证码图片
     * @param request
     * @param response
     * @return
     * @throws IOException
     */
    @RequestMapping("getKaptchaCode")
    public ModelAndView getKaptchaCode(HttpServletRequest request, HttpServletResponse response) throws IOException{
        HttpSession session = request.getSession();
        response.setDateHeader("Expires", 0);  
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");  
        response.addHeader("Cache-Control", "post-check=0, pre-check=0");  
        response.setHeader("Pragma", "no-cache");  
        response.setContentType("image/jpeg"); 
        
        //生成验证码文本
        String capText = kaptchaProducer.createText();  
        session.setAttribute(Constants.KAPTCHA_SESSION_KEY, capText);
        logger.info("生成验证码内容:" + capText);
        //利用生成的字符串构建图片
        BufferedImage bi = kaptchaProducer.createImage(capText);
        ServletOutputStream out = response.getOutputStream();  
        ImageIO.write(bi, "jpg", out);  
        
        try {  
            out.flush();  
        } finally {  
            out.close();  
        }
        return null;
    }
    
    /**
     * 前端输入的验证码与生成的对比
     * @param request
     * @param response
     * @param kaptchaCode
     */
    @RequestMapping("checkKaptchaCode")
    public void checkKaptchaCode(HttpServletRequest request, HttpServletResponse response,@RequestParam("kaptchaCode") String kaptchaCode){
    	logger.info("页面输入验证码:" + kaptchaCode);
        
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Pragma", "No-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
        
        String generateCode = (String) request.getSession().getAttribute(Constants.KAPTCHA_SESSION_KEY);
        String result = "";
        if(generateCode != null && generateCode.equals(kaptchaCode.toLowerCase())){
            result = "{\"code\":\"success\"}";
        }else{
            result = "{\"code\":\"failure\"}";
        }
        PrintWriter out = null;
        try {
            out = response.getWriter();
        } catch (IOException e) {
        	logger.error("检查验证码失败", e);
        }
        out.print(result);
        out.flush();
    }

}
