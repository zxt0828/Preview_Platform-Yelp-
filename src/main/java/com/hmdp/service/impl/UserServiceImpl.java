package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

  @Override
  public Result sendCode(String phone, HttpSession session) {
    //check valid phone number
    if(RegexUtils.isPhoneInvalid(phone)) {
      return Result.fail("wrong phone number");
    }

    //produce a verification code
    String code = RandomUtil.randomNumbers(6);

    //store the code to session
    session.setAttribute("code", code);

    //send the code
    log.debug("发送短信验证码成功，验证码：{}", code);
    return Result.ok();
  }

  @Override
  public Result login(LoginFormDTO loginForm, HttpSession session) {
    //verify phone number
    String phone = loginForm.getPhone();
    if(RegexUtils.isPhoneInvalid(phone)) {
      return Result.fail("wrong phone number");
    }

    //verify the verification code
    String code = loginForm.getCode();
    if(RegexUtils.isCodeInvalid(code)) {
      return Result.fail("wrong code");
    }

    //find the user from database
    User user = query().eq("phone", phone).one();

    //if user do not exist
    if(user == null){
      user = createUserWithPhone(phone);
    }

    //store session
    session.setAttribute("user", user);
    return Result.ok();

  }
  // a method : if user not exist, create a new user with phone number
  private User createUserWithPhone(String phone){
    User user = new User();
    user.setPhone(phone);
    user.setNickName("user" + RandomUtil.randomNumbers(6));
    save(user);
    return user;
  }


}
