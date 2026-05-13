package com.example.welfare.collect.service;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;

import java.time.LocalDate;

/**
 * detail payload 를 detail row / welfare_service fallback 으로 반영하는 책임만 담당한다.
 */
final class BokjiroDetailPersistenceSupport {

    WelfareServiceDetail mergeDetail(WelfareService service,
                                     WelfareServiceDetail existing,
                                     NormalizedPolicyAggregate aggregate) {
        NormalizedPolicyAggregate.Detail detail = aggregate.detail();
        return WelfareServiceDetail.builder()
                .id(existing == null ? null : existing.getId())
                .service(service)
                .targetDetail(detail.targetDetail())
                .supportDetail(detail.supportDetail())
                .applyMethodDetail(detail.applyMethodDetail())
                .selectionCriteria(detail.selectionCriteria())
                .contactList(toJsonArray(detail.contactText()))
                .supportCycle(detail.supportCycle())
                .provisionType(detail.provisionType())
                .homepageUrl(detail.onlineApplyUrl())
                .relatedLaw(detail.legalBasisText())
                .formFiles(detail.requiredDocuments())
                .build();
    }

    void applyFallbacksToService(WelfareService service, NormalizedPolicyAggregate aggregate) {
        Integer minAge = findAgeMin(aggregate);
        Integer maxAge = findAgeMax(aggregate);
        LocalDate applyEndDate = findApplyEndDate(aggregate);
        NormalizedPolicyAggregate.Detail detail = aggregate.detail();
        NormalizedPolicyAggregate.Core core = aggregate.core();
        String coreDetailUrl = core != null ? RawFieldValidator.normalize(core.detailUrl()) : null;
        service.applyDetailFallbacks(
                RawFieldValidator.normalize(detail.supportDetail()),
                RawFieldValidator.normalize(detail.applyMethodDetail()),
                minAge,
                maxAge,
                applyEndDate,
                inferOnlineApply(service.getDetailUrl() != null ? service.getDetailUrl() : coreDetailUrl,
                        detail.applyMethodDetail(), detail.supportDetail()),
                coreDetailUrl
        );
    }

    private Integer findAgeMin(NormalizedPolicyAggregate aggregate) {
        return aggregate.facts().stream()
                .filter(fact -> "AGE".equals(fact.factGroup()))
                .map(NormalizedPolicyAggregate.Fact::rangeMinInt)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private Integer findAgeMax(NormalizedPolicyAggregate aggregate) {
        return aggregate.facts().stream()
                .filter(fact -> "AGE".equals(fact.factGroup()))
                .map(NormalizedPolicyAggregate.Fact::rangeMaxInt)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private LocalDate findApplyEndDate(NormalizedPolicyAggregate aggregate) {
        return aggregate.facts().stream()
                .filter(fact -> "APPLY_END_DATE".equals(fact.factGroup()))
                .map(NormalizedPolicyAggregate.Fact::dateValue)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private Boolean inferOnlineApply(String detailUrl, String... texts) {
        if (RawFieldValidator.normalize(detailUrl) != null) {
            return true;
        }
        if (texts == null) {
            return null;
        }
        for (String text : texts) {
            String normalized = RawFieldValidator.normalize(text);
            if (normalized == null) {
                continue;
            }
            if (normalized.contains("온라인")
                    || normalized.contains("인터넷")
                    || normalized.contains("홈페이지")
                    || normalized.contains("모바일")
                    || normalized.contains("누리집")) {
                return true;
            }
        }
        return null;
    }

    private String toJsonArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String escaped = raw
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "[\"" + escaped + "\"]";
    }
}
