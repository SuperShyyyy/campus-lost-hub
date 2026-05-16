package com.hub.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ItemVo {

    private Long id;
    private Long userId;
    private Integer type;
    private String title;
    private String description;
    private String location;
    private Integer status;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
