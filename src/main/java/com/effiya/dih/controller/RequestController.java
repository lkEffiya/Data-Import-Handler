package com.effiya.dih.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.effiya.dih.service.RequestService;

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

    @PostMapping("/full-load")
    public ResponseEntity<String> fullLoad(){
        try {
            reqService.fullLoad();
            return ResponseEntity.ok("Full load completed successfully.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Full load failed: " + e.getMessage());
        }
    }

    @PostMapping("/incremental-load")
    public ResponseEntity<String> incrementalLoad(){
        try {
            reqService.incrementalLoad();
            return ResponseEntity.ok("Incremental load completed successfully.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Incremental load failed: " + e.getMessage());
        }
    }
}
