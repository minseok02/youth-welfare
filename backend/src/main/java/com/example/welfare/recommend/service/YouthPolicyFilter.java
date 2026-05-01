package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.support.RecommendationYouthRelevanceSupport;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Deprecated
public class YouthPolicyFilter {

    public boolean isYouthRelevant(WelfareService service, List<ServiceTag> tags) {
        return RecommendationYouthRelevanceSupport.computeYouthRelevant(service, tags);
    }

    public double relevanceBonus(WelfareService service, List<ServiceTag> tags) {
        return RecommendationYouthRelevanceSupport.computeRelevanceBonus(service, tags);
    }
}
