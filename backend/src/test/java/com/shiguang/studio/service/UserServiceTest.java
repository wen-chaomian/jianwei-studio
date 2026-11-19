package com.shiguang.studio.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shiguang.studio.dto.*;
import com.shiguang.studio.entity.SysUser;
import com.shiguang.studio.exception.BusinessException;
import com.shiguang.studio.mapper.UserMapper;
import com.shiguang.studio.security.AccountStateCache;
import com.shiguang.studio.service.impl.UserServiceImpl;
import com.shiguang.studio.vo.UserVO;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserServiceTest {
  UserMapper mapper;
  UserService service;
  BCryptPasswordEncoder encoder;
  AccountStateCache accounts;

  @BeforeEach
  void setup() {
    mapper = mock(UserMapper.class);
    accounts = mock(AccountStateCache.class);
    encoder = new BCryptPasswordEncoder(4);
    service =
        new UserServiceImpl(
            mapper, encoder, accounts, mock(com.shiguang.studio.cache.SyncVersions.class));
  }

  @Test
  void mismatchedPasswordDoesNotWrite() {
    assertThrows(
        BusinessException.class,
        () ->
            service.register(
                new RegisterRequest("13800000000", "测试客户", "password123", "different")));
    verifyNoInteractions(mapper);
  }

  @Test
  void disabledAccountCannotLogin() {
    SysUser u = new SysUser();
    u.setPasswordHash(encoder.encode("password123"));
    u.setEnabled(false);
    when(mapper.lockByPhone(any())).thenReturn(u);
    assertThrows(
        BusinessException.class,
        () -> service.login(new LoginRequest("13800000000", "password123")));
  }

  @Test
  void loginCarriesTheSameVersionAsTheLockedCredential() {
    SysUser u = new SysUser();
    u.setId(4L);
    u.setPhone("13800000000");
    u.setPasswordHash(encoder.encode("password123"));
    u.setEnabled(true);
    u.setAuthVersion(7);
    when(mapper.lockByPhone(u.getPhone())).thenReturn(u);
    when(mapper.roles(4L)).thenReturn(List.of("CUSTOMER"));
    var account = service.login(new LoginRequest(u.getPhone(), "password123"));
    assertEquals(7, account.version());
    assertEquals(4L, account.user().id());
    verifyNoInteractions(accounts);
  }

  @Test
  void oversizedUtf8LoginIsRejectedBeforeBcrypt() {
    assertThrows(
        BusinessException.class,
        () -> service.login(new LoginRequest("13800000000", "密".repeat(25))));
    verifyNoInteractions(mapper);
  }

  @Test
  void customerRegistrationNeverGrantsEmployeeRole() {
    SysUser stored = new SysUser();
    stored.setId(4L);
    stored.setPhone("13800000000");
    stored.setDisplayName("测试客户");
    stored.setEnabled(true);
    doAnswer(
            inv -> {
              SysUser u = inv.getArgument(0);
              assertNotEquals("password123", u.getPasswordHash());
              assertTrue(encoder.matches("password123", u.getPasswordHash()));
              u.setId(4L);
              return 1;
            })
        .when(mapper)
        .insert(any(SysUser.class));
    when(accounts.get(4L))
        .thenReturn(
            new AccountStateCache.State(
                true,
                0,
                new UserVO(4L, stored.getPhone(), stored.getDisplayName(), List.of("CUSTOMER"))));
    assertEquals(
        List.of("CUSTOMER"),
        service
            .register(new RegisterRequest("13800000000", "测试客户", "password123", "password123"))
            .roles());
    verify(mapper).addRole(4L, "CUSTOMER");
    verify(mapper).addCustomer(any());
    verify(mapper, never()).addEmployee(any(), any(), any(), anyBoolean());
  }
}
