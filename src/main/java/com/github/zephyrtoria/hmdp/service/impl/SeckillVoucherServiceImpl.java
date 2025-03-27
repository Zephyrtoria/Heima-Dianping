package com.github.zephyrtoria.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.SeckillVoucher;
import com.github.zephyrtoria.hmdp.service.ISeckillVoucherService;
import com.github.zephyrtoria.hmdp.mapper.SeckillVoucherMapper;
import org.springframework.stereotype.Service;

/**
* @author 23240
* @description 针对表【tb_seckill_voucher(秒杀优惠券表，与优惠券是一对一关系)】的数据库操作Service实现
* @createDate 2025-03-27 13:33:15
*/
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher>
    implements ISeckillVoucherService {

}




