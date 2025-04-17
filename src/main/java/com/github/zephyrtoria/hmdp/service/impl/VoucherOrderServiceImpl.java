package com.github.zephyrtoria.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.SeckillVoucher;
import com.github.zephyrtoria.hmdp.entity.VoucherOrder;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.mapper.SeckillVoucherMapper;
import com.github.zephyrtoria.hmdp.service.ISeckillVoucherService;
import com.github.zephyrtoria.hmdp.service.IVoucherOrderService;
import com.github.zephyrtoria.hmdp.mapper.VoucherOrderMapper;
import com.github.zephyrtoria.hmdp.utils.RedisIdWorker;
import com.github.zephyrtoria.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
* @author 23240
* @description 针对表【tb_voucher_order】的数据库操作Service实现
* @createDate 2025-03-27 13:33:15
*/
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
    implements IVoucherOrderService {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询优惠券
        SeckillVoucher findVoucher = seckillVoucherService.getById(voucherId);
        if (findVoucher == null) {
            return Result.fail("秒杀券不存在");
        }

        // 2. 判断秒杀是否开始
        LocalDateTime now = LocalDateTime.now();
        if (findVoucher.getBeginTime().isAfter(now)) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }

        // 3. 判断秒杀是否结束
        if (findVoucher.getEndTime().isBefore(now)) {
            // 已经结束
            return Result.fail("秒杀已经结束！");
        }

        // 4. 判断库存是否充足
        if (findVoucher.getStock() < 1) {
            // 库存不够
            return Result.fail("库存不足！");
        }

        // 5. 扣减库存
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .update();
        if (!success) {
            // 扣减失败
            return Result.fail("库存不足！");
        }

        // 6. 创建订单
        VoucherOrder order = new VoucherOrder();
        order.setId(redisIdWorker.nextId("order"));
        order.setVoucherId(voucherId);
        order.setUserId(UserHolder.getUser().getId());
        save(order);

        // 7. 返回订单id
        return Result.ok(order.getId());
    }
}




