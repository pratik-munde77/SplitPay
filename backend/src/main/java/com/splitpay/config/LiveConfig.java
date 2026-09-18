package com.splitpay.config;
import com.splitpay.notification.GroupSockets;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.*;
@Configuration @EnableWebSocket @EnableAsync
public class LiveConfig implements WebSocketConfigurer {
 private final GroupSockets sockets;
 public LiveConfig(GroupSockets sockets){this.sockets=sockets;}
 @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){registry.addHandler(sockets,"/api/live").addInterceptors(sockets);}
 @Bean(name="taskExecutor") public java.util.concurrent.Executor taskExecutor(){
  var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(1);executor.setMaxPoolSize(2);executor.setQueueCapacity(128);executor.setThreadNamePrefix("splitpay-events-");executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());executor.initialize();return executor;
 }
}
