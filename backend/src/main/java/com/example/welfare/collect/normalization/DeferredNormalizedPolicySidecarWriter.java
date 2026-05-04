package com.example.welfare.collect.normalization;

import com.example.welfare.collect.repository.DeferredNormalizedPolicySidecarCommandRepository;
import com.example.welfare.collect.repository.DeferredNormalizedPolicySidecarReadRepository;
import com.example.welfare.collect.support.NormalizationKeySupport;
import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * sidecar 테이블이 적용된 환경에서는 canonical aggregate 를 실제 DB sidecar 로 저장한다.
 * 아직 sidecar 테이블이 없는 환경에서는 collect 경로를 깨지 않도록 안전하게 skip 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeferredNormalizedPolicySidecarWriter implements NormalizedPolicySidecarWriter {

    private final DeferredNormalizedPolicySidecarReadRepository deferredNormalizedPolicySidecarReadRepository;
    private final DeferredNormalizedPolicySidecarCommandRepository deferredNormalizedPolicySidecarCommandRepository;
    private final NormalizedFactMergeSupport normalizedFactMergeSupport;
    private volatile boolean sidecarTablesReady;
    private volatile Boolean summarySlotTableReady;

    @Override
    public void upsert(WelfareService service, NormalizedPolicyAggregate aggregate) {
        if (service == null || service.getId() == null || aggregate == null || aggregate.core() == null) {
            throw new IllegalArgumentException("service.id/aggregate/core 는 필수입니다.");
        }
        validateYouthMidAliasContract(aggregate);
        if (!sidecarTablesReady()) {
            log.debug("[DeferredNormalizedPolicySidecarWriter] sidecar tables not ready; skip serviceId={} sourceType={}",
                    service.getId(), service.getSourceType());
            return;
        }

        upsertTaxonomySummary(service, aggregate);
        replaceTaxonomySummarySlots(service, aggregate);
        replaceTaxonomyTerms(service, aggregate);
        upsertMergedFacts(service, aggregate);

        log.debug("[DeferredNormalizedPolicySidecarWriter] sidecar upsert serviceId={} sourceType={} facts={} taxonomyTerms={}",
                service.getId(),
                service.getSourceType(),
                aggregate.facts().size(),
                aggregate.taxonomyTerms().size());
    }

    private void validateYouthMidAliasContract(NormalizedPolicyAggregate aggregate) {
        boolean hasYouthMidRawAlias = aggregate.taxonomyTerms().stream()
                .anyMatch(term -> NormalizationKeySupport.TERM_GROUP_YOUTH_MID_RAW_ALIAS.equals(term.termGroup()));
        long officialYouthMidCount = aggregate.taxonomyTerms().stream()
                .filter(term -> NormalizationKeySupport.TERM_GROUP_YOUTH_MID.equals(term.termGroup()))
                .count();
        String summaryYouthMid = TaxonomySummarySupport.summaryLabel(
                aggregate.taxonomy(),
                NormalizationKeySupport.SUMMARY_KEY_YOUTH_MID
        );

        if (officialYouthMidCount != 1
                && aggregate.taxonomy() != null
                && summaryYouthMid != null) {
            throw new IllegalArgumentException("YOUTH_MID summary 는 exact official 단일 token일 때만 채울 수 있습니다.");
        }

        if (hasYouthMidRawAlias
                && aggregate.taxonomy() != null
                && summaryYouthMid != null) {
            throw new IllegalArgumentException("YOUTH_MID_RAW_ALIAS 가 있으면 taxonomy.youthMid 는 null 이어야 합니다.");
        }
    }

    private boolean sidecarTablesReady() {
        if (sidecarTablesReady) {
            return true;
        }
        sidecarTablesReady = deferredNormalizedPolicySidecarReadRepository.sidecarTablesReady();
        return sidecarTablesReady;
    }

    private boolean summarySlotTableReady() {
        if (summarySlotTableReady != null) {
            return summarySlotTableReady;
        }
        summarySlotTableReady = deferredNormalizedPolicySidecarReadRepository.summarySlotTableReady();
        return summarySlotTableReady;
    }

    private void upsertTaxonomySummary(WelfareService service, NormalizedPolicyAggregate aggregate) {
        deferredNormalizedPolicySidecarCommandRepository.upsertTaxonomySummary(service, aggregate);
    }

    private void replaceTaxonomySummarySlots(WelfareService service, NormalizedPolicyAggregate aggregate) {
        if (!summarySlotTableReady()) {
            return;
        }
        deferredNormalizedPolicySidecarCommandRepository.replaceTaxonomySummarySlots(service, aggregate);
    }

    private void replaceTaxonomyTerms(WelfareService service, NormalizedPolicyAggregate aggregate) {
        if (aggregate.taxonomyTerms().isEmpty()) {
            return;
        }

        List<TermRefreshScope> refreshScopes = refreshableTermScopes(aggregate);
        deferredNormalizedPolicySidecarCommandRepository.replaceTaxonomyTerms(
                service.getId(),
                aggregate.taxonomyTerms(),
                refreshScopes.stream().map(TermRefreshScope::termGroup).toList(),
                refreshScopes.stream().map(TermRefreshScope::sourceField).toList()
        );
    }

    private void upsertMergedFacts(WelfareService service, NormalizedPolicyAggregate aggregate) {
        if (aggregate.facts().isEmpty()) {
            return;
        }

        List<NormalizedPolicyAggregate.Fact> mergedFacts =
                normalizedFactMergeSupport.merge(
                        deferredNormalizedPolicySidecarReadRepository.findExistingFacts(service.getId()),
                        aggregate.facts()
                );
        deferredNormalizedPolicySidecarCommandRepository.upsertMergedFacts(service.getId(), mergedFacts);
    }

    private List<TermRefreshScope> refreshableTermScopes(NormalizedPolicyAggregate aggregate) {
        return aggregate.taxonomyTerms().stream()
                .flatMap(term -> refreshScopeGroups(term.termGroup()).stream()
                        .map(group -> new TermRefreshScope(group, normalizeBlankString(term.sourceField()))))
                .distinct()
                .toList();
    }

    private List<String> refreshScopeGroups(String termGroup) {
        return NormalizationKeySupport.refreshScopeGroups(termGroup);
    }

    private String normalizeBlankString(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    private record TermRefreshScope(String termGroup, String sourceField) {
    }
}
