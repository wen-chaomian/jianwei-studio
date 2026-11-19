package com.shiguang.studio.cache;

public class CacheBusyException extends RuntimeException {
  public CacheBusyException() {
    super("数据服务暂时繁忙，请稍后重试");
  }
}
