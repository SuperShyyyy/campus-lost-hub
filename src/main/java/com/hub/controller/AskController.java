package com.hub.controller;

import com.hub.service.ConsultantBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ask")
@Validated
@RequiredArgsConstructor
public class AskController {

    private final ConsultantBizService consultantBizService;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam @NotBlank(message = "memoryId不能为空") String memoryId,
            @RequestParam @NotBlank(message = "message不能为空") String message) {
        Flux<String> result = consultantBizService.chat(memoryId, message);
        return result;
    }
}
