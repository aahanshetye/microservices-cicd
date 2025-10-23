package com.demo.billing.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
public class BillingController {
  @GetMapping("/health")
  public Map<String,String> health(){
    return Map.of("status","ok","service","billing");
  }

  @GetMapping("/invoices")
  public List<Map<String,Object>> invoices(){
    return List.of(
      Map.of("invoiceNo","INV-9001","amount",1499),
      Map.of("invoiceNo","INV-9002","amount",799)
    );
  }
}
