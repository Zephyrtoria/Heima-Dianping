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
import com.github.zephyrtoria.hmdp.utils.SimpleRedisLock;
import com.github.zephyrtoria.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.github.zephyrtoria.hmdp.consts.OrderConstants.*;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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

        Long userId = UserHolder.getUser().getId();
        // 用锁包裹事务，实现先提交事务再解锁的步骤
        // synchronized (userId.toString().intern())

        // 现在使用分布式锁来实现
        // 要注意名称的限定，要满足可以实现对于单个用户的锁，同时不影响其他用户，那么就必须要有一个唯一的标识符来区分 - userId
        SimpleRedisLock lock = new SimpleRedisLock(REDIS_LOCK_PREFIX + userId, stringRedisTemplate);
        boolean isLock = lock.tryLock(REDIS_LOCK_TTL);
        // 判断锁获取是否成功
        if (!isLock) {
            // 获取失败，返回错误信息或重试（根据业务来定）
            // 现在要求这个用户不能重复下单，所以不应该重试，直接返回错误信息
            return Result.fail("不能重复购买！");
        }

        try {
            // Spring的事务注解依靠代理对象实现，但是此时方法的调用是通过this调用的，并非代理对象
            // 所以会导致事务失效
            // return this.createVoucher(voucherId);
            // 获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucher(voucherId);
        } finally {
            lock.unlock();
        }
    }

    // synchronized 加在方法上 - 对于当前（this）生效
    // 事务注解
    @Override
    @Transactional
    public Result createVoucher(Long voucherId) {
        // 5. 一人一单
        Long userId = UserHolder.getUser().getId();
        // 换成对于id来加锁
        // 如果只是使用 userId.toString()，阅读源码可知，每次都会创建一个新的对象，最终比较的还是对象
        // 加入intern()来只比较字符串的值
        // synchronized (userId.toString().intern()) {
        // 5.1 查询订单
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2 判断该用户是否购买了该优惠券
        if (count > 0) {
            // 已经购买过了
            return Result.fail("不可重复购买！");
        }

        // 6. 扣减库存
        // 乐观锁实现
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                // .eq("stock", findVoucher.getStock())
                .update();
        if (!success) {
            // 扣减失败
            return Result.fail("库存不足！");
        }

        // 7. 创建订单
        VoucherOrder order = new VoucherOrder();
        order.setId(redisIdWorker.nextId(REDIS_NEXT_ID_PREFIX));
        order.setVoucherId(voucherId);
        order.setUserId(userId);
        save(order);

        // 8. 返回订单id
        return Result.ok(order.getId());
        // 但是底层是：先释放锁才提交事务，存在插空进入的风险
        // 所以将锁移到方法之外
        // }
    }
}




