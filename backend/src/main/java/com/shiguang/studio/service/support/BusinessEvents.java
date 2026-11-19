package com.shiguang.studio.service.support;

import static com.shiguang.studio.service.support.CurrentActor.user;

import com.shiguang.studio.cache.SyncVersions;
import com.shiguang.studio.entity.Appointment;
import com.shiguang.studio.mapper.OperationsMapper;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class BusinessEvents {
  private final OperationsMapper ops;
  private final SyncVersions sync;

  public BusinessEvents(OperationsMapper ops, SyncVersions sync) {
    this.ops = ops;
    this.sync = sync;
  }

  public void record(Appointment a, String action, String detail) {
    ops.log(a.getId(), action, user(), detail);
    audit(user(), "APPOINTMENT", action, a.getId(), detail);
    String content = a.getPackageName() + " · " + a.getStartTime() + "：" + detail;
    Long customer = ops.customerUser(a.getCustomerId()),
        employee = ops.employeeUser(a.getPhotographerId());
    if (customer != null) notifyUser(customer, actionTitle(action), content, a.getId());
    if (employee != null && !Set.of("payment", "refund").contains(action))
      notifyUser(employee, actionTitle(action), content, a.getId());
  }

  public String actionTitle(String action) {
    return switch (action) {
      case "cancel" -> "预约已取消";
      case "reschedule" -> "预约已改期";
      case "check-in" -> "已登记到店";
      case "start" -> "拍摄已开始";
      case "complete" -> "拍摄已完成";
      case "payment" -> "已登记收款";
      case "refund" -> "已登记退款";
      case "mark-no-show" -> "已标记爽约";
      case "service-notes" -> "服务备注已更新";
      default -> "预约进展";
    };
  }

  public void audit(long user, String module, String action, Long target, String detail) {
    ops.audit(user, module, action, target, detail);
    sync.changed("desk");
    sync.user(user);
  }

  public void notifyUser(long user, String title, String content, Long appointment) {
    ops.notify(user, title, content, appointment);
    sync.user(user);
  }
}
