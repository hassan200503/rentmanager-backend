package com.rentmanager.modules.platformadmin.application.service;

import com.rentmanager.modules.platformadmin.api.dto.response.LandlordCommissionResponse;
import com.rentmanager.modules.rentledger.application.service.CommissionPolicyService;
import com.rentmanager.modules.rentledger.domain.model.CommissionPolicy;
import com.rentmanager.modules.rentledger.domain.repository.CommissionPolicyRepository;
import com.rentmanager.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformAdminCommissionServiceTest {

    private final UUID landlordId = UUID.randomUUID();

    private CommissionPolicyService commissionPolicyService;
    private CommissionPolicyRepository commissionPolicyRepository;
    private PlatformAdminCommissionService service;

    @BeforeEach
    void setUp() {
        commissionPolicyService = mock(CommissionPolicyService.class);
        commissionPolicyRepository = mock(CommissionPolicyRepository.class);
        service = new PlatformAdminCommissionService(commissionPolicyService, commissionPolicyRepository);
    }

    @Test
    void getCommission_returnsOverrideWhenActiveOverrideExists() {
        CommissionPolicy override = CommissionPolicy.rehydrate(
                UUID.randomUUID(), 1L, landlordId, new BigDecimal("3.50"),
                Instant.now(), true, "admin", Instant.now(), Instant.now());
        when(commissionPolicyRepository.findActiveByLandlordOrgId(landlordId)).thenReturn(Optional.of(override));

        LandlordCommissionResponse response = service.getCommissionFor(landlordId);

        assertThat(response.ratePercent()).isEqualByComparingTo("3.50");
        assertThat(response.source()).isEqualTo("OVERRIDE");
        verify(commissionPolicyService, never()).getActiveRate(landlordId);
    }

    @Test
    void getCommission_fallsBackToDefaultWhenNoOverride() {
        when(commissionPolicyRepository.findActiveByLandlordOrgId(landlordId)).thenReturn(Optional.empty());
        when(commissionPolicyService.getActiveRate(landlordId)).thenReturn(new BigDecimal("5.00"));

        LandlordCommissionResponse response = service.getCommissionFor(landlordId);

        assertThat(response.ratePercent()).isEqualByComparingTo("5.00");
        assertThat(response.source()).isEqualTo("DEFAULT");
    }

    @Test
    void setCommission_delegatesAndReturnsOverride() {
        CommissionPolicy created = CommissionPolicy.rehydrate(
                UUID.randomUUID(), 0L, landlordId, new BigDecimal("4.00"),
                Instant.now(), true, "actor", Instant.now(), Instant.now());
        when(commissionPolicyService.setLandlordRate(eq(landlordId), eq(new BigDecimal("4.00")), any(Instant.class), eq("actor")))
                .thenReturn(created);

        LandlordCommissionResponse response = service.setCommission(landlordId, new BigDecimal("4.00"), "actor");

        assertThat(response.source()).isEqualTo("OVERRIDE");
        assertThat(response.ratePercent()).isEqualByComparingTo("4.00");
        verify(commissionPolicyService).setLandlordRate(eq(landlordId), eq(new BigDecimal("4.00")), any(Instant.class), eq("actor"));
    }

    @Test
    void setCommission_rejectsNullRate() {
        assertThatThrownBy(() -> service.setCommission(landlordId, null, "actor"))
                .isInstanceOf(BusinessException.class);
        verify(commissionPolicyService, never()).setLandlordRate(any(), any(), any(), any());
    }

    @Test
    void setCommission_rejectsRateAbove100() {
        assertThatThrownBy(() -> service.setCommission(landlordId, new BigDecimal("100.01"), "actor"))
                .isInstanceOf(BusinessException.class);
        verify(commissionPolicyService, never()).setLandlordRate(any(), any(), any(), any());
    }

    @Test
    void setCommission_rejectsNegativeRate() {
        assertThatThrownBy(() -> service.setCommission(landlordId, new BigDecimal("-1"), "actor"))
                .isInstanceOf(BusinessException.class);
        verify(commissionPolicyService, never()).setLandlordRate(any(), any(), any(), any());
    }

    @Test
    void clearCommission_delegatesToPolicyService() {
        service.clearCommission(landlordId);
        verify(commissionPolicyService).clearLandlordRate(landlordId);
    }
    @Test
    void getPlatformDefault_returnsActiveDefault() {
        CommissionPolicy def = CommissionPolicy.rehydrate(
                UUID.randomUUID(), 1L, null, new BigDecimal("5.00"),
                Instant.now(), true, "admin", Instant.now(), Instant.now());
        when(commissionPolicyRepository.findActiveDefault()).thenReturn(Optional.of(def));

        LandlordCommissionResponse response = service.getPlatformDefault();

        assertThat(response.ratePercent()).isEqualByComparingTo("5.00");
        assertThat(response.source()).isEqualTo("DEFAULT");
        assertThat(response.landlordOrgId()).isNull();
    }

    @Test
    void getPlatformDefault_whenNoPolicy_returnsNullRate() {
        when(commissionPolicyRepository.findActiveDefault()).thenReturn(Optional.empty());

        LandlordCommissionResponse response = service.getPlatformDefault();

        assertThat(response.ratePercent()).isNull();
        assertThat(response.source()).isEqualTo("DEFAULT");
    }

    @Test
    void setPlatformDefault_delegatesToPolicyService() {
        CommissionPolicy created = CommissionPolicy.rehydrate(
                UUID.randomUUID(), 0L, null, new BigDecimal("6.00"),
                Instant.now(), true, "actor", Instant.now(), Instant.now());
        when(commissionPolicyService.setDefaultRate(eq(new BigDecimal("6.00")), any(Instant.class), eq("actor")))
                .thenReturn(created);

        LandlordCommissionResponse response = service.setPlatformDefault(new BigDecimal("6.00"), "actor");

        assertThat(response.ratePercent()).isEqualByComparingTo("6.00");
        assertThat(response.source()).isEqualTo("DEFAULT");
        verify(commissionPolicyService).setDefaultRate(eq(new BigDecimal("6.00")), any(Instant.class), eq("actor"));
    }

}
