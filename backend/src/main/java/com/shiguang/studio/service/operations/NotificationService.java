package com.shiguang.studio.service.operations;

import static com.shiguang.studio.service.support.CurrentActor.role;
import static com.shiguang.studio.service.support.CurrentActor.user;
import static com.shiguang.studio.service.support.ServiceChecks.page;

import com.shiguang.studio.cache.SyncVersions;
import com.shiguang.studio.mapper.OperationsMapper;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
  private final OperationsMapper ops;
  private final SyncVersions sync;

  public NotificationService(OperationsMapper ops, SyncVersions sync) {
    this.ops = ops;
    this.sync = sync;
  }

  public Object notifications(int p, int s) {
    page(p, s);
    boolean financial = role("CUSTOMER") || role("ADMIN") || role("RECEPTIONIST");
    return Map.of(
        "items",
        ops.notifications(user(), s, (p - 1) * s, financial),
        "total",
        ops.notificationCount(user(), financial),
        "unread",
        ops.unread(user(), financial),
        "page",
        p,
        "size",
        s);
  }

  @Transactional
  public void readNotification(long id) {
    if (ops.read(id, user()) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    sync.user(user());
  }
}
