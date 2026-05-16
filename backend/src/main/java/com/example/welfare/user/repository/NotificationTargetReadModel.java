package com.example.welfare.user.repository;

public interface NotificationTargetReadModel {

    Long getUserId();

    String getUserKey();

    String getNotificationPeriod();

    Boolean getNotificationEmailYn();

    Boolean getNotificationInAppYn();

    Boolean getNotificationWebPushYn();

    Double getNotificationMinScore();

    int getDisplayCount();
}
