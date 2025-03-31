package com.github.zephyrtoria.hmdp.entity.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisDTO {
    private LocalDateTime expireTime;
    private Object Data;
}
