package com.rentmanager.modules.maintenance.application.attachment;

/** Private object storage for maintenance photos. Nothing it returns is public. */
public interface AttachmentStorage {

    /** Stores bytes privately under {@code folder} and returns the storage key. */
    String put(String folder, byte[] bytes, String contentType);

    /** Reads the stored bytes. Throws when the object is missing or storage is unavailable. */
    byte[] get(String storageKey);

    /** Best-effort removal. */
    void delete(String storageKey);
}
