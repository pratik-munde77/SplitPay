package com.splitpay.receipt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/receipts")
public class ReceiptController {
 private final ClassificationService classifier;
 public ReceiptController(ClassificationService classifier){this.classifier=classifier;}
 public record Input(@NotBlank @Size(max=200) String merchant,@Size(max=1000) String description){}
 @PostMapping("/classify") public ClassificationService.Result classify(@Valid @RequestBody Input input){return classifier.classify(input.merchant(),input.description()==null?"":input.description());}
}
