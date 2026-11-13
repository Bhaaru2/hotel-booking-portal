package com.hotelbooking.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(controllers = ErrorTestController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsStandardErrorsForValidationAndMalformedRequests() throws Exception {
        assertError(mockMvc.perform(post("/test-errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestName\":\" \"}")),
                400, "Bad Request", "/test-errors/body")
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("guestName"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value(notNullValue()));

        assertError(mockMvc.perform(get("/test-errors/constraint").param("count", "0")),
                400, "Bad Request", "/test-errors/constraint")
                .andExpect(jsonPath("$.fieldErrors[0].field").value("count"));

        assertError(mockMvc.perform(post("/test-errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestName\":\"Guest\",\"amount\":\"not-a-number\"}")),
                400, "Bad Request", "/test-errors/body")
                .andExpect(jsonPath("$.message").value(containsString("amount")))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));

        assertError(mockMvc.perform(get("/test-errors/required")),
                400, "Bad Request", "/test-errors/required")
                .andExpect(jsonPath("$.message").value(containsString("email")))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));

        assertError(mockMvc.perform(get("/test-errors/date").param("checkIn", "not-a-date")),
                400, "Bad Request", "/test-errors/date")
                .andExpect(jsonPath("$.message").value(containsString("checkIn")))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("checkIn"));
    }

    @Test
    void mapsCustomAndUnknownResourceErrorsToTheSameShape() throws Exception {
        assertError(mockMvc.perform(get("/test-errors/not-found")),
                404, "Not Found", "/test-errors/not-found")
                .andExpect(jsonPath("$.message").value("Hotel was not found."));

        assertError(mockMvc.perform(get("/test-errors/bad-request")),
                400, "Bad Request", "/test-errors/bad-request");

        assertError(mockMvc.perform(get("/test-errors/conflict")),
                409, "Conflict", "/test-errors/conflict");

        assertError(mockMvc.perform(get("/test-errors/unknown-url")),
                404, "Not Found", "/test-errors/unknown-url")
                .andExpect(jsonPath("$.message").value("The requested resource was not found."));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        assertError(mockMvc.perform(get("/test-errors/unexpected")),
                500, "Internal Server Error", "/test-errors/unexpected")
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.message", not(containsString("private details"))));
    }

    private ResultActions assertError(ResultActions result, int status, String error, String path)
            throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern("^[0-9]{4}-.*Z$")))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(notNullValue()))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
