package com.example.welfare.policy.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@Transactional(readOnly = true)
public class PolicySearchKeywordReadRepository {

    private static final String NORMALIZED_KEYWORD_SQL = """
            trim(
                regexp_replace(
                    lower(coalesce(keyword, '')),
                    '[^0-9a-z가-힣]+',
                    ' ',
                    'g'
                )
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PolicySearchKeywordReadRepository(
            @Qualifier("primaryNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findTrendingKeywords(LocalDateTime windowStart, int minKeywordLength, int limit) {
        return jdbcTemplate.query("""
                        with filtered as (
                            select %s as normalized_keyword,
                                   trim(keyword) as display_keyword,
                                   searched_at
                              from search_logs
                             where searched_at >= :windowStart
                               and keyword is not null
                               and trim(keyword) <> ''
                               and char_length(trim(keyword)) >= :minKeywordLength
                               and result_count > 0
                        ),
                        latest_keyword as (
                            select distinct on (normalized_keyword)
                                   normalized_keyword,
                                   display_keyword,
                                   searched_at
                              from filtered
                          order by normalized_keyword, searched_at desc, display_keyword asc
                        ),
                        aggregated as (
                            select normalized_keyword,
                                   count(*) as search_count,
                                   max(searched_at) as latest_searched_at
                              from filtered
                          group by normalized_keyword
                        )
                        select latest_keyword.display_keyword
                          from latest_keyword
                          join aggregated using (normalized_keyword)
                      order by aggregated.search_count desc,
                               aggregated.latest_searched_at desc,
                               latest_keyword.display_keyword asc
                         limit :limit
                        """.formatted(NORMALIZED_KEYWORD_SQL),
                new MapSqlParameterSource()
                        .addValue("windowStart", windowStart)
                        .addValue("minKeywordLength", minKeywordLength)
                        .addValue("limit", limit),
                (rs, rowNum) -> rs.getString("display_keyword")
        );
    }

    public List<String> findSuggestions(String normalizedInput,
                                        LocalDateTime windowStart,
                                        int minKeywordLength,
                                        int limit) {
        return jdbcTemplate.query("""
                        with matched as (
                            select %s as normalized_keyword,
                                   trim(keyword) as display_keyword,
                                   searched_at
                              from search_logs
                             where searched_at >= :windowStart
                               and keyword is not null
                               and trim(keyword) <> ''
                               and char_length(trim(keyword)) >= :minKeywordLength
                               and result_count > 0
                               and %s like :containsPattern
                        ),
                        latest_keyword as (
                            select distinct on (normalized_keyword)
                                   normalized_keyword,
                                   display_keyword,
                                   searched_at
                              from matched
                          order by normalized_keyword, searched_at desc, display_keyword asc
                        ),
                        aggregated as (
                            select normalized_keyword,
                                   count(*) as search_count,
                                   max(searched_at) as latest_searched_at,
                                   max(case when normalized_keyword = :normalizedInput then 1 else 0 end) as exact_match,
                                   max(case when normalized_keyword like :prefixPattern then 1 else 0 end) as prefix_match
                              from matched
                          group by normalized_keyword
                        )
                        select latest_keyword.display_keyword
                          from latest_keyword
                          join aggregated using (normalized_keyword)
                      order by aggregated.exact_match desc,
                               aggregated.prefix_match desc,
                               aggregated.search_count desc,
                               aggregated.latest_searched_at desc,
                               latest_keyword.display_keyword asc
                         limit :limit
                        """.formatted(NORMALIZED_KEYWORD_SQL, NORMALIZED_KEYWORD_SQL),
                new MapSqlParameterSource()
                        .addValue("windowStart", windowStart)
                        .addValue("minKeywordLength", minKeywordLength)
                        .addValue("normalizedInput", normalizedInput)
                        .addValue("prefixPattern", normalizedInput + "%")
                        .addValue("containsPattern", "%" + normalizedInput + "%")
                        .addValue("limit", limit),
                (rs, rowNum) -> rs.getString("display_keyword")
        );
    }
}
