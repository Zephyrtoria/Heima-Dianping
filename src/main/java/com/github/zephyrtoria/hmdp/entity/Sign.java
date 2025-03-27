package com.github.zephyrtoria.hmdp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * @TableName tb_sign
 */
@TableName(value = "tb_sign")
@Data
public class Sign {
    private Long id;

    private Long userId;

    private Object year;

    private Integer month;

    private Date date;

    private Integer isBackup;
}