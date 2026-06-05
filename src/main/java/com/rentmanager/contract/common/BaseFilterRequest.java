package com.rentmanager.contract.common;

public record BaseFilterRequest(
        int page,
        int size,
        SortRequest sort
) {}