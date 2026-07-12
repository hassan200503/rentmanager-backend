package com.rentmanager.modules.lease.application.dto.request;

/**
 * Application-layer DTO mirror of the domain TerminationType enum, matching
 * the existing pattern used by LeaseTypeDTO / BillingCycleDTO / LeaseStatusDTO
 * (all of which live in this package despite being used in responses too).
 *
 * NEW this session, added to support exposing lifecycle metadata on
 * LeaseDetailResponse. If a TerminationTypeDTO already exists elsewhere in
 * the codebase, discard this file and use that one instead.
 */
public enum TerminationTypeDTO {
    TENANT_REQUEST,
    LANDLORD_REQUEST,
    BREACH_OF_CONTRACT,
    NON_PAYMENT,
    MUTUAL_AGREEMENT
}