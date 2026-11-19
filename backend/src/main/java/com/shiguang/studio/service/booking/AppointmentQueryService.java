package com.shiguang.studio.service.booking;

import static com.shiguang.studio.service.support.CurrentActor.role;
import static com.shiguang.studio.service.support.CurrentActor.user;
import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.entity.Appointment;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.vo.BookingViews.AppointmentVO;
import com.shiguang.studio.vo.BookingViews.PageVO;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppointmentQueryService {
  private final BookingMapper mapper;

  public AppointmentQueryService(BookingMapper mapper) {
    this.mapper = mapper;
  }

  public AppointmentVO vo(Appointment a) {
    return new AppointmentVO(
        a.getId(),
        a.getAppointmentNo(),
        a.getPackageName(),
        a.getPhotographerName(),
        a.getVenueName(),
        a.getCustomerName(),
        a.getCustomerPhone(),
        a.getStartTime(),
        a.getShootEndTime(),
        a.getOccupiedEndTime(),
        a.getStatus(),
        a.getAmount(),
        a.getRemark(),
        a.getPendingRequestType());
  }

  public Long scopedCustomer() {
    if (!role("CUSTOMER")) return null;
    var id = mapper.customerId(user());
    require(id != null, "客户档案不存在");
    return id;
  }

  public Long scopedPhotographer() {
    if (role("ADMIN") || role("RECEPTIONIST") || role("CUSTOMER")) return null;
    require(role("PHOTOGRAPHER"), "没有访问权限");
    var id = mapper.employeeId(user());
    require(id != null, "员工档案不存在");
    return id;
  }

  public PageVO<AppointmentVO> appointments(int page, int size, LocalDate date) {
    require(page >= 1 && page <= 100000 && size >= 1 && size <= 100, "分页参数不正确");
    var customer = scopedCustomer();
    var photographer = scopedPhotographer();
    return new PageVO<>(
        mapper.appointments(customer, photographer, date, size, (page - 1) * size).stream()
            .map(this::vo)
            .toList(),
        mapper.countAppointments(customer, photographer, date),
        page,
        size);
  }

  public AppointmentVO detail(long id) {
    var a = mapper.appointment(id);
    if (a == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    var customer = scopedCustomer();
    var photographer = scopedPhotographer();
    if (customer != null && !customer.equals(a.getCustomerId())
        || photographer != null && !photographer.equals(a.getPhotographerId()))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return vo(a);
  }
}
