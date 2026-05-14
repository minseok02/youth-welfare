package com.example.welfare.collect.repository;

import com.example.welfare.collect.normalization.CanonicalTaxonomySummarySlots;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.normalization.ServiceTaxonomyLegacySummaryBridge;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.support.CompatCategorySupport;
import com.example.welfare.policy.support.WelfareSourceTypeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DeferredNormalizedPolicySidecarCommandRepositoryImpl
        implements DeferredNormalizedPolicySidecarCommandRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public void upsertTaxonomySummary(WelfareService service, NormalizedPolicyAggregate aggregate) {
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
                ON CONFLICT (service_id) DO UPDATE SET
                    primary_source_system = EXCLUDED.primary_source_system,
                    compat_unified_category_code = EXCLUDED.compat_unified_category_code,
                    compat_unified_category_label = EXCLUDED.compat_unified_category_label,
                    youth_major_code = EXCLUDED.youth_major_code,
                    youth_major_label = EXCLUDED.youth_major_label,
                    youth_mid_code = EXCLUDED.youth_mid_code,
                    youth_mid_label = EXCLUDED.youth_mid_label,
                    gov24_service_field_code = EXCLUDED.gov24_service_field_code,
                    gov24_service_field_label = EXCLUDED.gov24_service_field_label,
                    gov24_user_type_code = EXCLUDED.gov24_user_type_code,
                    gov24_user_type_label = EXCLUDED.gov24_user_type_label,
                    gov24_benefit_type_code = EXCLUDED.gov24_benefit_type_code,
                    gov24_benefit_type_label = EXCLUDED.gov24_benefit_type_label,
                    provision_method_code = EXCLUDED.provision_method_code,
                    provision_method_label = EXCLUDED.provision_method_label,
                    authority = EXCLUDED.authority,
                    confidence = EXCLUDED.confidence
                """,
                ServiceTaxonomyLegacySummaryBridge.apply(
                        new MapSqlParameterSource()
                                .addValue("serviceId", service.getId())
                                .addValue("primarySourceSystem", WelfareSourceTypeSupport.primarySourceSystem(aggregate.core().sourceType()))
                                .addValue("compatUnifiedCategoryCode", CompatCategorySupport.compatCode(taxonomy.compatUnifiedCategory()))
                                .addValue("compatUnifiedCategoryLabel", taxonomy.compatUnifiedCategory())
                                .addValue("authority", taxonomy.authority().name())
                                .addValue("confidence", taxonomy.confidence()),
                        summarySlots
                ));
    }

    @Override
    public void replaceTaxonomySummarySlots(WelfareService service, NormalizedPolicyAggregate aggregate) {
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
                    ON CONFLICT (service_id, slot_key, slot_code, authority) DO UPDATE SET
                        code_set_key = EXCLUDED.code_set_key,
                        slot_label = EXCLUDED.slot_label,
                        source_field = EXCLUDED.source_field,
                        confidence = EXCLUDED.confidence
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

    @Override
    public void replaceTaxonomyTerms(Long serviceId,
                                     List<NormalizedPolicyAggregate.TaxonomyTerm> taxonomyTerms,
                                     List<String> refreshScopeGroups,
                                     List<String> refreshScopeSourceFields) {
        if (!refreshScopeGroups.isEmpty()) {
            jdbcTemplate.update("""
                    DELETE FROM service_taxonomy_terms
                    WHERE service_id = ?
                      AND (%s)
                    """.formatted(String.join(" OR ",
                            refreshScopeGroups.stream()
                                    .map(group -> "(term_group = ? AND source_field = ?)")
                                    .toList())),
                    buildDeleteArgs(serviceId, refreshScopeGroups, refreshScopeSourceFields));
        }

        for (NormalizedPolicyAggregate.TaxonomyTerm term : taxonomyTerms) {
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
                    ON CONFLICT (service_id, term_group, term_code, term_label, authority) DO UPDATE SET
                        code_set_key = EXCLUDED.code_set_key,
                        source_field = EXCLUDED.source_field,
                        sort_order = EXCLUDED.sort_order
                    """,
                    new MapSqlParameterSource()
                            .addValue("serviceId", serviceId)
                            .addValue("termGroup", term.termGroup())
                            .addValue("codeSetKey", term.codeSetKey())
                            .addValue("termCode", normalizeBlankCode(term.termCode()))
                            .addValue("termLabel", term.termLabel())
                            .addValue("sourceField", normalizeBlankString(term.sourceField()))
                            .addValue("authority", term.authority().name())
                            .addValue("sortOrder", term.sortOrder() == null ? 0 : term.sortOrder()));
        }
    }

    @Override
    public void upsertMergedFacts(Long serviceId, List<NormalizedPolicyAggregate.Fact> mergedFacts) {
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
                    ON CONFLICT (service_id, fact_merge_key) DO UPDATE SET
                        fact_group = EXCLUDED.fact_group,
                        fact_code_set_key = EXCLUDED.fact_code_set_key,
                        fact_code = EXCLUDED.fact_code,
                        fact_label = EXCLUDED.fact_label,
                        operator = EXCLUDED.operator,
                        value_type = EXCLUDED.value_type,
                        bool_value = EXCLUDED.bool_value,
                        int_value = EXCLUDED.int_value,
                        decimal_value = EXCLUDED.decimal_value,
                        text_value = EXCLUDED.text_value,
                        date_value = EXCLUDED.date_value,
                        range_min_int = EXCLUDED.range_min_int,
                        range_max_int = EXCLUDED.range_max_int,
                        unit = EXCLUDED.unit,
                        source_field = EXCLUDED.source_field,
                        authority = EXCLUDED.authority,
                        confidence = EXCLUDED.confidence,
                        raw_value = EXCLUDED.raw_value,
                        evidence_text = EXCLUDED.evidence_text
                    """,
                    new MapSqlParameterSource()
                            .addValue("serviceId", serviceId)
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

    @Override
    public void replaceFactsByCodeSet(Long serviceId,
                                      String factCodeSetKey,
                                      List<NormalizedPolicyAggregate.Fact> facts) {
        jdbcTemplate.update("""
                DELETE FROM service_facts
                WHERE service_id = ?
                  AND fact_code_set_key = ?
                """, serviceId, factCodeSetKey);
        upsertMergedFacts(serviceId, facts);
    }

    private Object[] buildSummarySlotDeleteArgs(Long serviceId) {
        List<Object> args = new ArrayList<>();
        args.add(serviceId);
        args.addAll(CanonicalTaxonomySummarySlots.managedSlotKeys());
        return args.toArray();
    }

    private Object[] buildDeleteArgs(Long serviceId,
                                     List<String> refreshScopeGroups,
                                     List<String> refreshScopeSourceFields) {
        List<Object> args = new ArrayList<>();
        args.add(serviceId);
        for (int i = 0; i < refreshScopeGroups.size(); i++) {
            args.add(refreshScopeGroups.get(i));
            args.add(refreshScopeSourceFields.get(i));
        }
        return args.toArray();
    }

    private String normalizeBlankCode(String value) {
        return value == null ? "" : value;
    }

    private String normalizeBlankString(String value) {
        return value == null ? "" : value;
    }
}
