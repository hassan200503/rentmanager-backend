package com.rentmanager.shared.exception;

import com.rentmanager.shared.error.ErrorTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caller mistakes are 4xx and never reach error tracking. They used to be 500
 * with an error_events row each, which any unauthenticated scanner could use
 * to fill the table (e.g. GET /api/v1/public/anything).
 */
class ClientRequestMistakeHandlingTest {

    @RestController
    static class Probe {
        @GetMapping("/probe/{id}")
        String byId(@PathVariable UUID id) {
            return id.toString();
        }

        @GetMapping("/probe")
        String withParam(@RequestParam String q) {
            return q;
        }
    }

    private ErrorTrackingService tracking;
    private GlobalExceptionHandler handler;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        tracking = mock(ErrorTrackingService.class);
        handler = new GlobalExceptionHandler(tracking);
        mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(handler).build();
    }

    @Test
    void malformedUuidIsBadRequest() throws Exception {
        mvc.perform(get("/probe/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(tracking);
    }

    @Test
    void missingParameterIsBadRequest() throws Exception {
        mvc.perform(get("/probe")).andExpect(status().isBadRequest());
        verifyNoInteractions(tracking);
    }

    @Test
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mvc.perform(delete("/probe/" + UUID.randomUUID())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(tracking);
    }

    @Test
    void unknownPathIsNotFound() {
        var response = handler.handleClientRequestMistake(
                new NoResourceFoundException(HttpMethod.GET, "api/v1/public/anything"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(tracking);
    }
}
