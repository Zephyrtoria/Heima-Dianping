package com.github.zephyrtoria.hmdp.service;

import com.github.zephyrtoria.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.github.zephyrtoria.hmdp.entity.result.Result;

/**
* @author 23240
* @description 针对表【tb_voucher_order】的数据库操作Service
* @createDate 2025-03-27 13:33:15
*/
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);
}
