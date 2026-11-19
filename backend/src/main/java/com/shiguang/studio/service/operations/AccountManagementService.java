package com.shiguang.studio.service.operations;

import static com.shiguang.studio.service.support.CurrentActor.admin;
import static com.shiguang.studio.service.support.CurrentActor.desk;
import static com.shiguang.studio.service.support.CurrentActor.user;
import static com.shiguang.studio.service.support.ServiceChecks.page;
import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.dto.OperationsRequests.CustomerUpdate;
import com.shiguang.studio.dto.OperationsRequests.EmployeeRequest;
import com.shiguang.studio.dto.OperationsRequests.PasswordRequest;
import com.shiguang.studio.dto.OperationsRequests.ProfileRequest;
import com.shiguang.studio.entity.SysUser;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.mapper.OperationsMapper;
import com.shiguang.studio.mapper.UserMapper;
import com.shiguang.studio.security.AccountStateCache;
import com.shiguang.studio.service.BookingConfigurationLock;
import com.shiguang.studio.service.BookingService;
import com.shiguang.studio.service.support.BusinessEvents;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountManagementService {
  private final OperationsMapper ops;
  private final BookingMapper data;
  private final BookingService booking;
  private final UserMapper users;
  private final PasswordEncoder encoder;
  private final AccountStateCache accountStateCache;
  private final BookingReadCache readCache;
  private final BookingConfigurationLock configurationLock;
  private final BusinessEvents events;

  public AccountManagementService(
      OperationsMapper ops,
      BookingMapper data,
      BookingService booking,
      UserMapper users,
      PasswordEncoder encoder,
      AccountStateCache accountStateCache,
      BookingReadCache readCache,
      BookingConfigurationLock configurationLock,
      BusinessEvents events) {
    this.ops = ops;
    this.data = data;
    this.booking = booking;
    this.users = users;
    this.encoder = encoder;
    this.accountStateCache = accountStateCache;
    this.readCache = readCache;
    this.configurationLock = configurationLock;
    this.events = events;
  }

  public Object employees() {
    admin();
    return ops.employees();
  }

  @Transactional
  public void employee(Long id, EmployeeRequest r) {
    admin();
    configurationLock.write();
    require(
        r.password() == null || r.password().getBytes(StandardCharsets.UTF_8).length <= 72,
        "密码不能超过72字节");
    require(
        r.roles() != null
            && !r.roles().isEmpty()
            && r.roles().stream()
                .allMatch(x -> x != null && Set.of("RECEPTIONIST", "PHOTOGRAPHER").contains(x)),
        "员工仅可分配接待或摄影师角色");
    require(!r.bookable() || r.roles().contains("PHOTOGRAPHER"), "仅摄影师可开放预约");
    long uid;
    if (id == null) {
      require(r.password() != null && !r.password().isBlank(), "新员工必须设置密码");
      var u = new SysUser();
      u.setPhone(r.phone());
      u.setDisplayName(r.name().strip());
      u.setPasswordHash(encoder.encode(r.password()));
      u.setEnabled(r.enabled());
      users.insert(u);
      uid = u.getId();
      users.addEmployee(uid, r.employeeNo(), r.name().strip(), r.bookable());
      id = data.employeeId(uid);
    } else {
      Long existingUser = ops.employeeUser(id);
      require(existingUser != null, "员工不存在");
      uid = existingUser;
      data.lockUser(uid);
      require(ops.lockEmployee(id) != null, "员工不存在");
      require(!users.roles(uid).contains("ADMIN"), "管理员账号不通过员工管理修改");
      String passwordHash =
          r.password() == null || r.password().isBlank() ? null : encoder.encode(r.password());
      ops.employeeAccount(uid, r.phone(), r.name().strip(), r.enabled(), passwordHash);
      ops.revoke(uid);
      ops.clearRoles(uid);
    }
    for (String role : new LinkedHashSet<>(r.roles())) users.addRole(uid, role);
    ops.employeeSave(id, r.name().strip(), r.employeeNo(), r.enabled(), r.bookable());
    accountStateCache.evictAfterCommit(uid);
    readCache.invalidateConfigurationAfterCommit();
    events.audit(
        user(), "EMPLOYEE", "SAVE", id, r.name() + " / " + r.roles() + " / 启用：" + r.enabled());
  }

  public Object customers(int p, int s, String q) {
    desk();
    page(p, s);
    require(q.length() <= 80, "搜索内容过长");
    return page(ops.customers(q, s, (p - 1) * s), ops.customerCount(q), p, s);
  }

  public Object customer(long id, int p, int s) {
    desk();
    page(p, s);
    var c = ops.customer(id);
    require(c != null, "客户不存在");
    return Map.of(
        "profile",
        c,
        "history",
        page(
            data.appointments(id, null, null, s, (p - 1) * s).stream()
                .map(a -> booking.detail(a.getId()))
                .toList(),
            data.countAppointments(id, null, null),
            p,
            s));
  }

  @Transactional
  public void updateCustomer(long id, CustomerUpdate r) {
    desk();
    require(ops.customer(id) != null, "客户不存在");
    ops.customerSave(id, r.name(), r.phone(), r.remark());
    events.audit(user(), "CUSTOMER", "UPDATE", id, "更新客户资料");
  }

  @Transactional
  public void resetCustomerPassword(long id, PasswordRequest r) {
    admin();
    require(r.password().getBytes(StandardCharsets.UTF_8).length <= 72, "密码不能超过72字节");
    var c = ops.customer(id);
    require(c != null && c.get("userId") != null, "该客户未注册登录账号");
    long uid = ((Number) c.get("userId")).longValue();
    data.lockUser(uid);
    require(users.roles(uid).equals(List.of("CUSTOMER")), "仅可重置客户账号");
    ops.password(uid, encoder.encode(r.password()));
    ops.revoke(uid);
    accountStateCache.evictAfterCommit(uid);
    events.audit(user(), "CUSTOMER", "RESET_PASSWORD", id, "已核实身份并重置密码");
  }

  public Object profile() {
    var u = users.selectById(user());
    Long c = data.customerId(user());
    return Map.of(
        "displayName",
        u.getDisplayName(),
        "phone",
        u.getPhone(),
        "contactPhone",
        c == null ? u.getPhone() : ops.customer(c).get("phone"));
  }

  @Transactional
  public void profile(ProfileRequest r) {
    long uid = user();
    data.lockUser(uid);
    ops.profile(uid, r.displayName().strip());
    Long c = data.customerId(uid);
    if (c != null) ops.customerContact(c, r.displayName().strip(), r.contactPhone());
    accountStateCache.evictAfterCommit(uid);
    events.audit(uid, "PROFILE", "UPDATE", uid, "修改个人资料");
  }
}
