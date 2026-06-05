package com.rentmanager.shared.error;

public interface ErrorEventRepository {
    void save(ErrorEvent event);
}