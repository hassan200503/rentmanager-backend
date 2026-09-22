package com.rentmanager.modules.platformsettings.application.service;

import com.rentmanager.modules.platformsettings.infrastructure.persistence.PlatformBrandIconStore;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/**
 * The one icon the whole platform is branded with.
 *
 * Every surface reads it: the browser tab, the installed web app, the sidebar
 * and top bars, the sign-in and sign-up pages. The owner uploads once.
 *
 * <h2>Why the bytes are checked, not the file name</h2>
 * A browser sends whatever content type it likes and a name means nothing. The
 * file is identified by its magic bytes, and only PNG, JPEG and WEBP are
 * accepted. SVG is refused on purpose: it is a document that can carry script,
 * and this file is served from our own origin to every visitor — an uploaded
 * "icon" could otherwise run code in their browser.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformBrandIconService {

    /** Generous for an icon, small enough to serve from a row on every load. */
    static final int MAX_BYTES = 512 * 1024;

    private final PlatformBrandIconStore store;

    @Transactional(readOnly = true)
    public Optional<PlatformBrandIconStore.BrandIcon> find() {
        return store.find();
    }

    @Transactional(readOnly = true)
    public boolean exists() {
        return store.exists();
    }

    /**
     * When the current icon was stored, or empty when there is none.
     *
     * <p>Used to stamp a version onto the public logo URL so that replacing the
     * icon replaces the URL. Without it the address never changed, and no cache
     * between the database and the tab had any reason to fetch the new image.
     */
    @Transactional(readOnly = true)
    public Optional<Instant> currentVersion() {
        return store.updatedAt();
    }

    @Transactional
    public void upload(MultipartFile file, String actor) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose an image to upload", ErrorCode.VALIDATION_ERROR);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(
                    "The icon must be 512 KB or smaller", ErrorCode.VALIDATION_ERROR);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("The upload could not be read", ErrorCode.VALIDATION_ERROR);
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new BusinessException(
                    "The icon must be 512 KB or smaller", ErrorCode.VALIDATION_ERROR);
        }

        String contentType = sniff(bytes).orElseThrow(() -> new BusinessException(
                "That file is not a PNG, JPEG or WEBP image", ErrorCode.VALIDATION_ERROR));

        store.save(bytes, contentType, actor == null || actor.isBlank() ? "platform-owner" : actor);
        log.info("Platform brand icon replaced: actor={} type={} bytes={}", actor, contentType, bytes.length);
    }

    @Transactional
    public void remove(String actor) {
        store.delete();
        log.info("Platform brand icon removed: actor={}", actor);
    }

    /** The real type, from the file's own first bytes. */
    static Optional<String> sniff(byte[] b) {
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return Optional.of("image/png");
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }
}
