package com.rentmanager.modules.notification.email;

import com.rentmanager.modules.integration.application.IntegrationNotConfiguredException;
import com.rentmanager.modules.integration.application.IntegrationRegistry;
import com.rentmanager.modules.integration.domain.model.IntegrationEnvironment;
import com.rentmanager.modules.integration.domain.model.ProviderCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JavaMailEmailService resolves SMTP from the IntegrationRegistry (console DB
 * with spring.mail.* env fallback), builds the sender on demand, and fails
 * closed when email is enabled but unconfigured. No socket is opened: the
 * sender is asserted directly or stubbed via a spy.
 */
class JavaMailEmailServiceTest {

    private IntegrationRegistry registry;
    private JavaMailEmailService service;

    @BeforeEach
    void setUp() {
        registry = mock(IntegrationRegistry.class);
        service = new JavaMailEmailService(registry);
    }

    private static IntegrationRegistry.ResolvedConfig consoleConfig(Map<String, String> credentials) {
        return new IntegrationRegistry.ResolvedConfig(
                ProviderCatalog.EMAIL, IntegrationEnvironment.PRODUCTION, credentials, true);
    }

    @Test
    void send_whenNothingConfigured_failsClosedWithoutSending() {
        when(registry.resolveOrNull(ProviderCatalog.EMAIL)).thenReturn(null);

        assertThatThrownBy(() -> service.send("tenant@example.com", "Receipt", "Body"))
                .isInstanceOf(IntegrationNotConfiguredException.class);
    }

    @Test
    void send_whenConfiguredWithoutHost_failsClosed() {
        when(registry.resolveOrNull(ProviderCatalog.EMAIL))
                .thenReturn(consoleConfig(Map.of("username", "user", "password", "pass")));

        assertThatThrownBy(() -> service.send("tenant@example.com", "Receipt", "Body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no host");
    }

    @Test
    void send_whenConfigured_buildsSenderAndSendsMessage() {
        when(registry.resolveOrNull(ProviderCatalog.EMAIL)).thenReturn(consoleConfig(Map.of(
                "host", "smtp.example.com",
                "port", "587",
                "username", "user",
                "password", "pass",
                "from_address", "no-reply@example.com",
                "tls", "true")));

        JavaMailSender sender = mock(JavaMailSender.class);
        JavaMailEmailService spyService = spy(service);
        doReturn(sender).when(spyService).buildMailSender(anyMap());

        spyService.send("tenant@example.com", "Receipt", "Your rent is received");

        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    void buildSender_appliesHostPortAuthAndTls() {
        JavaMailSenderImpl sender = (JavaMailSenderImpl) service.buildMailSender(Map.of(
                "host", "smtp.example.com",
                "port", "465",
                "username", "user",
                "password", "pass",
                "tls", "true"));

        assertThat(sender.getHost()).isEqualTo("smtp.example.com");
        assertThat(sender.getPort()).isEqualTo(465);
        assertThat(sender.getUsername()).isEqualTo("user");
        assertThat(sender.getPassword()).isEqualTo("pass");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("true");
    }

    @Test
    void buildSender_defaultsPortAndTlsAndSkipsAuthWhenBlank() {
        JavaMailSenderImpl sender = (JavaMailSenderImpl) service.buildMailSender(Map.of("host", "smtp.example.com"));

        assertThat(sender.getPort()).isEqualTo(587);
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.auth")).isNull();
    }
}
