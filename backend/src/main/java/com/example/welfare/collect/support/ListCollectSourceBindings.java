package com.example.welfare.collect.support;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
public final class ListCollectSourceBindings {

    private ListCollectSourceBindings() {
    }

    public static ListCollectSourceBinding<YouthApiDto.Item> youth(WelfareServiceMapper mapper) {
        return CollectSourceRegistry.YOUTH.listBinding(mapper);
    }

    public static ListCollectSourceBinding<BokjiroCentralDto.Item> bokjiroCentral(WelfareServiceMapper mapper) {
        return CollectSourceRegistry.BOKJIRO_CENTRAL.listBinding(mapper);
    }

    public static ListCollectSourceBinding<BokjiroLocalDto.Item> bokjiroLocal(WelfareServiceMapper mapper) {
        return CollectSourceRegistry.BOKJIRO_LOCAL.listBinding(mapper);
    }
}
