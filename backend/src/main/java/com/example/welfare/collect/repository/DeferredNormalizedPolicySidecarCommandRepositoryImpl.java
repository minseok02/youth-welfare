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

    private Object[] buildSummarySlotDeleteArgs(Long serviceId) {
        List<Object> args = new ArrayList<>();
        args.add(serviceId);
        args.addAll(CanonicalTaxonomySummarySlots.managedSlotKeys());
        return args.toArray();
    }

    private String normalizeBlankCode(String value) {
        return value == null ? "" : value;
    }
}
