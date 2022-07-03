package com.nowcoder.seckill.configuration;

import com.nowcoder.seckill.controller.Interceptor.LoginCheckInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private LoginCheckInterceptor loginCheckInterceptor;

    @Override
    //拦截器拦截/order/captcha,/order/token, /order/create方法，执行这些方法前都要进行拦截，判断用户是否登陆
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginCheckInterceptor).addPathPatterns("/order/captcha","/order/token", "/order/create");
    }

}
