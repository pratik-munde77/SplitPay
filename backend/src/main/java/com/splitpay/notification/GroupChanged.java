package com.splitpay.notification;
import java.util.UUID;
public record GroupChanged(UUID groupId,UUID actorId,String type,String description,UUID activityId){}
