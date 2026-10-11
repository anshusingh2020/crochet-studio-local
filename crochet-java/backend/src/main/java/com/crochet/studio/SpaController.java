package com.crochet.studio;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
  @GetMapping({
    "/",
    "/login",
    "/logout",
    "/account",
    "/policies",
    "/admin",
    "/admin/login",
    "/admin/requests"
  })
  public String app() {
    return "forward:/index.html";
  }
}
