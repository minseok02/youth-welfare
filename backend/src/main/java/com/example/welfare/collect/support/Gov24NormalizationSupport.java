package com.example.welfare.collect.support;

import com.example.welfare.collect.dto.Gov24ServiceListDto;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.normalization.TaxonomySummarySupport;
import com.example.welfare.collect.validation.RawFieldValidator;

import java.util.ArrayList;
import java.util.List;

public final class Gov24NormalizationSupport {

    private Gov24NormalizationSupport() {
    }

    public static List<NormalizedPolicyAggregate.TaxonomyTerm> taxonomyTerms(Gov24ServiceListDto.Item item) {
        List<NormalizedPolicyAggregate.TaxonomyTerm> terms = new ArrayList<>();
        addTerm(terms,
                NormalizationKeySupport.TERM_GROUP_GOV24_SERVICE_FIELD,
                NormalizationKeySupport.TERM_GROUP_GOV24_SERVICE_FIELD,
                Gov24TaxonomyCodeSupport.serviceFieldCode(item.getServiceField()),
                item.getServiceField(),
                NormalizationKeySupport.SOURCE_FIELD_GOV24_SERVICE_FIELD,
                NormalizedPolicyAggregate.Authority.OFFICIAL,
                0);

        addTokenTerms(terms,
                NormalizationKeySupport.TERM_GROUP_GOV24_USER_TYPE_TOKEN,
                Gov24LabelTokenSupport.userTypeTokens(item.getUserType()),
                Gov24TaxonomyCodeSupport::userTypeTokenCode,
                NormalizationKeySupport.SOURCE_FIELD_GOV24_USER_TYPE);
        addTokenTerms(terms,
                NormalizationKeySupport.TERM_GROUP_GOV24_BENEFIT_TYPE_TOKEN,
                Gov24LabelTokenSupport.benefitTypeTokens(item.getSupportType()),
                Gov24TaxonomyCodeSupport::benefitTypeTokenCode,
                NormalizationKeySupport.SOURCE_FIELD_GOV24_BENEFIT_TYPE);
        addYouthBridgeTerms(terms, item);
        return terms;
    }

    private static void addTokenTerms(List<NormalizedPolicyAggregate.TaxonomyTerm> terms,
                                      String termGroup,
                                      List<String> labels,
                                      java.util.function.Function<String, String> codeResolver,
                                      String sourceField) {
        int sortOrder = 0;
        for (String label : labels) {
            addTerm(terms,
                    termGroup,
                    termGroup,
                    codeResolver.apply(label),
                    label,
                    sourceField,
                    NormalizedPolicyAggregate.Authority.OFFICIAL,
                    sortOrder++);
        }
    }

    private static void addYouthBridgeTerms(List<NormalizedPolicyAggregate.TaxonomyTerm> terms,
                                            Gov24ServiceListDto.Item item) {
        Gov24TaxonomyCodeSupport.YouthBridge bridge = Gov24TaxonomyCodeSupport.youthBridge(
                item.getServiceField(),
                item.getSupportType(),
                item.getServiceName(),
                item.getServicePurposeSummary()
        );
        addTerm(terms,
                NormalizationKeySupport.TERM_GROUP_YOUTH_MAJOR,
                NormalizationKeySupport.TERM_GROUP_YOUTH_MAJOR,
                TaxonomySummarySupport.toYouthMajorCode(bridge.youthMajorLabel()),
                bridge.youthMajorLabel(),
                NormalizationKeySupport.SOURCE_FIELD_GOV24_SERVICE_FIELD,
                NormalizedPolicyAggregate.Authority.RULE_DERIVED,
                0);
        addTerm(terms,
                NormalizationKeySupport.TERM_GROUP_YOUTH_MID,
                NormalizationKeySupport.TERM_GROUP_YOUTH_MID,
                null,
                bridge.youthMidLabel(),
                NormalizationKeySupport.SOURCE_FIELD_GOV24_SERVICE_FIELD,
                NormalizedPolicyAggregate.Authority.RULE_DERIVED,
                0);
    }

    private static void addTerm(List<NormalizedPolicyAggregate.TaxonomyTerm> terms,
                                String termGroup,
                                String codeSetKey,
                                String termCode,
                                String rawLabel,
                                String sourceField,
                                NormalizedPolicyAggregate.Authority authority,
                                int sortOrder) {
        String label = RawFieldValidator.normalize(rawLabel);
        if (label == null) {
            return;
        }
        terms.add(NormalizedPolicyAggregate.TaxonomyTerm.builder()
                .termGroup(termGroup)
                .codeSetKey(codeSetKey)
                .termCode(termCode)
                .termLabel(label)
                .sourceField(sourceField)
                .authority(authority)
                .sortOrder(sortOrder)
                .build());
    }
}
