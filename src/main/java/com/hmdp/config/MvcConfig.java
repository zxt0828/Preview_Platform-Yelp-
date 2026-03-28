package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshInterceptor;
import javax.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class MvcConfig implements WebMvcConfigurer {
  @Resource
  private StringRedisTemplate stringRedisTemple;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    //first
    registry.addInterceptor(new RefreshInterceptor(stringRedisTemple))
        .addPathPatterns("/**")
        .order(0);
    //second
    registry.addInterceptor(new LoginInterceptor())
        .excludePathPatterns(
            "/shop/**",
            "/voucher/**",
            "/shop-type/**",
            "/upload/**",
            "/blog/hot",
            "/user/code",
            "/user/login"
        )
        .order(1);
  }
}
