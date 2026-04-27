package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.policy.entity.WelfareService;

public interface PriorityMatcher {

    boolean matches(PriorityPreference priority, WelfareService service);
}
