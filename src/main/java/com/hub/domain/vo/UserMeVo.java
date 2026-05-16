package com.hub.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserMeVo {

    private Long id;
    private String username;
    private String avatar;
    private Integer status;
    private LocalDateTime createdAt;
}
