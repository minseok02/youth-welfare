package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicyDetailResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private static final long MAX_BOOKMARKS = 200;

    private final WelfareServiceRepository welfareServiceRepository;
    private final WelfareServiceDetailRepository detailRepository;
    private final ServiceRegionRepository regionRepository;
    private final ServiceTagRepository tagRepository;
    private final UserRecommendationRepository userRecommendationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<PolicySummaryResponse> getList(Long userId,
                                               String category,
                                               String sourceType,
                                               String status,
                                               Boolean includeClosed,
                                               String sido,
                                               String sgg,
                                               Boolean onlineApply,
                                               String sort,
                                               Pageable pageable) {
        Page<WelfareService> page = welfareServiceRepository.findListWithFilters(
                normalizeNullable(category),
                normalizeSourceType(sourceType),
                normalizeStatus(status),
                includeClosed != null && includeClosed,
                normalizeNullable(sido),
                normalizeNullable(sgg),
                onlineApply,
                buildPageable(pageable, sort)
        );

        Set<Long> bookmarkedServiceIds = getBookmarkedServiceIds(userId, page.getContent());
        return page.map(service -> PolicySummaryResponse.from(
                service,
                bookmarkedServiceIds.contains(service.getId())
        ));
    }

    @Transactional
    public PolicyDetailResponse getDetail(Long userId, Long serviceId, boolean increaseViewCount) {
        WelfareService ws = welfareServiceRepository.findById(serviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.POLICY_NOT_FOUND));
        if (increaseViewCount) {
            ws.increaseViewCount();
        }

        WelfareServiceDetail detail = detailRepository.findByServiceId(serviceId).orElse(null);
        List<ServiceRegion> regions = regionRepository.findByServiceId(serviceId);
        List<ServiceTag> tags = tagRepository.findByServiceId(serviceId);
        boolean bookmarked = getBookmarkedServiceIds(userId, List.of(ws)).contains(serviceId);

        return PolicyDetailResponse.of(ws, detail, regions, tags, bookmarked);
    }

    @Transactional
    public void toggleBookmark(Long userId, Long serviceId) {
        String userKey = resolveUserKey(userId);
        UserRecommendation recommendation = userRecommendationRepository
                .findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc(userKey, serviceId)
                .orElseGet(() -> createBookmarkPlaceholder(userId, userKey, serviceId));

        if (!recommendation.isBookmarked()
                && userRecommendationRepository.countByUserKeyAndIsBookmarkedTrue(userKey) >= MAX_BOOKMARKS) {
            throw new CustomException(ErrorCode.BOOKMARK_LIMIT_EXCEEDED);
        }
        recommendation.toggleBookmark();
    }

    private UserRecommendation createBookmarkPlaceholder(Long userId, String userKey, Long serviceId) {
        WelfareService service = welfareServiceRepository.findById(serviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.POLICY_NOT_FOUND));

        UserRecommendation placeholder = UserRecommendation.builder()
                .userKey(userKey)
                .service(service)
                .recommendedAt(LocalDateTime.now())
                .build();
        return userRecommendationRepository.save(placeholder);
    }

    private Set<Long> getBookmarkedServiceIds(Long userId, List<WelfareService> services) {
        if (userId == null || services.isEmpty()) {
            return Collections.emptySet();
        }
        String userKey = resolveUserKey(userId);

        List<Long> serviceIds = services.stream()
                .map(WelfareService::getId)
                .toList();

        return new HashSet<>(userRecommendationRepository.findLatestBookmarkedServiceIdsByUserKey(userKey, serviceIds));
    }

    private String resolveUserKey(Long userId) {
        return userRepository.findUserKeyById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Pageable buildPageable(Pageable pageable, String sort) {
        Sort resolvedSort = switch (normalizeSort(sort)) {
            case "VIEWS" -> Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("createdAt"));
            case "NAME" -> Sort.by(Sort.Order.asc("title"), Sort.Order.desc("createdAt"));
            default -> Sort.by(Sort.Order.desc("createdAt"));
        };

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolvedSort);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) return "LATEST";
        String upper = sort.trim().toUpperCase();
        return switch (upper) {
            case "LATEST", "VIEWS", "NAME" -> upper;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }

    private WelfareService.ServiceStatus normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String upper = status.trim().toUpperCase();
        return switch (upper) {
            case "ACTIVE" -> WelfareService.ServiceStatus.ACTIVE;
            case "UPCOMING" -> WelfareService.ServiceStatus.UPCOMING;
            case "CLOSED" -> WelfareService.ServiceStatus.CLOSED;
            case "ALL" -> null;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }

    private WelfareService.SourceType normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) return null;
        String upper = sourceType.trim().toUpperCase();
        return switch (upper) {
            case "YOUTH" -> WelfareService.SourceType.YOUTH;
            case "BOKJIRO_CENTRAL" -> WelfareService.SourceType.BOKJIRO_CENTRAL;
            case "BOKJIRO_LOCAL" -> WelfareService.SourceType.BOKJIRO_LOCAL;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }

    private String normalizeNullable(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
