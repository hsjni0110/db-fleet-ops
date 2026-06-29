package com.dbfleetops.common.error;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet
        .request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet
        .result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet
        .result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet
        .result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet
        .result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
        .WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbfleetops.common.web.RequestIdFilter;

@WebMvcTest(
        controllers =
                GlobalExceptionHandlerTest.TestController.class
)
@Import({
        GlobalExceptionHandler.class,
        RequestIdFilter.class
})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsProblemDetailForUnexpectedException()
            throws Exception {

        mockMvc.perform(
                        get("/test/unexpected-error")
                                .header(
                                        "X-Request-ID",
                                        "test-request-001"
                                )
                )
                .andExpect(
                        status().isInternalServerError()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                "application/problem+json"
                        )
                )
                .andExpect(
                        header().string(
                                "X-Request-ID",
                                "test-request-001"
                        )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Internal server error"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(500)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "An unexpected server error occurred."
                                )
                )
                .andExpect(
                        jsonPath("$.instance")
                                .value(
                                        "/test/unexpected-error"
                                )
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value(
                                        "DBOPS-COMMON-50001"
                                )
                )
                .andExpect(
                        jsonPath("$.requestId")
                                .value(
                                        "test-request-001"
                                )
                )
                .andExpect(
                        jsonPath("$.timestamp")
                                .value(
                                        not(blankOrNullString())
                                )
                );
    }

    @RestController
    static class TestController {

        @GetMapping("/test/unexpected-error")
        void throwUnexpectedException() {
            throw new IllegalStateException(
                    "Sensitive internal exception message"
            );
        }
    }
}