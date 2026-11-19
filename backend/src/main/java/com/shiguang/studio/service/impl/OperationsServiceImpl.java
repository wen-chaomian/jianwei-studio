package com.shiguang.studio.service.impl;

import com.shiguang.studio.dto.OperationsRequests.ActionRequest;
import com.shiguang.studio.dto.OperationsRequests.CustomerUpdate;
import com.shiguang.studio.dto.OperationsRequests.EmployeeRequest;
import com.shiguang.studio.dto.OperationsRequests.NotesRequest;
import com.shiguang.studio.dto.OperationsRequests.PasswordRequest;
import com.shiguang.studio.dto.OperationsRequests.PaymentRequest;
import com.shiguang.studio.dto.OperationsRequests.ProfileRequest;
import com.shiguang.studio.dto.OperationsRequests.RequestCreate;
import com.shiguang.studio.dto.OperationsRequests.RequestHandle;
import com.shiguang.studio.dto.OperationsRequests.RescheduleRequest;
import com.shiguang.studio.dto.OperationsRequests.RuleRequest;
import com.shiguang.studio.service.OperationsService;
import com.shiguang.studio.service.operations.AccountManagementService;
import com.shiguang.studio.service.operations.AppointmentActivityService;
import com.shiguang.studio.service.operations.AppointmentWorkflowService;
import com.shiguang.studio.service.operations.CustomerRequestService;
import com.shiguang.studio.service.operations.NotificationService;
import com.shiguang.studio.service.operations.PaymentService;
import com.shiguang.studio.service.operations.StudioManagementService;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** 稳定的办理入口，仅委派给各业务服务；事务与校验由对应业务服务承担。 */
@Service
public class OperationsServiceImpl implements OperationsService {
  private final AppointmentActivityService activityService;
  private final StudioManagementService rulesService;
  private final AppointmentWorkflowService workflow;
  private final PaymentService payments;
  private final CustomerRequestService requestsService;
  private final AccountManagementService accounts;
  private final NotificationService notificationsService;

  public OperationsServiceImpl(
      AppointmentActivityService activityService,
      StudioManagementService rulesService,
      AppointmentWorkflowService workflow,
      PaymentService payments,
      CustomerRequestService requestsService,
      AccountManagementService accounts,
      NotificationService notificationsService) {
    this.activityService = activityService;
    this.rulesService = rulesService;
    this.workflow = workflow;
    this.payments = payments;
    this.requestsService = requestsService;
    this.accounts = accounts;
    this.notificationsService = notificationsService;
  }

  public Object activity(long id) {
    return activityService.activity(id);
  }

  public Object act(long id, String action, ActionRequest r) {
    return workflow.act(id, action, r);
  }

  public Object reschedule(long id, RescheduleRequest r) {
    return workflow.reschedule(id, r);
  }

  public Object payment(long id, PaymentRequest r) {
    return payments.payment(id, r);
  }

  public Object refund(long paymentId, PaymentRequest r) {
    return payments.refund(paymentId, r);
  }

  public void notes(long id, NotesRequest r) {
    workflow.notes(id, r);
  }

  public Object requests(int p, int s) {
    return requestsService.requests(p, s);
  }

  public void createRequest(RequestCreate r) {
    requestsService.createRequest(r);
  }

  public void handleRequest(long id, RequestHandle r) {
    requestsService.handleRequest(id, r);
  }

  public Object notifications(int p, int s) {
    return notificationsService.notifications(p, s);
  }

  public void readNotification(long id) {
    notificationsService.readNotification(id);
  }

  public RuleRequest rules() {
    return rulesService.rules();
  }

  public void saveRules(RuleRequest r) {
    rulesService.saveRules(r);
  }

  public Object dashboard(LocalDate from, LocalDate to) {
    return rulesService.dashboard(from, to);
  }

  public Object logs(int p, int s) {
    return rulesService.logs(p, s);
  }

  public Object employees() {
    return accounts.employees();
  }

  public void employee(Long id, EmployeeRequest r) {
    accounts.employee(id, r);
  }

  public Object customers(int p, int s, String q) {
    return accounts.customers(p, s, q);
  }

  public Object customer(long id, int p, int s) {
    return accounts.customer(id, p, s);
  }

  public void updateCustomer(long id, CustomerUpdate r) {
    accounts.updateCustomer(id, r);
  }

  public void resetCustomerPassword(long id, PasswordRequest r) {
    accounts.resetCustomerPassword(id, r);
  }

  public Object profile() {
    return accounts.profile();
  }

  public void profile(ProfileRequest r) {
    accounts.profile(r);
  }

  public Object mySchedule(LocalDate date) {
    return rulesService.mySchedule(date);
  }
}
