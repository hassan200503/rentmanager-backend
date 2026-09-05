package com.rentmanager.modules.tenant.application.command.service;

import com.rentmanager.modules.audit.application.service.FinancialAuditService;
import com.rentmanager.modules.integration.application.provider.ProviderTester;
import com.rentmanager.modules.integration.bridge.LandlordDarajaVerifier;
import com.rentmanager.modules.tenant.application.dto.response.DarajaCredentialsTestResponse;
import com.rentmanager.modules.tenant.application.mapper.TenantMapper;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the landlord-facing Daraja connection test.
 *
 * <p>The behaviour worth pinning is that a REJECTED credential is not an
 * error: the request succeeded and Safaricom's "no" is the answer. If this
 * ever starts throwing, the card will render its generic "couldn't run the
 * test" branch and the landlord loses the provider's actual error code —
 * which is the only thing that tells them which of the four fields is wrong.
 */
class TestDarajaCredentialsTest {

    private TenantRepository tenantRepository;
    private TenantMapper tenantMapper;
    private FinancialAuditService financialAuditService;
    private LandlordDarajaVerifier darajaVerifier;
    private TenantCommandServiceImpl service;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        tenantMapper = mock(TenantMapper.class);
        financialAuditService = mock(FinancialAuditService.class);
        darajaVerifier = mock(LandlordDarajaVerifier.class);

        service = new TenantCommandServiceImpl(
                tenantRepository, tenantMapper, financialAuditService, darajaVerifier);

        tenantId = UUID.randomUUID();
    }

    @Test
    void reportsSuccessWhenSafaricomIssuesAToken() {
        Tenant tenant = mock(Tenant.class);
        DarajaCredentials credentials = DarajaCredentials.of("ck", "cs", "123456", "pk");

        when(tenant.getId()).thenReturn(tenantId);
        when(tenant.getDarajaCredentials()).thenReturn(credentials);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(darajaVerifier.verify("ck", "cs"))
                .thenReturn(ProviderTester.TestResult.success("OAuth token acquired"));
        when(darajaVerifier.environmentBaseUrl()).thenReturn("https://api.safaricom.co.ke");

        DarajaCredentialsTestResponse response = service.testDarajaCredentials(tenantId, tenantId);

        assertThat(response.ok()).isTrue();
        assertThat(response.message()).isEqualTo("OAuth token acquired");
        assertThat(response.error()).isNull();
        assertThat(response.environment()).isEqualTo("https://api.safaricom.co.ke");
        // The landlord must be told the shortcode/passkey were not covered,
        // or a green tick reads as "M-Pesa fully works".
        assertThat(response.scope()).contains("Shortcode and Passkey");
    }

    @Test
    void aRejectedCredentialIsAnAnswerNotAnException() {
        Tenant tenant = mock(Tenant.class);
        DarajaCredentials credentials = DarajaCredentials.of("bad", "bad", "123456", "pk");

        when(tenant.getId()).thenReturn(tenantId);
        when(tenant.getDarajaCredentials()).thenReturn(credentials);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(darajaVerifier.verify("bad", "bad")).thenReturn(
                ProviderTester.TestResult.failure(
                        "Daraja rejected the credentials (401)", "400.008.01 Invalid credentials"));
        when(darajaVerifier.environmentBaseUrl()).thenReturn("https://api.safaricom.co.ke");

        DarajaCredentialsTestResponse response = service.testDarajaCredentials(tenantId, tenantId);

        assertThat(response.ok()).isFalse();
        // Safaricom's own code survives to the UI — not reworded.
        assertThat(response.error()).isEqualTo("400.008.01 Invalid credentials");
    }

    @Test
    void doesNotCallSafaricomWhenNothingIsSaved() {
        Tenant tenant = mock(Tenant.class);

        when(tenant.getId()).thenReturn(tenantId);
        when(tenant.getDarajaCredentials()).thenReturn(DarajaCredentials.unconfigured());
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(darajaVerifier.environmentBaseUrl()).thenReturn("https://api.safaricom.co.ke");

        DarajaCredentialsTestResponse response = service.testDarajaCredentials(tenantId, tenantId);

        assertThat(response.ok()).isFalse();
        assertThat(response.message()).isEqualTo("No credentials saved");
        // Spending a Safaricom round trip to discover we sent it nothing is
        // a self-inflicted rate limit.
        verify(darajaVerifier, never()).verify(any(), any());
    }
}
