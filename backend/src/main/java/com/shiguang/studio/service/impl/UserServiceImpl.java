package com.shiguang.studio.service.impl;

import com.shiguang.studio.cache.SyncVersions;
import com.shiguang.studio.dto.*;
import com.shiguang.studio.entity.SysUser;
import com.shiguang.studio.exception.BusinessException;
import com.shiguang.studio.mapper.UserMapper;
import com.shiguang.studio.security.AccountStateCache;
import com.shiguang.studio.service.UserService;
import com.shiguang.studio.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {
  private final UserMapper mapper;
  private final PasswordEncoder encoder;
  private final AccountStateCache accounts;
  private final SyncVersions sync;

  public UserServiceImpl(
      UserMapper mapper, PasswordEncoder encoder, AccountStateCache accounts, SyncVersions sync) {
    this.sync = sync;
    this.mapper = mapper;
    this.encoder = encoder;
    this.accounts = accounts;
  }

  @Transactional
  public UserVO register(RegisterRequest r) {
    if (r.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new BusinessException("密码UTF-8长度不能超过72字节");
    if (!r.password().equals(r.confirmPassword())) throw new BusinessException("两次密码不一致");
    if (r.displayName().isBlank()) throw new BusinessException("请输入姓名");
    SysUser u = new SysUser();
    u.setPhone(r.phone());
    u.setDisplayName(r.displayName().strip());
    u.setPasswordHash(encoder.encode(r.password()));
    u.setEnabled(true);
    mapper.insert(u);
    mapper.addRole(u.getId(), "CUSTOMER");
    mapper.addCustomer(u);
    accounts.evictAfterCommit(u.getId());
    sync.changed("desk");
    return get(u.getId());
  }

  @Transactional
  public AuthenticatedAccount login(LoginRequest r) {
    if (r.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new BusinessException("手机号或密码错误，或账号已停用");
    SysUser u = mapper.lockByPhone(r.phone());
    if (u == null
        || !Boolean.TRUE.equals(u.getEnabled())
        || !encoder.matches(r.password(), u.getPasswordHash()))
      throw new BusinessException("手机号或密码错误，或账号已停用");
    return new AuthenticatedAccount(
        new UserVO(u.getId(), u.getPhone(), u.getDisplayName(), mapper.roles(u.getId())),
        u.getAuthVersion());
  }

  public UserVO get(Long id) {
    var state = accounts.get(id);
    if (!state.enabled() || state.user() == null) throw new BusinessException("账号不可用");
    return state.user();
  }
}
