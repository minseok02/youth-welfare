package com.example.welfare.collect.support;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.policy.entity.WelfareService;

public final class ListCollectSourceBindings {

    private ListCollectSourceBindings() {
    }

    public static ListCollectSourceBinding<YouthApiDto.Item> youth(WelfareServiceMapper mapper) {
        return new ListCollectSourceBinding<>(
                WelfareService.SourceType.YOUTH,
                YouthApiDto.Item::getPlcyNo,
                RawFieldValidator::recordStatsYouth,
                mapper::fromYouth,
                mapper::regionsFromYouth,
                mapper::tagsFromYouth,
                mapper::toNormalizedYouth
        );
    }

    public static ListCollectSourceBinding<BokjiroCentralDto.Item> bokjiroCentral(WelfareServiceMapper mapper) {
        return new ListCollectSourceBinding<>(
                WelfareService.SourceType.BOKJIRO_CENTRAL,
                BokjiroCentralDto.Item::getServId,
                RawFieldValidator::recordStatsBokjiroCentral,
                mapper::fromBokjiroCentral,
                mapper::regionsFromBokjiroCentral,
                mapper::tagsFromBokjiroCentral,
                item -> mapper.toNormalizedBokjiroCentral(item, null)
        );
    }

    public static ListCollectSourceBinding<BokjiroLocalDto.Item> bokjiroLocal(WelfareServiceMapper mapper) {
        return new ListCollectSourceBinding<>(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                BokjiroLocalDto.Item::getServId,
                RawFieldValidator::recordStatsBokjiroLocal,
                mapper::fromBokjiroLocal,
                mapper::regionsFromBokjiroLocal,
                mapper::tagsFromBokjiroLocal,
                item -> mapper.toNormalizedBokjiroLocal(item, null)
        );
    }
}
