package com.hub.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("claim_record")
public class ClaimRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;

    private Long userId;

    private String message;

    private Integer status;

    private LocalDateTime createdAt;
}
