package com.example.welfare.policy.repository;

import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.policy.entity.WelfareService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@Slf4j
public class WelfareServiceReadRepositoryImpl implements WelfareServiceReadRepository {

    private static final String ACTIVE_ONLY_COUNT_CACHE_KEY = "policy:list:active-only:count:v1";
    private static final Duration DEFAULT_ACTIVE_LIST_COUNT_CACHE_TTL = Duration.ofSeconds(30);

    private final WelfareServiceRepository welfareServiceRepository;
    private final WelfareServiceSearchRepository welfareServiceSearchRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final Duration activeListCountCacheTtl;

    @Autowired
    public WelfareServiceReadRepositoryImpl(WelfareServiceRepository welfareServiceRepository,
                                            WelfareServiceSearchRepository welfareServiceSearchRepository,
                                            RedisTemplate<String, String> redisTemplate,
                                            @Value("${policy.cache.active-list-count.ttl-seconds:30}") long activeListCountCacheTtlSeconds) {
        this(
                welfareServiceRepository,
                welfareServiceSearchRepository,
                redisTemplate,
                Duration.ofSeconds(activeListCountCacheTtlSeconds)
        );
    }

    WelfareServiceReadRepositoryImpl(WelfareServiceRepository welfareServiceRepository,
                                     WelfareServiceSearchRepository welfareServiceSearchRepository) {
        this(welfareServiceRepository, welfareServiceSearchRepository, null, DEFAULT_ACTIVE_LIST_COUNT_CACHE_TTL);
    }

    WelfareServiceReadRepositoryImpl(WelfareServiceRepository welfareServiceRepository,
                                     WelfareServiceSearchRepository welfareServiceSearchRepository,
                                     RedisTemplate<String, String> redisTemplate,
                                     Duration activeListCountCacheTtl) {
        this.welfareServiceRepository = welfareServiceRepository;
        this.welfareServiceSearchRepository = welfareServiceSearchRepository;
        this.redisTemplate = redisTemplate;
        this.activeListCountCacheTtl = activeListCountCacheTtl == null
                || activeListCountCacheTtl.isNegative()
                || activeListCountCacheTtl.isZero()
                ? DEFAULT_ACTIVE_LIST_COUNT_CACHE_TTL
                : activeListCountCacheTtl;
    }

    @Override
    public Page<WelfareService> findList(PolicyListReadCondition condition, Pageable pageable) {
        String sido = RegionCodeUtil.fullSidoName(condition.sido());
        String sidoCode = RegionCodeUtil.getSidoCode(sido);
        String regionCode = RegionCodeUtil.getRegionCode(sido, condition.sgg());
        Integer onlineApplyInt = condition.onlineApply() == null ? null : (condition.onlineApply() ? 1 : 0);
        // native query handles ORDER BY — pass unsorted pageable for page/size only
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        if (shouldUseActiveOnlyListFastPath(condition, sido, regionCode, onlineApplyInt)) {
            return findActiveOnlyList(condition.sort(), unsorted);
        }
        return welfareServiceRepository.findListWithFilters(
                condition.category(),
                condition.sourceType() != null ? condition.sourceType().name() : null,
                condition.status() != null ? condition.status().name() : null,
                condition.statusFilter(),
                sido,
                condition.sgg(),
                sidoCode,
                regionCode,
                onlineApplyInt,
                condition.sort(),
                condition.incomeMaxWon(),
                condition.targetGroup(),
                condition.gov24ServiceField(),
                condition.gov24UserType(),
                condition.gov24BenefitType(),
                unsorted
        );
    }

    @Override
    public Page<WelfareService> search(PolicySearchReadCondition condition, Pageable pageable) {
        return welfareServiceSearchRepository.search(condition, pageable);
    }

    private boolean shouldUseActiveOnlyListFastPath(PolicyListReadCondition condition,
                                                   String sido,
                                                   String regionCode,
                                                   Integer onlineApplyInt) {
        return condition.status() == null
                && "ACTIVE_ONLY".equals(condition.statusFilter())
                && condition.category() == null
                && condition.sourceType() == null
                && sido == null
                && condition.sgg() == null
                && regionCode == null
                && onlineApplyInt == null
                && condition.incomeMaxWon() == null
                && condition.targetGroup() == null
                && condition.gov24ServiceField() == null
                && condition.gov24UserType() == null
                && condition.gov24BenefitType() == null
                && isFastPathSort(condition.sort());
    }

    private boolean isFastPathSort(String sort) {
        return "LATEST".equals(sort) || "DEADLINE".equals(sort) || "VIEWS".equals(sort);
    }

    private Page<WelfareService> findActiveOnlyList(String sort, Pageable pageable) {
        List<WelfareService> rows = switch (sort) {
            case "DEADLINE" -> welfareServiceRepository.findActiveOnlyDeadlineListRows(pageable);
            case "VIEWS" -> welfareServiceRepository.findActiveOnlyViewsListRows(pageable);
            case "LATEST" -> welfareServiceRepository.findActiveOnlyLatestListRows(pageable);
            default -> throw new IllegalArgumentException("Unsupported active list fast-path sort: " + sort);
        };
        return new PageImpl<>(rows, pageable, activeOnlyVisibleCount());
    }

    private long activeOnlyVisibleCount() {
        Long cached = readActiveOnlyVisibleCountCache();
        if (cached != null) {
            return cached;
        }
        long count = welfareServiceRepository.countActiveOnlyVisibleList();
        writeActiveOnlyVisibleCountCache(count);
        return count;
    }

    private Long readActiveOnlyVisibleCountCache() {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String cached = redisTemplate.opsForValue().get(ACTIVE_ONLY_COUNT_CACHE_KEY);
            if (cached == null || cached.isBlank()) {
                return null;
            }
            return Long.parseLong(cached);
        } catch (Exception e) {
            log.warn("[WelfareServiceReadRepositoryImpl] Redis active list count cache read failed errorType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }

    private void writeActiveOnlyVisibleCountCache(long count) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(ACTIVE_ONLY_COUNT_CACHE_KEY, Long.toString(count), activeListCountCacheTtl);
        } catch (Exception e) {
            log.warn("[WelfareServiceReadRepositoryImpl] Redis active list count cache write failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }
}
