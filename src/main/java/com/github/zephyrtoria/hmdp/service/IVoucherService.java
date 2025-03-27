package com.github.zephyrtoria.hmdp.service;

import com.github.zephyrtoria.hmdp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;
import com.github.zephyrtoria.hmdp.entity.result.Result;

/**
* @author 23240
* @description 针对表【tb_voucher】的数据库操作Service
* @createDate 2025-03-27 13:33:15
*/
public interface IVoucherService extends IService<Voucher> {
    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
