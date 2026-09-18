package com.splitpay;
import com.splitpay.receipt.ClassificationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class ClassificationTest {
 @Test void knownMerchantsDoNotNeedCredentials(){var service=new ClassificationService(new MockEnvironment());assertEquals("Food",service.classify("SWIGGY","Order").category());assertEquals("Travel",service.classify("Uber","Ride").category());}
 @Test void noKeyAndInvalidOutputFallBack(){var service=new ClassificationService(new MockEnvironment());assertEquals("Other",service.classify("Unknown store","").category());assertEquals("Other",ClassificationService.parse("{\"merchant\":\"A\",\"category\":\"MadeUp\",\"confidence\":1}","A").category());}
}
