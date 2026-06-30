package com.rentmanager.modules.lease.application.command.usecase;

import com.rentmanager.modules.lease.application.command.CreateLeaseCommand;

import java.util.UUID;

/**
 * The inbound port — the contract the outside world (REST controller,
 * event consumer, CLI) calls to create a lease.
 *
 * Why an interface?
 *  - Decouples callers from the handler implementation
 *  - Makes the use case mockable in tests without Spring context
 *  - Makes it explicit that "create a lease" is a named capability of this module
 *
 * Returns the new lease's ID — callers need it to redirect or link.
 */
public interface CreateLeaseUseCase {
    UUID handle(CreateLeaseCommand command);
}