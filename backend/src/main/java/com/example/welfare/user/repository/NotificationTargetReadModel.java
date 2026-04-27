package com.example.welfare.user.repository;

public interface NotificationTargetReadModel {

    Long getUserId();

    String getUserKey();

    String getEmailEnc();

    String getNotificationPeriod();

    Double getNotificationMinScore();

    int getDisplayCount();
}
