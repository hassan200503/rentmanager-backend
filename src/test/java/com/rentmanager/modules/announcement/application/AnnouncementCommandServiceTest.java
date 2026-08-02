package com.rentmanager.modules.announcement.application;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority;
import com.rentmanager.modules.announcement.domain.events.AnnouncementCreated;
import com.rentmanager.modules.announcement.domain.model.Announcement;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write side must not lose the AnnouncementCreated event: the
 * repository adapter persists and returns a REHYDRATED instance (see
 * AnnouncementRepositoryAdapter.save -> mapper.toDomain), so events pulled
 * from the returned object would be empty and the AFTER_COMMIT broadcast
 * fan-out would never create delivery rows.
 */
class AnnouncementCommandServiceTest {

    @Test
    void createdEventIsPublishedEvenWhenSaveReturnsRehydratedInstance() {
        AnnouncementRepository repository = mock(AnnouncementRepository.class);
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        AnnouncementCommandService service = new AnnouncementCommandService(repository, publisher);

        UUID tenantId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Set<AnnouncementChannel> channels = EnumSet.allOf(AnnouncementChannel.class);

        // The adapter's real shape: save() maps the aggregate to an entity,
        // persists it, and returns a freshly rehydrated aggregate with NO
        // domain events on it.
        when(repository.save(any())).thenAnswer(inv -> {
            Announcement saved = inv.getArgument(0);
            return Announcement.rehydrate(
                    saved.getId(), saved.getTenantId(), saved.getAuthorId(), saved.getMessage(),
                    saved.getPriority(), saved.getChannels(), saved.getExpiresAt(),
                    saved.getCreatedAt(), saved.getUpdatedAt(), saved.getVersion());
        });

        service.create(tenantId, authorId, "Lift maintenance Friday", AnnouncementPriority.URGENT, channels, null);

        ArgumentCaptor<List<DomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(publisher).publishAll(captor.capture());
        List<DomainEvent> published = captor.getValue();
        assertEquals(1, published.size(), "the create event must survive the save round-trip");
        assertTrue(published.get(0) instanceof AnnouncementCreated);
        AnnouncementCreated event = (AnnouncementCreated) published.get(0);
        assertEquals(tenantId, event.getTenantId());
        assertEquals(channels, event.getChannels());
    }

    @Test
    void saveIsCalledWithTheCreatedAggregate() {
        AnnouncementRepository repository = mock(AnnouncementRepository.class);
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        AnnouncementCommandService service = new AnnouncementCommandService(repository, publisher);

        UUID tenantId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.create(tenantId, UUID.randomUUID(), "Hello", AnnouncementPriority.INFO, null, null);

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(repository).save(captor.capture());
        Announcement saved = captor.getValue();
        assertEquals(tenantId, saved.getTenantId());
        assertEquals("Hello", saved.getMessage());
        assertTrue(saved.getChannels().contains(AnnouncementChannel.IN_APP), "IN_APP always on");
    }
}
