package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class DefaultPriorityWeightPolicy implements PriorityWeightPolicy {

    private static final double[] WEIGHTS = {2.0, 1.6, 1.3, 1.1, 1.0};

    @Override
    public double weightForRank(int rank) {
        if (rank < 1 || rank > WEIGHTS.length) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return WEIGHTS[rank - 1];
    }

    @Override
    public int maxRank() {
        return WEIGHTS.length;
    }
}

