package com.shiguang.studio.service.support;

import static com.shiguang.studio.service.support.CurrentActor.user;
import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.mapper.OperationsMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class CommandReceipts {
  private final OperationsMapper ops;

  public CommandReceipts(OperationsMapper ops) {
    this.ops = ops;
  }

  public String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public boolean replay(String request, String payload) {
    String old = ops.receipt(user(), request);
    require(old == null || old.equals(hash(payload)), "请求号已被其他操作使用");
    return old != null;
  }

  public void done(String request, String payload) {
    ops.receiptSave(user(), request, hash(payload));
  }
}
