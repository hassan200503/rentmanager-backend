package com.rentmanager.ai.infrastructure.event;

import org.springframework.context.event.EventListener;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@EventListener
public @interface AiDomainEventListener {}