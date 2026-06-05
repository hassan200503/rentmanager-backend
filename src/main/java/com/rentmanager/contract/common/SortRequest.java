package com.rentmanager.contract.common;

public record SortRequest(
        String field,
        SortDirection direction
) {}