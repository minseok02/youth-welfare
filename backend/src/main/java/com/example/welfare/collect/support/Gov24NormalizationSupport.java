package com.example.welfare.collect.support;

import com.example.welfare.collect.dto.Gov24ServiceListDto;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
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
                null,
                item.getServiceField(),
                NormalizationKeySupport.SOURCE_FIELD_GOV24_SERVICE_FIELD,
                0);

        addTokenTerms(terms,
                NormalizationKeySupport.TERM_GROUP_GOV24_USER_TYPE_TOKEN,
                Gov24LabelTokenSupport.userTypeTokens(item.getUserType()),
                NormalizationKeySupport.SOURCE_FIELD_GOV24_USER_TYPE);
        addTokenTerms(terms,
                NormalizationKeySupport.TERM_GROUP_GOV24_BENEFIT_TYPE_TOKEN,
                Gov24LabelTokenSupport.benefitTypeTokens(item.getSupportType()),
                NormalizationKeySupport.SOURCE_FIELD_GOV24_BENEFIT_TYPE);
        return terms;
    }

    private static void addTokenTerms(List<NormalizedPolicyAggregate.TaxonomyTerm> terms,
                                      String termGroup,
                                      List<String> labels,
                                      String sourceField) {
        int sortOrder = 0;
        for (String label : labels) {
            addTerm(terms, termGroup, null, label, sourceField, sortOrder++);
        }
    }

    private static void addTerm(List<NormalizedPolicyAggregate.TaxonomyTerm> terms,
                                String termGroup,
                                String codeSetKey,
                                String rawLabel,
                                String sourceField,
                                int sortOrder) {
        String label = RawFieldValidator.normalize(rawLabel);
        if (label == null) {
            return;
        }
        terms.add(NormalizedPolicyAggregate.TaxonomyTerm.builder()
                .termGroup(termGroup)
                .codeSetKey(codeSetKey)
                .termCode(null)
                .termLabel(label)
                .sourceField(sourceField)
                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                .sortOrder(sortOrder)
                .build());
    }
}
