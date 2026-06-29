package com.dbfleetops.common.error;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@RestController
public class TestExceptionController {

    @PostMapping("/test/databases")
    public void createDatabase(
            @Valid
            @RequestBody
            TestDatabaseRequest request
    ) {
    }

    @GetMapping("/test/health")
    public String getHealth() {
        return "UP";
    }

    @GetMapping("/test/unexpected-error")
    public void throwUnexpectedException() {
        throw new IllegalStateException(
                "Sensitive internal exception"
        );
    }

    public record TestDatabaseRequest(

            @NotBlank
            String name,

            @Min(1)
            @Max(65535)
            int port

    ) {
    }
}