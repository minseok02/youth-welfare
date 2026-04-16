package com.example.welfare.user.service;

public interface PriorityWeightPolicy {

    double weightForRank(int rank);

    int maxRank();
}

