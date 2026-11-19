package com.shiguang.studio.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiguang.studio.entity.SysUser;
import com.shiguang.studio.mapper.UserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.core.annotation.Order(0)
@Component
public class LocalAccountInitializer implements ApplicationRunner {
  private final UserMapper mapper;
  private final PasswordEncoder encoder;
  private final Environment env;

  public LocalAccountInitializer(UserMapper mapper, PasswordEncoder encoder, Environment env) {
    this.mapper = mapper;
    this.encoder = encoder;
    this.env = env;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    for (String role : new String[] {"ADMIN", "RECEPTIONIST", "PHOTOGRAPHER"}) {
      String prefix = "studio.bootstrap." + role.toLowerCase() + ".";
      String phone = env.getProperty(prefix + "phone"),
          password = env.getProperty(prefix + "password");
      if (phone == null || password == null) continue;
      if (mapper.selectCount(new QueryWrapper<SysUser>().eq("phone", phone)) > 0) continue;
      SysUser u = new SysUser();
      u.setPhone(phone);
      u.setDisplayName(
          switch (role) {
            case "ADMIN" -> "工作室管理员";
            case "RECEPTIONIST" -> "见微接待";
            default -> "见微摄影师";
          });
      u.setPasswordHash(encoder.encode(password));
      u.setEnabled(true);
      mapper.insert(u);
      mapper.addRole(u.getId(), role);
      mapper.addEmployee(
          u.getId(), "SG-" + u.getId(), u.getDisplayName(), role.equals("PHOTOGRAPHER"));
    }
  }
}
