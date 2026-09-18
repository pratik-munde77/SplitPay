package com.splitpay.settlement;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/groups/{groupId}/settlements")
public class SettlementController {
 private final SettlementService service;
 public SettlementController(SettlementService service){this.service=service;}
 @GetMapping public Object list(@PathVariable UUID groupId){return service.list(groupId);}
 @PostMapping public Object create(@PathVariable UUID groupId,@Valid @RequestBody SettlementService.Input input){return service.create(groupId,input);}
}
