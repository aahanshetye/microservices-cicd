package com.demo.accounts.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
public class AccountsController {
  @GetMapping("/health")
  public Map<String,String> health(){
    return Map.of("status","ok","service","accounts");
  }

  @GetMapping("/accounts")
  public List<Map<String,Object>> list(){
    return List.of(
      Map.of("id",101, "name","Riya", "tier","GOLD"),
      Map.of("id",102, "name","Dev",  "tier","SILVER")
    );
  }
}
