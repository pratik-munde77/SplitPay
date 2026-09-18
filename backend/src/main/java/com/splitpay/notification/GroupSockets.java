package com.splitpay.notification;
import com.splitpay.group.GroupRepository;
import com.splitpay.user.CurrentUser;
import com.google.gson.Gson;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class GroupSockets extends TextWebSocketHandler implements HandshakeInterceptor {
 private final CurrentUser current;private final GroupRepository groups;private final Map<String,WebSocketSession> sessions=new ConcurrentHashMap<>();
 public GroupSockets(CurrentUser current,GroupRepository groups){this.current=current;this.groups=groups;}
 @Override public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes){attributes.put("userId",current.get().id);return true;}
 @Override public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception exception){}
 @Override public void afterConnectionEstablished(WebSocketSession session){sessions.put(session.getId(),session);}
 @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status){sessions.remove(session.getId());}
 @Async @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
 public void changed(GroupChanged event){
  try{groups.findById(event.groupId()).ifPresent(group->{
   String json=new Gson().toJson(Map.of("groupId",group.id.toString(),"type",event.type()));
   for(var session:sessions.values())if(group.memberIds.contains(session.getAttributes().get("userId"))){
    try{synchronized(session){if(session.isOpen())session.sendMessage(new TextMessage(json));}}catch(Exception ignored){sessions.remove(session.getId());}
   }
  });}catch(Exception ignored){/* REST refresh remains available if live delivery fails. */}
 }
}
