package com.rentmanager.modules.notification.push;

import com.rentmanager.modules.notification.push.application.PushDeviceService;
import com.rentmanager.modules.notification.push.domain.PushDevice;
import com.rentmanager.modules.notification.push.domain.PushDeviceRepository;
import com.rentmanager.modules.notification.push.domain.PushPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PushDeviceServiceTest {

    private static final String TOKEN = "ExponentPushToken[abcdefghijklmnop]";

    private PushDeviceRepository repository;
    private PushDeviceService service;

    @BeforeEach
    void setUp() {
        repository = mock(PushDeviceRepository.class);
        service = new PushDeviceService(repository);
    }

    @Test
    void registersNewDevice() {
        when(repository.findByToken(TOKEN)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PushDevice device = service.register("user_a", TOKEN, PushPlatform.ANDROID, "1.0.0");

        assertThat(device.isOwnedBy("user_a")).isTrue();
        assertThat(device.isActive()).isTrue();
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> service.register("user_a", "not-a-token", PushPlatform.IOS, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void signInByAnotherPersonMovesOwnershipAndReactivates() {
        PushDevice existing = PushDevice.register("user_a", TOKEN, PushPlatform.IOS, "1.0.0");
        existing.revoke();
        when(repository.findByToken(TOKEN)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PushDevice device = service.register("user_b", TOKEN, PushPlatform.IOS, "1.0.1");

        assertThat(device.isOwnedBy("user_b")).isTrue();
        assertThat(device.isOwnedBy("user_a")).isFalse();
        assertThat(device.isActive()).isTrue();
    }

    @Test
    void unregisterRevokesOwnDevice() {
        PushDevice existing = PushDevice.register("user_a", TOKEN, PushPlatform.IOS, null);
        when(repository.findByToken(TOKEN)).thenReturn(Optional.of(existing));

        service.unregister("user_a", TOKEN);

        ArgumentCaptor<PushDevice> captor = ArgumentCaptor.forClass(PushDevice.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void unregisterOfSomeoneElsesDeviceIsSilentNoOp() {
        PushDevice existing = PushDevice.register("user_a", TOKEN, PushPlatform.IOS, null);
        when(repository.findByToken(TOKEN)).thenReturn(Optional.of(existing));

        service.unregister("attacker", TOKEN);

        verify(repository, never()).save(any());
        assertThat(existing.isActive()).isTrue();
    }

    @Test
    void deliverableOnlyWhenActiveAndOwnedByIntendedPerson() {
        PushDevice device = PushDevice.register("user_b", TOKEN, PushPlatform.ANDROID, null);
        when(repository.findByToken(TOKEN)).thenReturn(Optional.of(device));

        assertThat(service.isDeliverable(TOKEN, "user_b")).isTrue();
        assertThat(service.isDeliverable(TOKEN, "user_a")).isFalse();

        device.revoke();
        assertThat(service.isDeliverable(TOKEN, "user_b")).isFalse();
    }
}
