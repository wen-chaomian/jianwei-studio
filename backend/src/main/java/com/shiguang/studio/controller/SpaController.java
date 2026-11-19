package com.shiguang.studio.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
  @GetMapping({
    "/login",
    "/register",
    "/customer",
    "/customer/**",
    "/admin",
    "/admin/**",
    "/staff",
    "/staff/**"
  })
  public String page() {
    return "forward:/index.html";
  }
}
