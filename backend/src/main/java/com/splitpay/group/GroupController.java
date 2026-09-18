package com.splitpay.group;
import com.splitpay.expense.SharedExpense;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class GroupController {
 private final GroupService service;
 public GroupController(GroupService service) { this.service=service; }
 public record MemberInput(@NotBlank @Email String email) {}
 @GetMapping("/groups") public Object groups() { return service.list(); }
 @PostMapping("/groups") public Object create(@Valid @RequestBody GroupService.GroupInput input) { return service.create(input); }
 @GetMapping("/groups/{id}") public Object details(@PathVariable UUID id) { return service.details(id); }
 @PutMapping("/groups/{id}") public Object update(@PathVariable UUID id,@Valid @RequestBody GroupService.GroupInput input) { return service.update(id,input); }
 @DeleteMapping("/groups/{id}") public void delete(@PathVariable UUID id) { service.delete(id); }
 @PostMapping("/groups/{id}/members") public Object add(@PathVariable UUID id,@Valid @RequestBody MemberInput input) { return service.addMember(id,input.email()); }
 @DeleteMapping("/groups/{id}/members/{userId}") public void remove(@PathVariable UUID id,@PathVariable UUID userId) { service.removeMember(id,userId); }
 @GetMapping("/groups/{id}/expenses") public Object expenses(@PathVariable UUID id) { return service.details(id).expenses(); }
 @PostMapping("/groups/{id}/expenses") public SharedExpense createExpense(@PathVariable UUID id,@Valid @RequestBody GroupService.ExpenseInput input) { return service.saveExpense(id,null,input); }
 @GetMapping("/expenses/{id}") public Object expense(@PathVariable UUID id) { return service.expense(id); }
 @PutMapping("/expenses/{id}") public Object updateExpense(@PathVariable UUID id,@Valid @RequestBody GroupService.ExpenseInput input) { return service.saveExpense(service.expense(id).groupId,id,input); }
 @DeleteMapping("/expenses/{id}") public void deleteExpense(@PathVariable UUID id) { service.deleteExpense(id); }
 @GetMapping("/groups/{id}/balances") public Object balances(@PathVariable UUID id) { return service.details(id).balances(); }
 @GetMapping("/groups/{id}/simplified-balances") public Object simplified(@PathVariable UUID id) { return service.details(id).simplified(); }
 @GetMapping("/groups/{id}/activity") public Object activity(@PathVariable UUID id) { return service.details(id).activity(); }
}
