package com.example.welfare.global.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 온통청년 API의 지역코드(region_code)와 시도/시군구 명칭 간 변환 유틸.
 *
 * 온통청년은 지역코드(5자리 행정구역코드)만 저장하고 sido_name/sgg_name은 NULL이다.
 * 복지로 지자체는 sido_name/sgg_name을 직접 저장하고 region_code는 NULL이다.
 *
 * 필터 쿼리에서 두 경로를 모두 처리하기 위해:
 *   - sido 필터: sido_name 매칭(복지로) OR region_code 앞 2자리 prefix 매칭(온통청년)
 *   - sgg 필터:  sgg_name 매칭(복지로) OR region_code 5자리 정확 매칭(온통청년)
 */
public final class RegionCodeUtil {

    private RegionCodeUtil() {}

    // 시도 단축명 → 행정구역 앞 2자리 코드
    private static final Map<String, String> SIDO_CODE_MAP = Map.ofEntries(
            Map.entry("서울", "11"),
            Map.entry("부산", "26"),
            Map.entry("대구", "27"),
            Map.entry("인천", "28"),
            Map.entry("광주", "29"),
            Map.entry("대전", "30"),
            Map.entry("울산", "31"),
            Map.entry("세종", "36"),
            Map.entry("경기", "41"),
            Map.entry("강원", "42"),
            Map.entry("충북", "43"),
            Map.entry("충남", "44"),
            Map.entry("전북", "45"),
            Map.entry("전남", "46"),
            Map.entry("경북", "47"),
            Map.entry("경남", "48"),
            Map.entry("제주", "50")
    );

    // 시도 전체명 → 단축명 (온통청년 host_org에서 활용)
    private static final Map<String, String> SIDO_ALIAS_MAP = Map.ofEntries(
            Map.entry("서울특별시", "서울"),
            Map.entry("부산광역시", "부산"),
            Map.entry("대구광역시", "대구"),
            Map.entry("인천광역시", "인천"),
            Map.entry("광주광역시", "광주"),
            Map.entry("대전광역시", "대전"),
            Map.entry("울산광역시", "울산"),
            Map.entry("세종특별자치시", "세종"),
            Map.entry("경기도", "경기"),
            Map.entry("강원특별자치도", "강원"),
            Map.entry("강원도", "강원"),
            Map.entry("충청북도", "충북"),
            Map.entry("충청남도", "충남"),
            Map.entry("전라북도", "전북"),
            Map.entry("전북특별자치도", "전북"),
            Map.entry("전라남도", "전남"),
            Map.entry("경상북도", "경북"),
            Map.entry("경상남도", "경남"),
            Map.entry("제주특별자치도", "제주")
    );

    private static final Map<String, String> SIDO_FULL_NAME_MAP = Map.ofEntries(
            Map.entry("서울", "서울특별시"),
            Map.entry("부산", "부산광역시"),
            Map.entry("대구", "대구광역시"),
            Map.entry("인천", "인천광역시"),
            Map.entry("광주", "광주광역시"),
            Map.entry("대전", "대전광역시"),
            Map.entry("울산", "울산광역시"),
            Map.entry("세종", "세종특별자치시"),
            Map.entry("경기", "경기도"),
            Map.entry("강원", "강원특별자치도"),
            Map.entry("충북", "충청북도"),
            Map.entry("충남", "충청남도"),
            Map.entry("전북", "전북특별자치도"),
            Map.entry("전남", "전라남도"),
            Map.entry("경북", "경상북도"),
            Map.entry("경남", "경상남도"),
            Map.entry("제주", "제주특별자치도")
    );

    // (시도 단축명 + "/" + 시군구명) → 5자리 행정구역코드
    private static final Map<String, String> SGG_CODE_MAP = Map.ofEntries(
            // 서울
            Map.entry("서울/강남구", "11680"), Map.entry("서울/강동구", "11740"),
            Map.entry("서울/강북구", "11305"), Map.entry("서울/강서구", "11500"),
            Map.entry("서울/관악구", "11620"), Map.entry("서울/광진구", "11215"),
            Map.entry("서울/구로구", "11530"), Map.entry("서울/금천구", "11545"),
            Map.entry("서울/노원구", "11350"), Map.entry("서울/도봉구", "11320"),
            Map.entry("서울/동대문구", "11230"), Map.entry("서울/동작구", "11590"),
            Map.entry("서울/마포구", "11440"), Map.entry("서울/서대문구", "11410"),
            Map.entry("서울/서초구", "11650"), Map.entry("서울/성동구", "11200"),
            Map.entry("서울/성북구", "11290"), Map.entry("서울/송파구", "11710"),
            Map.entry("서울/양천구", "11470"), Map.entry("서울/영등포구", "11560"),
            Map.entry("서울/용산구", "11170"), Map.entry("서울/은평구", "11380"),
            Map.entry("서울/종로구", "11110"), Map.entry("서울/중구", "11140"),
            Map.entry("서울/중랑구", "11260"),
            // 부산
            Map.entry("부산/강서구", "26440"), Map.entry("부산/금정구", "26410"),
            Map.entry("부산/기장군", "26710"), Map.entry("부산/남구", "26290"),
            Map.entry("부산/동구", "26170"), Map.entry("부산/동래구", "26260"),
            Map.entry("부산/부산진구", "26230"), Map.entry("부산/북구", "26320"),
            Map.entry("부산/사상구", "26530"), Map.entry("부산/사하구", "26380"),
            Map.entry("부산/서구", "26140"), Map.entry("부산/수영구", "26500"),
            Map.entry("부산/연제구", "26470"), Map.entry("부산/영도구", "26200"),
            Map.entry("부산/중구", "26110"), Map.entry("부산/해운대구", "26350"),
            // 대구
            Map.entry("대구/남구", "27200"), Map.entry("대구/달서구", "27290"),
            Map.entry("대구/달성군", "27710"), Map.entry("대구/동구", "27140"),
            Map.entry("대구/북구", "27230"), Map.entry("대구/서구", "27170"),
            Map.entry("대구/수성구", "27260"), Map.entry("대구/중구", "27110"),
            // 인천
            Map.entry("인천/강화군", "28710"), Map.entry("인천/계양구", "28245"),
            Map.entry("인천/남동구", "28200"), Map.entry("인천/미추홀구", "28177"),
            Map.entry("인천/부평구", "28237"), Map.entry("인천/서구", "28260"),
            Map.entry("인천/연수구", "28185"), Map.entry("인천/옹진군", "28720"),
            Map.entry("인천/중구", "28110"), Map.entry("인천/동구", "28140"),
            // 광주
            Map.entry("광주/광산구", "29200"), Map.entry("광주/남구", "29155"),
            Map.entry("광주/동구", "29110"), Map.entry("광주/북구", "29170"),
            Map.entry("광주/서구", "29140"),
            // 대전
            Map.entry("대전/대덕구", "30230"), Map.entry("대전/동구", "30110"),
            Map.entry("대전/서구", "30170"), Map.entry("대전/유성구", "30200"),
            Map.entry("대전/중구", "30140"),
            // 울산
            Map.entry("울산/남구", "31140"), Map.entry("울산/동구", "31170"),
            Map.entry("울산/북구", "31200"), Map.entry("울산/울주군", "31710"),
            Map.entry("울산/중구", "31110"),
            // 세종
            Map.entry("세종/세종시", "36110"),
            // 경기
            Map.entry("경기/가평군", "41820"), Map.entry("경기/고양시", "41280"),
            Map.entry("경기/과천시", "41290"), Map.entry("경기/광명시", "41210"),
            Map.entry("경기/광주시", "41610"), Map.entry("경기/구리시", "41310"),
            Map.entry("경기/군포시", "41410"), Map.entry("경기/김포시", "41570"),
            Map.entry("경기/남양주시", "41360"), Map.entry("경기/동두천시", "41250"),
            Map.entry("경기/부천시", "41190"), Map.entry("경기/성남시", "41130"),
            Map.entry("경기/수원시", "41110"), Map.entry("경기/시흥시", "41390"),
            Map.entry("경기/안산시", "41270"), Map.entry("경기/안성시", "41550"),
            Map.entry("경기/안양시", "41170"), Map.entry("경기/양주시", "41630"),
            Map.entry("경기/양평군", "41830"), Map.entry("경기/여주시", "41670"),
            Map.entry("경기/연천군", "41800"), Map.entry("경기/오산시", "41370"),
            Map.entry("경기/용인시", "41460"), Map.entry("경기/의왕시", "41430"),
            Map.entry("경기/의정부시", "41150"), Map.entry("경기/이천시", "41500"),
            Map.entry("경기/파주시", "41480"), Map.entry("경기/평택시", "41220"),
            Map.entry("경기/포천시", "41650"), Map.entry("경기/하남시", "41450"),
            Map.entry("경기/화성시", "41590"),
            // 강원
            Map.entry("강원/강릉시", "42150"), Map.entry("강원/고성군", "42820"),
            Map.entry("강원/동해시", "42170"), Map.entry("강원/삼척시", "42230"),
            Map.entry("강원/속초시", "42210"), Map.entry("강원/양구군", "42800"),
            Map.entry("강원/양양군", "42830"), Map.entry("강원/영월군", "42750"),
            Map.entry("강원/원주시", "42130"), Map.entry("강원/인제군", "42810"),
            Map.entry("강원/정선군", "42770"), Map.entry("강원/철원군", "42780"),
            Map.entry("강원/춘천시", "42110"), Map.entry("강원/태백시", "42190"),
            Map.entry("강원/평창군", "42760"), Map.entry("강원/홍천군", "42720"),
            Map.entry("강원/화천군", "42790"), Map.entry("강원/횡성군", "42730"),
            // 충북
            Map.entry("충북/괴산군", "43760"), Map.entry("충북/단양군", "43800"),
            Map.entry("충북/보은군", "43720"), Map.entry("충북/영동군", "43740"),
            Map.entry("충북/옥천군", "43730"), Map.entry("충북/음성군", "43770"),
            Map.entry("충북/제천시", "43150"), Map.entry("충북/증평군", "43745"),
            Map.entry("충북/진천군", "43750"), Map.entry("충북/청주시", "43110"),
            Map.entry("충북/충주시", "43130"),
            // 충남
            Map.entry("충남/공주시", "44150"), Map.entry("충남/금산군", "44710"),
            Map.entry("충남/논산시", "44230"), Map.entry("충남/당진시", "44270"),
            Map.entry("충남/보령시", "44180"), Map.entry("충남/부여군", "44760"),
            Map.entry("충남/서산시", "44210"), Map.entry("충남/서천군", "44770"),
            Map.entry("충남/아산시", "44200"), Map.entry("충남/예산군", "44810"),
            Map.entry("충남/천안시", "44130"), Map.entry("충남/청양군", "44790"),
            Map.entry("충남/태안군", "44825"), Map.entry("충남/홍성군", "44800"),
            Map.entry("충남/계룡시", "44250"),
            // 전북
            Map.entry("전북/고창군", "45790"), Map.entry("전북/군산시", "45130"),
            Map.entry("전북/김제시", "45210"), Map.entry("전북/남원시", "45190"),
            Map.entry("전북/무주군", "45730"), Map.entry("전북/부안군", "45800"),
            Map.entry("전북/순창군", "45770"), Map.entry("전북/완주군", "45710"),
            Map.entry("전북/익산시", "45140"), Map.entry("전북/임실군", "45750"),
            Map.entry("전북/장수군", "45740"), Map.entry("전북/전주시", "45110"),
            Map.entry("전북/정읍시", "45180"), Map.entry("전북/진안군", "45720"),
            // 전남
            Map.entry("전남/강진군", "46810"), Map.entry("전남/고흥군", "46770"),
            Map.entry("전남/곡성군", "46720"), Map.entry("전남/광양시", "46230"),
            Map.entry("전남/구례군", "46730"), Map.entry("전남/나주시", "46170"),
            Map.entry("전남/담양군", "46710"), Map.entry("전남/목포시", "46110"),
            Map.entry("전남/무안군", "46840"), Map.entry("전남/보성군", "46780"),
            Map.entry("전남/순천시", "46150"), Map.entry("전남/신안군", "46910"),
            Map.entry("전남/여수시", "46130"), Map.entry("전남/영광군", "46870"),
            Map.entry("전남/영암군", "46830"), Map.entry("전남/완도군", "46890"),
            Map.entry("전남/장성군", "46880"), Map.entry("전남/장흥군", "46800"),
            Map.entry("전남/진도군", "46900"), Map.entry("전남/함평군", "46860"),
            Map.entry("전남/해남군", "46820"), Map.entry("전남/화순군", "46790"),
            // 경북
            Map.entry("경북/경산시", "47290"), Map.entry("경북/경주시", "47130"),
            Map.entry("경북/고령군", "47830"), Map.entry("경북/구미시", "47190"),
            Map.entry("경북/김천시", "47150"), Map.entry("경북/문경시", "47280"),
            Map.entry("경북/봉화군", "47920"), Map.entry("경북/상주시", "47250"),
            Map.entry("경북/성주군", "47840"), Map.entry("경북/안동시", "47170"),
            Map.entry("경북/영덕군", "47770"), Map.entry("경북/영양군", "47760"),
            Map.entry("경북/영주시", "47210"), Map.entry("경북/영천시", "47230"),
            Map.entry("경북/예천군", "47900"), Map.entry("경북/울릉군", "47940"),
            Map.entry("경북/울진군", "47930"), Map.entry("경북/의성군", "47730"),
            Map.entry("경북/청도군", "47820"), Map.entry("경북/청송군", "47750"),
            Map.entry("경북/칠곡군", "47850"), Map.entry("경북/포항시", "47110"),
            // 경남
            Map.entry("경남/거제시", "48310"), Map.entry("경남/거창군", "48880"),
            Map.entry("경남/고성군", "48820"), Map.entry("경남/김해시", "48250"),
            Map.entry("경남/남해군", "48840"), Map.entry("경남/밀양시", "48270"),
            Map.entry("경남/사천시", "48240"), Map.entry("경남/산청군", "48860"),
            Map.entry("경남/양산시", "48330"), Map.entry("경남/의령군", "48720"),
            Map.entry("경남/진주시", "48170"), Map.entry("경남/창녕군", "48740"),
            Map.entry("경남/창원시", "48120"), Map.entry("경남/통영시", "48220"),
            Map.entry("경남/하동군", "48850"), Map.entry("경남/함안군", "48730"),
            Map.entry("경남/함양군", "48870"), Map.entry("경남/합천군", "48890"),
            // 제주
            Map.entry("제주/서귀포시", "50130"), Map.entry("제주/제주시", "50110")
    );

    // 전국에 중복되지 않는 시군구명 → 5자리 코드 (host_org 역매핑용)
    private static final Map<String, String> UNIQUE_SGG_CODE_MAP = buildUniqueSggCodeMap();
    private static final Map<String, String> UNIQUE_SGG_STEM_CODE_MAP = buildUniqueSggStemCodeMap();
    private static final Map<String, RegionName> REGION_NAME_BY_CODE = buildRegionNameByCode();

    private static Map<String, String> buildUniqueSggCodeMap() {
        Map<String, Integer> nameCount = new HashMap<>();
        for (String key : SGG_CODE_MAP.keySet()) {
            String sgg = key.split("/", 2)[1];
            nameCount.merge(sgg, 1, Integer::sum);
        }
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> e : SGG_CODE_MAP.entrySet()) {
            String sgg = e.getKey().split("/", 2)[1];
            if (nameCount.getOrDefault(sgg, 0) == 1) {
                result.put(sgg, e.getValue());
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<String, String> buildUniqueSggStemCodeMap() {
        Map<String, Integer> stemCount = new HashMap<>();
        for (String sgg : UNIQUE_SGG_CODE_MAP.keySet()) {
            String stem = stripSggSuffix(sgg);
            if (!stem.isBlank()) {
                stemCount.merge(stem, 1, Integer::sum);
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : UNIQUE_SGG_CODE_MAP.entrySet()) {
            String stem = stripSggSuffix(entry.getKey());
            if (stem.length() >= 2 && stemCount.getOrDefault(stem, 0) == 1) {
                result.put(stem, entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static String stripSggSuffix(String sgg) {
        if (sgg == null || sgg.length() < 2) {
            return "";
        }
        char last = sgg.charAt(sgg.length() - 1);
        if (last == '시' || last == '군' || last == '구') {
            return sgg.substring(0, sgg.length() - 1);
        }
        return "";
    }

    private static Map<String, RegionName> buildRegionNameByCode() {
        Map<String, RegionName> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : SGG_CODE_MAP.entrySet()) {
            String[] parts = entry.getKey().split("/", 2);
            result.put(entry.getValue(), new RegionName(
                    entry.getValue(),
                    SIDO_FULL_NAME_MAP.getOrDefault(parts[0], parts[0]),
                    parts[1]
            ));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 시도 단축명("서울", "경기" 등)을 행정구역코드 앞 2자리로 변환.
     * 온통청년 region_code LIKE '11%' 형태의 시도 필터에 사용.
     * 매핑이 없으면 null 반환 → 쿼리에서 regionCode 경로를 비활성화.
     */
    public static String getSidoCode(String sido) {
        if (sido == null || sido.isBlank()) return null;
        String normalized = normalizeSido(sido);
        return normalized != null ? SIDO_CODE_MAP.get(normalized) : null;
    }

    /**
     * 시도 전체명/단축명을 추천/검색 내부에서 공통으로 쓰는 단축명으로 정규화.
     * 예: "서울특별시" -> "서울", "경기도" -> "경기"
     */
    public static String normalizeSido(String sido) {
        if (sido == null || sido.isBlank()) {
            return null;
        }
        String trimmed = sido.trim();
        return SIDO_ALIAS_MAP.getOrDefault(trimmed, trimmed);
    }

    public static String fullSidoName(String sido) {
        String normalized = normalizeSido(sido);
        if (normalized == null) {
            return null;
        }
        return SIDO_FULL_NAME_MAP.getOrDefault(normalized, normalized);
    }

    public static RegionName getRegionName(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            return null;
        }
        return REGION_NAME_BY_CODE.get(regionCode.trim());
    }

    public static List<RegionName> inferRegionNamesFromText(String... texts) {
        String haystack = joinTexts(texts);
        if (haystack.isBlank() || haystack.contains("전국")) {
            return List.of();
        }

        Map<String, RegionName> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : SGG_CODE_MAP.entrySet()) {
            String[] parts = entry.getKey().split("/", 2);
            String shortSido = parts[0];
            String fullSido = SIDO_FULL_NAME_MAP.getOrDefault(shortSido, shortSido);
            String sgg = parts[1];
            if ((haystack.contains(fullSido) || haystack.contains(shortSido)) && haystack.contains(sgg)) {
                putRegion(result, entry.getValue());
            }
        }

        for (Map.Entry<String, String> entry : UNIQUE_SGG_CODE_MAP.entrySet()) {
            if (haystack.contains(entry.getKey())) {
                putRegion(result, entry.getValue());
            }
        }
        for (Map.Entry<String, String> entry : UNIQUE_SGG_STEM_CODE_MAP.entrySet()) {
            if (haystack.contains(entry.getKey())) {
                putRegion(result, entry.getValue());
            }
        }

        if (!result.isEmpty()) {
            return List.copyOf(result.values());
        }

        for (String shortSido : SIDO_CODE_MAP.keySet()) {
            String fullSido = SIDO_FULL_NAME_MAP.getOrDefault(shortSido, shortSido);
            if (haystack.contains(fullSido) || haystack.contains(shortSido + "시") || haystack.contains(shortSido + "도")) {
                addAllSidoRegions(result, shortSido);
            }
        }
        for (Map.Entry<String, String> alias : SIDO_ALIAS_MAP.entrySet()) {
            if (haystack.contains(alias.getKey())) {
                addAllSidoRegions(result, alias.getValue());
            }
        }

        return List.copyOf(result.values());
    }

    public static List<RegionName> inferRegionNamesFromLocalAgency(String agencyType, String agencyName) {
        if (!isLocalAgencyType(agencyType) || agencyName == null || agencyName.isBlank()) {
            return List.of();
        }

        Map<String, RegionName> result = new LinkedHashMap<>();
        String haystack = agencyName.trim();
        for (Map.Entry<String, String> alias : SIDO_ALIAS_MAP.entrySet()) {
            if (haystack.contains(alias.getKey())) {
                addAllSidoRegions(result, alias.getValue());
            }
        }
        for (String shortSido : SIDO_CODE_MAP.keySet()) {
            if (haystack.contains(shortSido)) {
                addAllSidoRegions(result, shortSido);
            }
        }
        return List.copyOf(result.values());
    }

    private static boolean isLocalAgencyType(String agencyType) {
        if (agencyType == null || agencyType.isBlank()) {
            return false;
        }
        return agencyType.contains("광역시도")
                || agencyType.contains("시군구")
                || agencyType.contains("교육청")
                || agencyType.contains("지방공기업")
                || agencyType.contains("지방출자");
    }

    private static String joinTexts(String... texts) {
        if (texts == null || texts.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                builder.append(' ').append(text.trim());
            }
        }
        return builder.toString();
    }

    private static void addAllSidoRegions(Map<String, RegionName> result, String shortSido) {
        for (Map.Entry<String, String> entry : SGG_CODE_MAP.entrySet()) {
            if (entry.getKey().startsWith(shortSido + "/")) {
                putRegion(result, entry.getValue());
            }
        }
    }

    private static void putRegion(Map<String, RegionName> result, String regionCode) {
        RegionName regionName = getRegionName(regionCode);
        if (regionName != null) {
            result.putIfAbsent(regionCode, regionName);
        }
    }

    /**
     * zipCd가 전국 수준일 때 host_org(주관기관명)에서 실제 운영 지역 코드를 추정한다.
     *
     * 추정 순서:
     * 1. 전국 고유 시군구명 포함 → 해당 5자리 코드 1개 반환 (예: "서산시청" → ["44210"])
     * 2. 시도 전체명/단축명 포함 → 해당 시도의 모든 시군구 코드 반환 (예: "충청남도청" → 충남 전체)
     * 3. 매핑 불가(중앙부처 등) → 빈 리스트 반환 → service_regions에 행 없음 → NOT EXISTS로 전국 노출
     *
     * 한계: 중앙부처가 주관하는 지역 한정 정책은 전국 노출로 처리된다.
     * 온통청년 API가 명확한 지역 구분 필드를 제공하지 않아 현재 방식으로 최선이다.
     */
    public static List<String> inferFromHostOrg(String hostOrg) {
        if (hostOrg == null || hostOrg.isBlank()) return List.of();
        String trimmed = hostOrg.trim();

        // 1. 전국 고유 시군구명 매칭 (예: "서산시청" → "44210")
        for (Map.Entry<String, String> entry : UNIQUE_SGG_CODE_MAP.entrySet()) {
            if (trimmed.contains(entry.getKey())) {
                return List.of(entry.getValue());
            }
        }

        // 2. 시도 전체명 정규화 후 매칭 (예: "충청남도청" → "충남" → 충남 전체 코드)
        String normalized = trimmed;
        for (Map.Entry<String, String> alias : SIDO_ALIAS_MAP.entrySet()) {
            if (trimmed.contains(alias.getKey())) {
                normalized = alias.getValue();
                break;
            }
        }

        // 3. 시도 단축명 직접 매칭 (예: "충남도청", "서울시")
        for (String sido : SIDO_CODE_MAP.keySet()) {
            if (normalized.contains(sido) || trimmed.contains(sido)) {
                List<String> codes = new ArrayList<>();
                for (Map.Entry<String, String> e : SGG_CODE_MAP.entrySet()) {
                    if (e.getKey().startsWith(sido + "/")) {
                        codes.add(e.getValue());
                    }
                }
                if (!codes.isEmpty()) return codes;
            }
        }

        return List.of();
    }

    /**
     * 시도 단축명 + 시군구명으로 5자리 행정구역코드를 반환.
     * 온통청년 region_code = '11680' 형태의 시군구 정확 매칭에 사용.
     * 매핑이 없으면 null 반환 → 쿼리에서 regionCode 경로를 비활성화.
     */
    public static String getRegionCode(String sido, String sgg) {
        if (sido == null || sgg == null || sido.isBlank() || sgg.isBlank()) return null;
        String normalizedSido = normalizeSido(sido);
        if (normalizedSido == null || normalizedSido.isBlank()) {
            return null;
        }
        return SGG_CODE_MAP.get(normalizedSido + "/" + sgg.trim());
    }

    public record RegionName(String regionCode, String sidoName, String sggName) {
    }
}
