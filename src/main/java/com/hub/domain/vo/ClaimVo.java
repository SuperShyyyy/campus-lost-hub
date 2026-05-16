package com.hub.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClaimVo {

    private Long id;
    private Long itemId;
    private Long userId;
    private String message;
    private Integer status;
    private LocalDateTime createdAt;
}
