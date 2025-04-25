package com.github.zephyrtoria.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.VoucherOrder;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.mapper.VoucherOrderMapper;
import com.github.zephyrtoria.hmdp.service.ISeckillVoucherService;
import com.github.zephyrtoria.hmdp.service.IVoucherOrderService;
import com.github.zephyrtoria.hmdp.utils.RedisIdWorker;
import com.github.zephyrtoria.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.zephyrtoria.hmdp.consts.OrderConstants.REDIS_LOCK_PREFIX;
import static com.github.zephyrtoria.hmdp.consts.OrderConstants.REDIS_NEXT_ID_PREFIX;

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

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;


    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        // 指定脚本，使用 ClassPathResource()会默认在resource文件夹下寻找资源
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 阻塞队列
    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // 线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    // 事务代理对象
    private IVoucherOrderService proxy;

    // 类初始化完毕后立即执行
    @PostConstruct
    private void init() {
        // 当前类一旦初始化完毕，就要启动这个线程任务，从而能够不断从阻塞队列中取出元素
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 执行的线程任务：不断从阻塞队列中取出元素
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 1. 获取队列中的订单信息
                    // take()如果没有获取元素，则会阻塞，所以不会造成过多资源浪费
                    VoucherOrder order = orderTasks.take();
                    // 2. 创建订单
                    handlerVoucherOrder(order);
                } catch (InterruptedException e) {
                    log.error("处理订单异常");
                }
            }
        }
    }

    private void handlerVoucherOrder(VoucherOrder order) {
        // 将原先的seckillVoucher的部分内容移植到此处
        Long userId = UserHolder.getUser().getId();
        // 用锁包裹事务，实现先提交事务再解锁的步骤
        RLock lock = redissonClient.getLock(REDIS_LOCK_PREFIX + userId);
        boolean isLock = lock.tryLock();
        // 判断锁获取是否成功
        if (!isLock) {
            // 异步处理，不用返回值
            log.error("不允许重复下单");
            return;
        }

        try {
            // 获取事务代理对象
            // 注：由于当前是通过子线程调用的方法，无法从 ThreadLocal 中获取事务代理对象，所以只能提前传入
            // IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            proxy.createVoucher(order);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 执行lua脚本
        Long userId = UserHolder.getUser().getId();
        long flag = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),  // keys 为空集合，但是不能传null
                voucherId.toString(), userId.toString()
        );


        // 2. 判断返回值
        // 2.1 不为0，没有购买资格
        if (flag != 0L) {
            return Result.fail(flag == 1L ? "库存不足" : "不能重复下单");
        }

        // 2.2 为0，有购买资格，把下单信息保存到阻塞队列
        // 2.3 获取事务代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        // 2.4 创建新订单
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWorker.nextId(REDIS_NEXT_ID_PREFIX);
        order.setId(orderId);
        order.setVoucherId(voucherId);
        order.setUserId(userId);
        // 2.5 放入阻塞队列
        orderTasks.add(order);

        // 3. 返回订单ID
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucher(VoucherOrder order) {
        // 5. 一人一单
        Long userId = order.getUserId();
        Long voucherId = order.getVoucherId();

        // 5.1 查询订单
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2 判断该用户是否购买了该优惠券
        if (count > 0) {
            // 已经购买过了
            log.error("不可重复购买");
            return;
        }

        // 6. 扣减库存
        // 乐观锁实现
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            // 扣减失败
            log.error("库存不足！");
        }
    }

/*    @Override
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
        // SimpleRedisLock lock = new SimpleRedisLock(REDIS_LOCK_PREFIX + userId, stringRedisTemplate);
        // boolean isLock = lock.tryLock(REDIS_LOCK_TTL);
        // 使用Redisson进行锁操作
        RLock lock = redissonClient.getLock(REDIS_LOCK_PREFIX + userId);
        boolean isLock = lock.tryLock();
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
            // 获取事务代理对象
            // TODO 了解代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucher(voucherId);
        } finally {
            lock.unlock();
        }
    }*/

    // synchronized 加在方法上 - 对于当前（this）生效
    // 事务注解
/*    @Override
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
    }*/
}