package com.rentmanager.modules.platformsettings.application.service;

import com.rentmanager.modules.platformsettings.infrastructure.persistence.PlatformBrandIconStore;
import com.rentmanager.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The one icon the platform is branded with.
 *
 * The rule under test is that the FILE decides what it is, not its name or the
 * content type the browser claims: this image is served from our own origin to
 * every visitor, so an uploaded document that can carry script must never be
 * stored as an "icon".
 */
class PlatformBrandIconServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1};

    private PlatformBrandIconStore store;
    private PlatformBrandIconService service;

    @BeforeEach
    void setUp() {
        store = mock(PlatformBrandIconStore.class);
        service = new PlatformBrandIconService(store);
    }

    @Test
    void storesTheThreeSafeImageTypes() {
        service.upload(new MockMultipartFile("file", "logo.png", "image/png", PNG), "owner@example.com");
        verify(store).save(eq(PNG), eq("image/png"), eq("owner@example.com"));

        service.upload(new MockMultipartFile("file", "logo.jpg", "image/jpeg", JPEG), "owner@example.com");
        verify(store).save(eq(JPEG), eq("image/jpeg"), any());

        service.upload(new MockMultipartFile("file", "logo.webp", "image/webp", WEBP), "owner@example.com");
        verify(store).save(eq(WEBP), eq("image/webp"), any());
    }

    @Test
    void refusesAnSvgEvenWhenItIsNamedAndTypedAsAnImage() {
        // An SVG can carry <script>. Served from our origin, an uploaded
        // "icon" would then run in every visitor's browser.
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.upload(
                new MockMultipartFile("file", "logo.svg", "image/svg+xml", svg), "owner"))
                .isInstanceOf(BusinessException.class);
        verify(store, never()).save(any(), any(), any());
    }

    @Test
    void refusesAFileWhoseBytesAreNotAnImage_whateverItClaims() {
        byte[] html = "<html><body>not an image</body></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.upload(
                new MockMultipartFile("file", "logo.png", "image/png", html), "owner"))
                .isInstanceOf(BusinessException.class);
        verify(store, never()).save(any(), any(), any());
    }

    @Test
    void refusesAnEmptyUploadAndOneTooLargeToServeOnEveryPage() {
        assertThatThrownBy(() -> service.upload(
                new MockMultipartFile("file", "logo.png", "image/png", new byte[0]), "owner"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.upload(null, "owner"))
                .isInstanceOf(BusinessException.class);

        byte[] tooBig = new byte[PlatformBrandIconService.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, tooBig, 0, PNG.length);
        assertThatThrownBy(() -> service.upload(
                new MockMultipartFile("file", "logo.png", "image/png", tooBig), "owner"))
                .isInstanceOf(BusinessException.class);

        verify(store, never()).save(any(), any(), any());
    }

    @Test
    void namesTheOwnerWhenTheActorIsUnknown() {
        service.upload(new MockMultipartFile("file", "logo.png", "image/png", PNG), "  ");
        verify(store).save(any(), any(), eq("platform-owner"));
    }

    @Test
    void sniffRecognisesOnlyTheTypesTheDatabaseAccepts() {
        assertThat(PlatformBrandIconService.sniff(PNG)).contains("image/png");
        assertThat(PlatformBrandIconService.sniff(JPEG)).contains("image/jpeg");
        assertThat(PlatformBrandIconService.sniff(WEBP)).contains("image/webp");
        assertThat(PlatformBrandIconService.sniff(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' '})).isEmpty();
        assertThat(PlatformBrandIconService.sniff(new byte[] {1, 2, 3})).isEmpty();
    }
}
