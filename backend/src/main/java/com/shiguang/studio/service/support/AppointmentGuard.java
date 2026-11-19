package com.shiguang.studio.service.support;

import static com.shiguang.studio.service.support.CurrentActor.desk;
import static com.shiguang.studio.service.support.CurrentActor.role;
import static com.shiguang.studio.service.support.ServiceChecks.now;
import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.cache.SyncVersions;
import com.shiguang.studio.dto.OperationsRequests.RuleRequest;
import com.shiguang.studio.entity.Appointment;
import com.shiguang.studio.mapper.OperationsMapper;
import com.shiguang.studio.service.BookingService;
import java.time.LocalTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppointmentGuard {
  private final OperationsMapper ops;
  private final BookingService booking;
  private final SyncVersions sync;

  public AppointmentGuard(OperationsMapper ops, BookingService booking, SyncVersions sync) {
    this.ops = ops;
    this.booking = booking;
    this.sync = sync;
  }

  public Appointment locked(long id) {
    var a = ops.lockAppointment(id);
    if (a == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    booking.detail(id);
    sync.user(ops.customerUser(a.getCustomerId()));
    sync.user(ops.employeeUser(a.getPhotographerId()));
    return a;
  }

  public void version(long id, int version) {
    require(ops.version(id) == version, "预约已被其他人修改，请刷新后重试");
  }

  public void noPendingChange(long id) {
    require(ops.pendingChanges(id) == 0, "此预约有待处理的取消或改期申请，请先处理申请");
  }

  public void mayChange(Appointment a, String reason) {
    require(a.getStatus().equals("CONFIRMED"), "仅待到店预约可改期或取消");
    if (role("CUSTOMER")) {
      require(
          !now().plusHours(currentRules().changeCutoffHours()).isAfter(a.getStartTime()),
          "已超过自助办理时限，请提交人工申请");
    } else {
      desk();
      require(reason != null && !reason.isBlank(), "员工办理必须填写原因");
    }
  }

  public RuleRequest currentRules() {
    var r = ops.rule();
    return new RuleRequest(
        LocalTime.parse(r.get("open_time").toString()),
        LocalTime.parse(r.get("close_time").toString()),
        ((Number) r.get("slot_minutes")).intValue(),
        ((Number) r.get("min_advance_hours")).intValue(),
        ((Number) r.get("max_advance_days")).intValue(),
        ((Number) r.get("change_cutoff_hours")).intValue());
  }
}
