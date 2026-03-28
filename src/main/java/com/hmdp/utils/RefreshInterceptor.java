package com.hmdp.utils;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

public class RefreshInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshInterceptor(StringRedisTemplate stringRedisTemplate){
      this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
      //get token
      String token = request.getHeader("authorization");
      //no check token is empty, give it to another interceptor to do it
      if(StrUtil.isBlank(token)){
        return true;
      }
      //get redis key
      String key = LOGIN_USER_KEY + token;
      //hashmap
      Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
      if(userMap.isEmpty()){
        return true;
      }
      //change to DTO
      UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
      //save to threadLocal
      UserHolder.saveUser(userDTO);
      //set expire time
      stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
      // 7. 放行
      return true;
    }

  @Override
  public void afterCompletion(HttpServletRequest request,
      HttpServletResponse response,
      Object handler, Exception ex) {
    UserHolder.removeUser();
  }
}
