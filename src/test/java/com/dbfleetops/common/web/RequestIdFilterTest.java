package com.dbfleetops.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
    }

    @Test
    void reusesValidIncomingRequestId() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                RequestIdFilter.REQUEST_ID_HEADER,
                "client-request-001"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                new MockFilterChain()
        );

        assertThat(
                response.getHeader(
                        RequestIdFilter.REQUEST_ID_HEADER
                )
        ).isEqualTo("client-request-001");

        assertThat(
                request.getAttribute(
                        RequestIdFilter.REQUEST_ID_ATTRIBUTE
                )
        ).isEqualTo("client-request-001");
    }

    @Test
    void generatesRequestIdWhenHeaderIsMissing()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                new MockFilterChain()
        );

        String generatedRequestId =
                response.getHeader(
                        RequestIdFilter.REQUEST_ID_HEADER
                );

        assertThat(generatedRequestId)
                .isNotBlank();

        assertThat(
                request.getAttribute(
                        RequestIdFilter.REQUEST_ID_ATTRIBUTE
                )
        ).isEqualTo(generatedRequestId);
    }

    @Test
    void replacesInvalidIncomingRequestId()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                RequestIdFilter.REQUEST_ID_HEADER,
                "invalid request id with spaces"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                new MockFilterChain()
        );

        String generatedRequestId =
                response.getHeader(
                        RequestIdFilter.REQUEST_ID_HEADER
                );

        assertThat(generatedRequestId)
                .isNotBlank()
                .isNotEqualTo(
                        "invalid request id with spaces"
                );
    }
}