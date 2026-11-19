package com.shiguang.studio.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupUrlsLogger {
  private static final Logger log = LoggerFactory.getLogger(StartupUrlsLogger.class);

  @EventListener(ApplicationReadyEvent.class)
  public void printUrls(ApplicationReadyEvent event) {
    if (!(event.getApplicationContext() instanceof ServletWebServerApplicationContext context))
      return;
    String scheme =
        context.getEnvironment().getProperty("server.ssl.enabled", Boolean.class, false)
            ? "https"
            : "http";
    String base =
        scheme
            + "://localhost:"
            + context.getWebServer().getPort()
            + context.getServletContext().getContextPath();
    log.info(
        "\n\n见微预约平台已启动，本机访问地址：\n客户预约：{}\n员工工作台：{}\n管理工作台：{}\n",
        base + "/customer",
        base + "/staff",
        base + "/admin");
  }
}
