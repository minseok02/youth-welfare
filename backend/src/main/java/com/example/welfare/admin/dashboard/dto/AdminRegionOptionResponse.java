package com.example.welfare.admin.dashboard.dto;

import java.util.List;

public record AdminRegionOptionResponse(
        List<RegionOption> regions
) {
    public record RegionOption(
            String regionCode,
            String sidoName,
            String sggName,
            String label
    ) {
    }
}
