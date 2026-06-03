package com.example.welfare.admin.dashboard.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class AdminDashboardUserProfileReadRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminDashboardUserProfileReadRepository(
            @Qualifier("adminDashboardReadNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminDashboardReadRows.UserProfileStandardCodeCoverageRow fetchUserProfileStandardCodeCoverage() {
        return jdbcTemplate.queryForObject("""
                with joined as (
                    select
                        u.user_key,
                        up.user_key is not null as has_profile_row,
                        nullif(btrim(u.house_tenure_code), '') as u_house_tenure_code,
                        nullif(btrim(u.housing_type_code), '') as u_housing_type_code,
                        nullif(btrim(u.basic_living_recipient_type_code), '') as u_basic_living_recipient_type_code,
                        nullif(btrim(u.disability_grade_code), '') as u_disability_grade_code,
                        nullif(btrim(up.house_tenure_code), '') as p_house_tenure_code,
                        nullif(btrim(up.housing_type_code), '') as p_housing_type_code,
                        nullif(btrim(up.basic_living_recipient_type_code), '') as p_basic_living_recipient_type_code,
                        nullif(btrim(up.disability_grade_code), '') as p_disability_grade_code
                    from users u
                    left join user_profiles up
                      on up.user_key = u.user_key
                ),
                scored as (
                    select
                        *,
                        ((u_house_tenure_code is not null)::int
                         + (u_housing_type_code is not null)::int
                         + (u_basic_living_recipient_type_code is not null)::int
                         + (u_disability_grade_code is not null)::int) as u_filled_count,
                        ((p_house_tenure_code is not null)::int
                         + (p_housing_type_code is not null)::int
                         + (p_basic_living_recipient_type_code is not null)::int
                         + (p_disability_grade_code is not null)::int) as p_filled_count,
                        (
                            (u_house_tenure_code is null and p_house_tenure_code is not null)
                            or (u_housing_type_code is null and p_housing_type_code is not null)
                            or (u_basic_living_recipient_type_code is null and p_basic_living_recipient_type_code is not null)
                            or (u_disability_grade_code is null and p_disability_grade_code is not null)
                        ) as profile_only_gap,
                        (
                            (u_house_tenure_code is not null and p_house_tenure_code is null)
                            or (u_housing_type_code is not null and p_housing_type_code is null)
                            or (u_basic_living_recipient_type_code is not null and p_basic_living_recipient_type_code is null)
                            or (u_disability_grade_code is not null and p_disability_grade_code is null)
                        ) as user_only_gap,
                        (
                            (u_house_tenure_code is not null and p_house_tenure_code is not null and u_house_tenure_code <> p_house_tenure_code)
                            or (u_housing_type_code is not null and p_housing_type_code is not null and u_housing_type_code <> p_housing_type_code)
                            or (u_basic_living_recipient_type_code is not null and p_basic_living_recipient_type_code is not null and u_basic_living_recipient_type_code <> p_basic_living_recipient_type_code)
                            or (u_disability_grade_code is not null and p_disability_grade_code is not null and u_disability_grade_code <> p_disability_grade_code)
                        ) as conflicting_value_gap
                    from joined
                )
                select
                    count(*) as total_users,
                    count(*) filter (where has_profile_row) as users_with_profile_row,
                    count(*) filter (where not has_profile_row) as users_without_profile_row,
                    count(*) filter (where u_filled_count > 0) as users_with_any_standard_code,
                    count(*) filter (where u_filled_count = 4) as users_with_all_standard_codes,
                    count(*) filter (where u_filled_count = 0) as users_missing_all_standard_codes,
                    count(*) filter (where u_house_tenure_code is not null) as users_house_tenure_code_filled,
                    count(*) filter (where u_housing_type_code is not null) as users_housing_type_code_filled,
                    count(*) filter (where u_basic_living_recipient_type_code is not null) as users_basic_living_recipient_type_code_filled,
                    count(*) filter (where u_disability_grade_code is not null) as users_disability_grade_code_filled,
                    count(*) filter (where has_profile_row and p_filled_count > 0) as profiles_with_any_standard_code,
                    count(*) filter (where has_profile_row and p_filled_count = 4) as profiles_with_all_standard_codes,
                    count(*) filter (where has_profile_row and p_filled_count = 0) as profiles_missing_all_standard_codes,
                    count(*) filter (where profile_only_gap) as profile_only_gap_rows,
                    count(*) filter (where user_only_gap) as user_only_gap_rows,
                    count(*) filter (where profile_only_gap or user_only_gap) as safe_reconcile_candidate_rows,
                    count(*) filter (where conflicting_value_gap) as conflicting_value_gap_rows
                from scored
                """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new AdminDashboardReadRows.UserProfileStandardCodeCoverageRow(
                        rs.getLong("total_users"),
                        rs.getLong("users_with_profile_row"),
                        rs.getLong("users_without_profile_row"),
                        rs.getLong("users_with_any_standard_code"),
                        rs.getLong("users_with_all_standard_codes"),
                        rs.getLong("users_missing_all_standard_codes"),
                        rs.getLong("users_house_tenure_code_filled"),
                        rs.getLong("users_housing_type_code_filled"),
                        rs.getLong("users_basic_living_recipient_type_code_filled"),
                        rs.getLong("users_disability_grade_code_filled"),
                        rs.getLong("profiles_with_any_standard_code"),
                        rs.getLong("profiles_with_all_standard_codes"),
                        rs.getLong("profiles_missing_all_standard_codes"),
                        rs.getLong("profile_only_gap_rows"),
                        rs.getLong("user_only_gap_rows"),
                        rs.getLong("safe_reconcile_candidate_rows"),
                        rs.getLong("conflicting_value_gap_rows")
                )
        );
    }
}
