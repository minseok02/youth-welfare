package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminUserProfileStandardCodeCoverageResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import com.example.welfare.admin.dashboard.repository.AdminDashboardUserProfileReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardUserProfileService {

    private final AdminDashboardUserProfileReadRepository adminDashboardUserProfileReadRepository;

    public AdminUserProfileStandardCodeCoverageResponse getUserProfileStandardCodeCoverage() {
        AdminDashboardReadRows.UserProfileStandardCodeCoverageRow row =
                adminDashboardUserProfileReadRepository.fetchUserProfileStandardCodeCoverage();
        return new AdminUserProfileStandardCodeCoverageResponse(
                LocalDateTime.now(),
                row.totalUsers(),
                row.usersWithProfileRow(),
                row.usersWithoutProfileRow(),
                row.usersWithAnyStandardCode(),
                row.usersWithAllStandardCodes(),
                row.usersMissingAllStandardCodes(),
                row.usersHouseTenureCodeFilled(),
                row.usersHousingTypeCodeFilled(),
                row.usersBasicLivingRecipientTypeCodeFilled(),
                row.usersDisabilityGradeCodeFilled(),
                row.profilesWithAnyStandardCode(),
                row.profilesWithAllStandardCodes(),
                row.profilesMissingAllStandardCodes(),
                row.profileOnlyGapRows(),
                row.userOnlyGapRows(),
                row.safeReconcileCandidateRows(),
                row.conflictingValueGapRows()
        );
    }
}
