package com.shiguang.studio.service;

import com.shiguang.studio.dto.OperationsRequests.*;
import java.time.*;
import java.util.*;

public interface OperationsService {
  Object activity(long id);

  Object act(long id, String action, ActionRequest r);

  Object reschedule(long id, RescheduleRequest r);

  Object payment(long id, PaymentRequest r);

  Object refund(long id, PaymentRequest r);

  void notes(long id, NotesRequest r);

  Object requests(int page, int size);

  void createRequest(RequestCreate r);

  void handleRequest(long id, RequestHandle r);

  Object notifications(int page, int size);

  void readNotification(long id);

  RuleRequest rules();

  void saveRules(RuleRequest r);

  Object dashboard(LocalDate from, LocalDate to);

  Object logs(int page, int size);

  Object employees();

  void employee(Long id, EmployeeRequest r);

  Object customers(int page, int size, String q);

  Object customer(long id, int page, int size);

  void updateCustomer(long id, CustomerUpdate r);

  void resetCustomerPassword(long id, PasswordRequest r);

  Object profile();

  void profile(ProfileRequest r);

  Object mySchedule(LocalDate date);
}
