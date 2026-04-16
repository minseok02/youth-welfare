package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.user.entity.UserPriority;

public interface PriorityMatcher {

    boolean matches(UserPriority priority, WelfareService service);
}

