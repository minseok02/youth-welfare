package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AwsOpsMonitorPolicyContractTest {

    private static final Path POLICY = Path.of("../deploy/ops/aws-ops-monitor-role-policy.json");

    @Test
    @DisplayName("ops monitor IAM policy는 운영 RDS metadata 확인용 읽기 권한을 포함한다")
    void policyAllowsReadingProductionRdsMetadata() throws IOException {
        String policy = Files.readString(POLICY);

        assertThat(policy)
                .contains("ReadYouthWelfareRdsMetadata")
                .contains("rds:DescribeDBInstances")
                .contains("rds:DescribeDBInstanceAutomatedBackups")
                .contains("rds:DescribeDBSnapshots")
                .contains("\"Resource\": \"*\"");
    }
}
