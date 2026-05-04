package com.example.welfare.collect.normalization;

import com.example.welfare.collect.repository.DeferredNormalizedPolicySidecarReadRepository;
import com.example.welfare.policy.support.WelfareSourceTypeSupport;
import com.example.welfare.policy.support.CompatCategorySupport;
import com.example.welfare.collect.support.NormalizationKeySupport;
import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final DeferredNormalizedPolicySidecarReadRepository deferredNormalizedPolicySidecarReadRepository;
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
        NormalizedPolicyAggregate.TaxonomySummary taxonomy = aggregate.taxonomy();
        if (taxonomy == null) {
            return;
        }
        CanonicalTaxonomySummarySlots.SummarySlots summarySlots = CanonicalTaxonomySummarySlots.from(taxonomy);

        namedParameterJdbcTemplate.update("""
                INSERT INTO service_taxonomies (
                    service_id,
                    primary_source_system,
                    compat_unified_category_code,
                    compat_unified_category_label,
                    youth_major_code,
                    youth_major_label,
                    youth_mid_code,
                    youth_mid_label,
                    gov24_service_field_code,
                    gov24_service_field_label,
                    gov24_user_type_code,
                    gov24_user_type_label,
                    gov24_benefit_type_code,
                    gov24_benefit_type_label,
                    provision_method_code,
                    provision_method_label,
                    authority,
                    confidence
                ) VALUES (
                    :serviceId,
                    :primarySourceSystem,
                    :compatUnifiedCategoryCode,
                    :compatUnifiedCategoryLabel,
                    :youthMajorCode,
                    :youthMajorLabel,
                    :youthMidCode,
                    :youthMidLabel,
                    :gov24ServiceFieldCode,
                    :gov24ServiceFieldLabel,
                    :gov24UserTypeCode,
                    :gov24UserTypeLabel,
                    :gov24BenefitTypeCode,
                    :gov24BenefitTypeLabel,
                    :provisionMethodCode,
                    :provisionMethodLabel,
                    :authority,
                    :confidence
                )
                ON DUPLICATE KEY UPDATE
                    primary_source_system = VALUES(primary_source_system),
                    compat_unified_category_code = VALUES(compat_unified_category_code),
                    compat_unified_category_label = VALUES(compat_unified_category_label),
                    youth_major_code = VALUES(youth_major_code),
                    youth_major_label = VALUES(youth_major_label),
                    youth_mid_code = VALUES(youth_mid_code),
                    youth_mid_label = VALUES(youth_mid_label),
                    gov24_service_field_code = VALUES(gov24_service_field_code),
                    gov24_service_field_label = VALUES(gov24_service_field_label),
                    gov24_user_type_code = VALUES(gov24_user_type_code),
                    gov24_user_type_label = VALUES(gov24_user_type_label),
                    gov24_benefit_type_code = VALUES(gov24_benefit_type_code),
                    gov24_benefit_type_label = VALUES(gov24_benefit_type_label),
                    provision_method_code = VALUES(provision_method_code),
                    provision_method_label = VALUES(provision_method_label),
                    authority = VALUES(authority),
                    confidence = VALUES(confidence)
                """,
                ServiceTaxonomyLegacySummaryBridge.apply(
                        new MapSqlParameterSource()
                                .addValue("serviceId", service.getId())
                                .addValue("primarySourceSystem", WelfareSourceTypeSupport.primarySourceSystem(aggregate.core().sourceType()))
                                .addValue("compatUnifiedCategoryCode", toCompatUnifiedCategoryCode(taxonomy.compatUnifiedCategory()))
                                .addValue("compatUnifiedCategoryLabel", taxonomy.compatUnifiedCategory())
                                .addValue("authority", taxonomy.authority().name())
                                .addValue("confidence", taxonomy.confidence()),
                        summarySlots
                ));
    }

    private void replaceTaxonomySummarySlots(WelfareService service, NormalizedPolicyAggregate aggregate) {
        if (!summarySlotTableReady()) {
            return;
        }
        NormalizedPolicyAggregate.TaxonomySummary taxonomy = aggregate.taxonomy();
        CanonicalTaxonomySummarySlots.SummarySlots summarySlots = CanonicalTaxonomySummarySlots.from(taxonomy);

        jdbcTemplate.update("""
                DELETE FROM service_taxonomy_summary_slots
                WHERE service_id = ?
                  AND slot_key IN (%s)
                """.formatted(String.join(", ", CanonicalTaxonomySummarySlots.managedSlotKeys().stream()
                .map(slot -> "?")
                .toList())), buildSummarySlotDeleteArgs(service.getId()));

        if (taxonomy == null) {
            return;
        }

        for (CanonicalTaxonomySummarySlots.SummarySlot slot : summarySlots.presentSlots()) {
            namedParameterJdbcTemplate.update("""
                    INSERT INTO service_taxonomy_summary_slots (
                        service_id,
                        slot_key,
                        code_set_key,
                        slot_code,
                        slot_label,
                        source_field,
                        authority,
                        confidence
                    ) VALUES (
                        :serviceId,
                        :slotKey,
                        :codeSetKey,
                        :slotCode,
                        :slotLabel,
                        :sourceField,
                        :authority,
                        :confidence
                    )
                    ON DUPLICATE KEY UPDATE
                        code_set_key = VALUES(code_set_key),
                        slot_code = VALUES(slot_code),
                        slot_label = VALUES(slot_label),
                        source_field = VALUES(source_field),
                        authority = VALUES(authority),
                        confidence = VALUES(confidence)
                    """,
                    new MapSqlParameterSource()
                            .addValue("serviceId", service.getId())
                            .addValue("slotKey", slot.slotKey())
                            .addValue("codeSetKey", slot.codeSetKey())
                            .addValue("slotCode", normalizeBlankCode(slot.slotCode()))
                            .addValue("slotLabel", slot.slotLabel())
                            .addValue("sourceField", "")
                            .addValue("authority", taxonomy.authority().name())
                            .addValue("confidence", taxonomy.confidence()));
        }
    }

    private void replaceTaxonomyTerms(WelfareService service, NormalizedPolicyAggregate aggregate) {
        if (aggregate.taxonomyTerms().isEmpty()) {
            return;
        }

        List<TermRefreshScope> refreshScopes = refreshableTermScopes(aggregate);
        if (!refreshScopes.isEmpty()) {
            jdbcTemplate.update("""
                    DELETE FROM service_taxonomy_terms
                    WHERE service_id = ?
                      AND (%s)
                    """.formatted(String.join(" OR ",
                            refreshScopes.stream()
                                    .map(scope -> "(term_group = ? AND source_field = ?)")
                                    .toList())),
                    buildDeleteArgs(service.getId(), refreshScopes));
        }

        for (NormalizedPolicyAggregate.TaxonomyTerm term : aggregate.taxonomyTerms()) {
            namedParameterJdbcTemplate.update("""
                    INSERT INTO service_taxonomy_terms (
                        service_id,
                        term_group,
                        code_set_key,
                        term_code,
                        term_label,
                        source_field,
                        authority,
                        sort_order
                    ) VALUES (
                        :serviceId,
                        :termGroup,
                        :codeSetKey,
                        :termCode,
                        :termLabel,
                        :sourceField,
                        :authority,
                        :sortOrder
                    )
                    ON DUPLICATE KEY UPDATE
                        code_set_key = VALUES(code_set_key),
                        source_field = VALUES(source_field),
                        sort_order = VALUES(sort_order)
                    """,
                    new MapSqlParameterSource()
                            .addValue("serviceId", service.getId())
                            .addValue("termGroup", term.termGroup())
                            .addValue("codeSetKey", term.codeSetKey())
                            .addValue("termCode", normalizeBlankCode(term.termCode()))
                            .addValue("termLabel", term.termLabel())
                            .addValue("sourceField", normalizeBlankString(term.sourceField()))
                            .addValue("authority", term.authority().name())
                            .addValue("sortOrder", term.sortOrder() == null ? 0 : term.sortOrder()));
        }
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

        for (NormalizedPolicyAggregate.Fact fact : mergedFacts) {
            namedParameterJdbcTemplate.update("""
                    INSERT INTO service_facts (
                        service_id,
                        fact_group,
                        fact_code_set_key,
                        fact_code,
                        fact_merge_key,
                        fact_label,
                        operator,
                        value_type,
                        bool_value,
                        int_value,
                        decimal_value,
                        text_value,
                        date_value,
                        range_min_int,
                        range_max_int,
                        unit,
                        source_field,
                        authority,
                        confidence,
                        raw_value,
                        evidence_text
                    ) VALUES (
                        :serviceId,
                        :factGroup,
                        :factCodeSetKey,
                        :factCode,
                        :factMergeKey,
                        :factLabel,
                        :operator,
                        :valueType,
                        :boolValue,
                        :intValue,
                        :decimalValue,
                        :textValue,
                        :dateValue,
                        :rangeMinInt,
                        :rangeMaxInt,
                        :unit,
                        :sourceField,
                        :authority,
                        :confidence,
                        :rawValue,
                        :evidenceText
                    )
                    ON DUPLICATE KEY UPDATE
                        fact_group = VALUES(fact_group),
                        fact_code_set_key = VALUES(fact_code_set_key),
                        fact_code = VALUES(fact_code),
                        fact_label = VALUES(fact_label),
                        operator = VALUES(operator),
                        value_type = VALUES(value_type),
                        bool_value = VALUES(bool_value),
                        int_value = VALUES(int_value),
                        decimal_value = VALUES(decimal_value),
                        text_value = VALUES(text_value),
                        date_value = VALUES(date_value),
                        range_min_int = VALUES(range_min_int),
                        range_max_int = VALUES(range_max_int),
                        unit = VALUES(unit),
                        source_field = VALUES(source_field),
                        authority = VALUES(authority),
                        confidence = VALUES(confidence),
                        raw_value = VALUES(raw_value),
                        evidence_text = VALUES(evidence_text)
                    """,
                    new MapSqlParameterSource()
                            .addValue("serviceId", service.getId())
                            .addValue("factGroup", fact.factGroup())
                            .addValue("factCodeSetKey", fact.factCodeSetKey())
                            .addValue("factCode", fact.factCode())
                            .addValue("factMergeKey", fact.factMergeKey())
                            .addValue("factLabel", fact.factLabel())
                            .addValue("operator", fact.operator().name())
                            .addValue("valueType", fact.valueType().name())
                            .addValue("boolValue", fact.boolValue())
                            .addValue("intValue", fact.intValue())
                            .addValue("decimalValue", fact.decimalValue())
                            .addValue("textValue", fact.textValue())
                            .addValue("dateValue", fact.dateValue())
                            .addValue("rangeMinInt", fact.rangeMinInt())
                            .addValue("rangeMaxInt", fact.rangeMaxInt())
                            .addValue("unit", fact.unit())
                            .addValue("sourceField", normalizeBlankString(fact.sourceField()))
                            .addValue("authority", fact.authority().name())
                            .addValue("confidence", fact.confidence())
                            .addValue("rawValue", fact.rawValue())
                            .addValue("evidenceText", fact.evidenceText()));
        }
    }

    private Object[] buildDeleteArgs(Long serviceId, List<TermRefreshScope> refreshScopes) {
        List<Object> args = new ArrayList<>();
        args.add(serviceId);
        for (TermRefreshScope scope : refreshScopes) {
            args.add(scope.termGroup());
            args.add(scope.sourceField());
        }
        return args.toArray();
    }

    private Object[] buildSummarySlotDeleteArgs(Long serviceId) {
        List<Object> args = new ArrayList<>();
        args.add(serviceId);
        args.addAll(CanonicalTaxonomySummarySlots.managedSlotKeys());
        return args.toArray();
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

    private String toCompatUnifiedCategoryCode(String label) {
        return CompatCategorySupport.compatCode(label);
    }

    private String normalizeBlankCode(String value) {
        return value == null ? "" : value;
    }

    private String normalizeBlankString(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    private record TermRefreshScope(String termGroup, String sourceField) {
    }
}
