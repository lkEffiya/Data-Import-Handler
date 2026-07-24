package com.effiya.dih.controller;

import com.effiya.dih.service.RequestService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api")
public class RequestController {

    private final RequestService reqService;

    public RequestController(RequestService reqService) {
        this.reqService = reqService;
    }

    @GetMapping("/getData")
    public ResponseEntity<StreamingResponseBody> getData() {
        StreamingResponseBody responseBody = outputStream -> {
            reqService.streamAllData(outputStream);
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }
}
