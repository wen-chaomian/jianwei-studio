package com.shiguang.studio.service.operations;

import static com.shiguang.studio.service.support.CurrentActor.role;
import static com.shiguang.studio.service.support.ServiceChecks.now;
import static com.shiguang.studio.service.support.ServiceChecks.requestLabel;

import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.mapper.OperationsMapper;
import com.shiguang.studio.service.BookingService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentActivityService {
  private final OperationsMapper ops;
  private final BookingMapper data;
  private final BookingService booking;
  private final StudioManagementService rulesService;

  public AppointmentActivityService(
      OperationsMapper ops,
      BookingMapper data,
      BookingService booking,
      StudioManagementService rulesService) {
    this.ops = ops;
    this.data = data;
    this.booking = booking;
    this.rulesService = rulesService;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Object activity(long id) {
    var a = booking.detail(id);
    boolean financial = role("CUSTOMER") || role("ADMIN") || role("RECEPTIONIST");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("version", ops.version(id));
    var requests = ops.appointmentRequests(id);
    result.put("requests", requests);
    result.put("pendingChange", ops.pendingChanges(id) > 0);
    var entity = data.appointment(id);
    var supportedPhotographers = new HashSet<>(data.photographerIds(entity.getPackageId()));
    result.put(
        "photographers",
        data.photographers().stream()
            .filter(
                p ->
                    p.getEnabled() && p.getBookable() && supportedPhotographers.contains(p.getId()))
            .map(p -> Map.of("id", p.getId(), "name", p.getName()))
            .toList());
    result.put(
        "selfChangeAllowed",
        "CONFIRMED".equals(entity.getStatus())
            && !now()
                .plusHours(rulesService.rules().changeCutoffHours())
                .isAfter(entity.getStartTime()));
    result.put("service", ops.service(id));
    var history = new ArrayList<>(ops.logs(id));
    if (!financial)
      history.removeIf(log -> Set.of("payment", "refund").contains(log.get("action")));
    for (var request : requests) {
      history.add(
          Map.of(
              "id",
              "request-" + request.get("id"),
              "action",
              "request-submitted",
              "detail",
              "提交"
                  + requestLabel(request.get("type").toString())
                  + " #"
                  + request.get("id")
                  + "："
                  + request.get("content"),
              "operator",
              a.customerName(),
              "createdAt",
              request.get("createdAt")));
    }
    history.sort(
        (x, y) ->
            y.get("createdAt")
                .toString()
                .replace('T', ' ')
                .compareTo(x.get("createdAt").toString().replace('T', ' ')));
    result.put("logs", history);
    if (financial) {
      var payments = ops.payments(id);
      BigDecimal received = BigDecimal.ZERO, refunded = BigDecimal.ZERO;
      for (var payment : payments) {
        BigDecimal amount = (BigDecimal) payment.get("amount");
        if ("PAYMENT".equals(payment.get("type"))) received = received.add(amount);
        else if ("REFUND".equals(payment.get("type"))) refunded = refunded.add(amount);
      }
      BigDecimal net = received.subtract(refunded);
      result.put("payments", payments);
      result.put("received", received);
      result.put("refunded", refunded);
      result.put("net", net);
      result.put(
          "collectionStatus",
          net.compareTo(a.amount()) >= 0 ? "已收足" : net.signum() > 0 ? "部分收款" : "未收款");
      result.put(
          "refundStatus",
          refunded.signum() == 0 ? "无退款" : refunded.compareTo(received) >= 0 ? "全额退款" : "部分退款");
    }
    return result;
  }
}
