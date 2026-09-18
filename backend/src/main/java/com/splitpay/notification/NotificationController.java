package com.splitpay.notification;
import com.splitpay.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
@RestController @RequestMapping("/api") @Transactional
public class NotificationController {
 private final CurrentUser current;private final NotificationRepository notifications;private final DeviceRepository devices;
 public NotificationController(CurrentUser current,NotificationRepository notifications,DeviceRepository devices){this.current=current;this.notifications=notifications;this.devices=devices;}
 public record Token(@NotBlank @Size(max=2048) String token){}
 @GetMapping("/notifications") public Object list(){return notifications.findTop100ByUserIdOrderByCreatedAtDesc(current.get().id);}
 @PostMapping("/notifications/{id}/read") public void read(@PathVariable UUID id){var item=notifications.findById(id).orElseThrow();if(!item.userId.equals(current.get().id))throw new AccessDeniedException("Not your notification");item.readAt=Instant.now();}
 @PostMapping("/devices") public void register(@Valid @RequestBody Token input)throws Exception{
  String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.token().getBytes(StandardCharsets.UTF_8)));
  var device=devices.findById(id).orElseGet(DeviceToken::new);device.id=id;device.userId=current.get().id;device.token=input.token();device.updatedAt=Instant.now();devices.save(device);
 }
 @PostMapping("/devices/unregister") public void unregister(@Valid @RequestBody Token input)throws Exception{
  String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.token().getBytes(StandardCharsets.UTF_8)));
  devices.findById(id).filter(d->d.userId.equals(current.get().id)).ifPresent(devices::delete);
 }
}
