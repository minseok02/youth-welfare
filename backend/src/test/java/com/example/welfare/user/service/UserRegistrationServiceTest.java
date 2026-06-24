package com.example.welfare.user.service;

import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.entity.PriorityOption;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserPriority;
import com.example.welfare.user.repository.UserMetadataCommandRepository;
import com.example.welfare.user.repository.UserRegistrationCommandRepository;
import com.example.welfare.user.util.UserEmailShadowValue;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRegistrationCommandRepository userRegistrationCommandRepository;
    @Mock
    private UserMetadataCommandRepository userMetadataCommandRepository;
    @Mock
    private PriorityOptionReadService priorityOptionReadService;
    @Mock
    private PriorityWeightPolicy priorityWeightPolicy;
    @Mock
    private UserCoreSyncService userCoreSyncService;
    @Mock
    private UserAccountOriginResolver userAccountOriginResolver;
    @Mock
    private UserProfileStandardCodeValidator userProfileStandardCodeValidator;
    @Mock
    private UserKeyLookupService userKeyLookupService;
    @Mock
    private UserConsentService userConsentService;

    @InjectMocks
    private UserRegistrationService userRegistrationService;

    @Test
    @DisplayName("회원가입 저장은 user 생성 후 core sync까지 위임한다")
    void registerSavesAndSyncsUser() {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", "user@example.com");
        ReflectionTestUtils.setField(request, "name", "홍길동");
        ReflectionTestUtils.setField(request, "birthDate", java.time.LocalDate.of(2000, 1, 10));
        ReflectionTestUtils.setField(request, "sido", "부산광역시");
        ReflectionTestUtils.setField(request, "sgg", "해운대구");
        ReflectionTestUtils.setField(request, "houseTenureCode", "3");
        ReflectionTestUtils.setField(request, "housingTypeCode", "4");
        ReflectionTestUtils.setField(request, "basicLivingRecipientTypeCode", "1");
        ReflectionTestUtils.setField(request, "disabilityGradeCode", "011");
        ReflectionTestUtils.setField(request, "optionalProfileConsentAgreed", true);
        ReflectionTestUtils.setField(request, "sensitiveInfoConsentAgreed", true);
        when(userAccountOriginResolver.resolve("user@example.com")).thenReturn(User.AccountOrigin.REAL_USER);
        when(userKeyLookupService.findRequired(null)).thenReturn("user-key-1");

        userRegistrationService.register(request, "encoded-password");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userProfileStandardCodeValidator).validateProfileCodes("3", "4", "1", "011");
        verify(userRegistrationCommandRepository).save(userCaptor.capture());
        verify(userConsentService).recordSignupConsents("user-key-1", request);
        verify(userCoreSyncService).syncFromUser(
                eq(userCaptor.getValue()),
                argThat(pii -> pii != null
                        && "user@example.com".equals(pii.email())
                        && "홍길동".equals(pii.name())
                        && java.time.LocalDate.of(2000, 1, 10).equals(pii.birthDate()))
        );
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(userCaptor.getValue().getName()).isNull();
        assertThat(userCaptor.getValue().getSido()).isEqualTo("부산광역시");
        assertThat(userCaptor.getValue().getSgg()).isEqualTo("해운대구");
        assertThat(userCaptor.getValue().getRegionCode()).isEqualTo("26350");
        assertThat(userCaptor.getValue().getHouseTenureCode()).isEqualTo("3");
        assertThat(userCaptor.getValue().getHousingTypeCode()).isEqualTo("4");
        assertThat(userCaptor.getValue().getProfileCompleteness()).isEqualTo(50);
    }

    @Test
    @DisplayName("회원가입 우선순위는 가입 트랜잭션에서 user_priorities와 관심분야로 저장한다")
    void registerStoresSignupPriorities() {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", "user@example.com");
        ReflectionTestUtils.setField(request, "name", "홍길동");
        ReflectionTestUtils.setField(request, "birthDate", java.time.LocalDate.of(2000, 1, 10));
        ReflectionTestUtils.setField(request, "optionalProfileConsentAgreed", true);
        ReflectionTestUtils.setField(request, "priorityCodes", List.of("HOUSING", "JOB"));

        PriorityOption housing = mock(PriorityOption.class);
        PriorityOption job = mock(PriorityOption.class);
        when(userAccountOriginResolver.resolve("user@example.com")).thenReturn(User.AccountOrigin.REAL_USER);
        when(priorityWeightPolicy.maxRank()).thenReturn(5);
        when(priorityWeightPolicy.weightForRank(1)).thenReturn(2.0);
        when(priorityWeightPolicy.weightForRank(2)).thenReturn(1.6);
        when(priorityOptionReadService.requireByCode("HOUSING")).thenReturn(housing);
        when(priorityOptionReadService.requireByCode("JOB")).thenReturn(job);
        when(userKeyLookupService.findRequired(1L)).thenReturn("user-key-1");
        org.mockito.Mockito.doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        }).when(userRegistrationCommandRepository).save(org.mockito.ArgumentMatchers.any(User.class));

        userRegistrationService.register(request, "encoded-password");

        ArgumentCaptor<List<UserPriority>> priorityCaptor = ArgumentCaptor.forClass(List.class);
        verify(userMetadataCommandRepository).replacePriorities(eq("user-key-1"), priorityCaptor.capture());
        verify(userMetadataCommandRepository).replaceAttributes(
                eq(1L),
                eq("user-key-1"),
                eq(UserAttribute.AttrType.INTEREST_FIELD.name()),
                eq(List.of("주거", "취업"))
        );
        assertThat(priorityCaptor.getValue())
                .extracting(UserPriority::getUserId, UserPriority::getUserKey, UserPriority::getPriorityRank, UserPriority::getWeight)
                .containsExactly(
                        Tuple.tuple(1L, "user-key-1", 1, 2.0),
                        Tuple.tuple(1L, "user-key-1", 2, 1.6)
                );
    }

    @Test
    @DisplayName("실사용 도메인 이메일은 users 평문 대신 shadow 값으로 저장한다")
    void registerStoresShadowValueForNonTestDomainEmail() {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", "real.user@private-domain.com");
        when(userAccountOriginResolver.resolve("real.user@private-domain.com")).thenReturn(User.AccountOrigin.REAL_USER);
        when(userKeyLookupService.findRequired(null)).thenReturn("user-key-1");

        userRegistrationService.register(request, "encoded-password");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userProfileStandardCodeValidator).validateProfileCodes(null, null, null, null);
        verify(userRegistrationCommandRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail())
                .isEqualTo(UserEmailShadowValue.from("real.user@private-domain.com"));
    }
}
