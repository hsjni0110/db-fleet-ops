package com.dbfleetops.common.error;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dbfleetops.common.web.RequestIdFilter;

@WebMvcTest(
        controllers = TestExceptionController.class
)
@Import({
        GlobalExceptionHandler.class,
        RequestIdFilter.class
})
class GlobalWebExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsProblemDetailForValidationFailure()
            throws Exception {

        mockMvc.perform(
                        post("/test/databases")
                                .header(
                                        "X-Request-ID",
                                        "validation-test-001"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "name": "",
                                          "port": 70000
                                        }
                                        """)
                )
                .andDo(print())
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        header().string(
                                "X-Request-ID",
                                "validation-test-001"
                        )
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "https://db-fleetops.dev/problems/validation-failed"
                                )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value("Validation failed")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "One or more request fields are invalid."
                                )
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value(
                                        "DBOPS-COMMON-40002"
                                )
                )
                .andExpect(
                        jsonPath("$.requestId")
                                .value(
                                        "validation-test-001"
                                )
                )
                .andExpect(
                        jsonPath("$.timestamp")
                                .value(
                                        not(blankOrNullString())
                                )
                )
                .andExpect(
                        jsonPath("$.errors")
                                .isArray()
                )
                .andExpect(
                        jsonPath(
                                "$.errors",
                                hasSize(2)
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.errors[*].field",
                                containsInAnyOrder(
                                        "name",
                                        "port"
                                )
                        )
                );
    }

    @Test
    void returnsProblemDetailForUnsupportedMethod()
            throws Exception {

        mockMvc.perform(
                        post("/test/health")
                                .header(
                                        "X-Request-ID",
                                        "method-test-001"
                                )
                )
                .andDo(print())
                .andExpect(
                        status().isMethodNotAllowed()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        header().string(
                                "X-Request-ID",
                                "method-test-001"
                        )
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "https://db-fleetops.dev/problems/method-not-allowed"
                                )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value("Method not allowed")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(405)
                )
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The HTTP method is not supported for this resource."
                                )
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value(
                                        "DBOPS-COMMON-40501"
                                )
                )
                .andExpect(
                        jsonPath("$.requestId")
                                .value(
                                        "method-test-001"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.allowedMethods",
                                hasItem("GET")
                        )
                );
    }

    @Test
    void returnsProblemDetailForUnexpectedException()
            throws Exception {

        mockMvc.perform(
                        get("/test/unexpected-error")
                                .header(
                                        "X-Request-ID",
                                        "server-error-test-001"
                                )
                )
                .andDo(print())
                .andExpect(
                        status().isInternalServerError()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        header().string(
                                "X-Request-ID",
                                "server-error-test-001"
                        )
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "https://db-fleetops.dev/problems/internal-server-error"
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
                        jsonPath("$.errorCode")
                                .value(
                                        "DBOPS-COMMON-50001"
                                )
                )
                .andExpect(
                        jsonPath("$.requestId")
                                .value(
                                        "server-error-test-001"
                                )
                )
                .andExpect(
                        jsonPath("$.timestamp")
                                .value(
                                        not(blankOrNullString())
                                )
                );
    }
}