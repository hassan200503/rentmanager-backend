package com.rentmanager.modules.property.concurrency;

import com.rentmanager.modules.property.application.command.service.PropertyCommandServiceImpl;
import com.rentmanager.modules.property.application.command.validator.*;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PropertyConcurrencyLifecycleTest {

    private PropertyCommandServiceImpl service;

    @Mock private PropertyRepository repository;
    @Mock private PropertyMapper mapper;

    @Mock private CreatePropertyValidator createPropertyValidator;
    @Mock private UpdatePropertyValidator updatePropertyValidator;
    @Mock private ActivatePropertyValidator activatePropertyValidator;
    @Mock private MarkFullyOccupiedValidator markFullyOccupiedValidator;
    @Mock private PropertyMarkVacantValidator propertyMarkVacantValidator;

    private final UUID TENANT_ID = UUID.randomUUID();
    private final UUID PROPERTY_ID = UUID.randomUUID();

    private Property sharedProperty;

    @BeforeEach
    void setup() {

        service = new PropertyCommandServiceImpl(
                repository,
                mapper,
                createPropertyValidator,
                updatePropertyValidator,
                activatePropertyValidator,
                markFullyOccupiedValidator,
                propertyMarkVacantValidator
        );

        sharedProperty = mock(Property.class);

        when(repository.findByIdAndTenantId(any(), any()))
                .thenReturn(Optional.of(sharedProperty));

        when(repository.save(any(Property.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // SAFE DEFAULT STUBS (ONLY WHERE NEEDED IN MULTIPLE TESTS)
        lenient().when(activatePropertyValidator.validate(any(), any()))
                .thenReturn(sharedProperty);

        lenient().when(markFullyOccupiedValidator.validate(any(), any()))
                .thenReturn(sharedProperty);

        lenient().when(propertyMarkVacantValidator.validate(any(), any()))
                .thenReturn(sharedProperty);
    }

    @Test
    void shouldHandleConcurrentActivationsSafely() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Void> t1 = () -> {
            service.activateProperty(TENANT_ID, PROPERTY_ID);
            return null;
        };

        Callable<Void> t2 = () -> {
            service.activateProperty(TENANT_ID, PROPERTY_ID);
            return null;
        };

        executor.invokeAll(List.of(t1, t2));
        executor.shutdown();

        verify(activatePropertyValidator, times(2))
                .validate(TENANT_ID, PROPERTY_ID);

        verify(repository, atLeastOnce())
                .save(sharedProperty);
    }

    @Test
    void shouldHandleConcurrentOccupancyChanges() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Void> occupy = () -> {
            service.markFullyOccupied(TENANT_ID, PROPERTY_ID);
            return null;
        };

        Callable<Void> vacate = () -> {
            service.markVacant(TENANT_ID, PROPERTY_ID);
            return null;
        };

        executor.invokeAll(List.of(occupy, vacate));
        executor.shutdown();

        verify(repository, atLeastOnce())
                .save(sharedProperty);
    }

    @Test
    void shouldSimulateConcurrentUpdatesSafely() throws Exception {

        doNothing().when(updatePropertyValidator)
                .validate(any(), any(), any());

        when(repository.findByIdAndTenantId(any(), any()))
                .thenReturn(Optional.of(sharedProperty));

        when(repository.save(any(Property.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Void> task1 = () -> {
            service.updateProperty(
                    TENANT_ID,
                    PROPERTY_ID,
                    mock(UpdatePropertyRequest.class)
            );
            return null;
        };

        Callable<Void> task2 = () -> {
            service.updateProperty(
                    TENANT_ID,
                    PROPERTY_ID,
                    mock(UpdatePropertyRequest.class)
            );
            return null;
        };

        executor.invokeAll(List.of(task1, task2));
        executor.shutdown();

        verify(repository, atLeast(2))
                .save(any(Property.class));
    }
}