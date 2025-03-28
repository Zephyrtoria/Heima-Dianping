package com.github.zephyrtoria.hmdp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @TableName tb_sign
 */
@TableName(value = "tb_sign")
@Data
public class Sign implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;

    private Long userId;

    private Object year;

    private Integer month;

    private Date date;

    private Integer isBackup;
}