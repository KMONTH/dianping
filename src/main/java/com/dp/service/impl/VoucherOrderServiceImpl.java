package com.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.dto.Result;
import com.dp.entity.SeckillVoucher;
import com.dp.entity.VoucherOrder;
import com.dp.mapper.VoucherOrderMapper;
import com.dp.mq.MqSender;
import com.dp.service.ISeckillVoucherService;
import com.dp.service.IVoucherOrderService;
import com.dp.utils.RedisWorker;
import com.dp.utils.UserHolder;
import com.google.common.util.concurrent.RateLimiter;
import org.apache.ibatis.javassist.bytecode.analysis.Executor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    RedisWorker redisWorker;
    @Autowired
    VoucherOrderMapper voucherOrderMapper;
    @Autowired
    RedissonClient redissonClient;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private RateLimiter rateLimiter = RateLimiter.create(10);


    private BlockingQueue<VoucherOrder> orderQueue = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_EX = Executors.newSingleThreadExecutor();

/*    //额外线程执行处理数据库删减的任务
    public class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //从stream队列读消息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    MapRecord<String, Object, Object> task = list.get(0);
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(task.getValue(), new VoucherOrder(), true);
                    proxy.creatVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",task.getId());
                } catch (Exception e) {
                    handlePendingList();
                }
            }
        }
    }

    //执行消息出现异常的处理方法
    private void handlePendingList() {
        while (true) {
            try {
                //从pendinglist读消息
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
                );
                if (list == null || list.isEmpty()) {
                    break;
                }
                MapRecord<String, Object, Object> task = list.get(0);
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(task.getValue(), new VoucherOrder(), true);
                proxy.creatVoucherOrder(voucherOrder);
                stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", task.getId());
            } catch (Exception e) {
                log.error("处理订单异常123");
            }
        }
    }*/

/*    //需要一开始就开一个独立线程来准备接收订单
    @PostConstruct
    public void init() {
        SECKILL_EX.submit(new VoucherOrderHandler());
    }*/

    //指明使用的lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /*private VoucherOrderServiceImpl proxy;*/
    @Autowired
    private MqSender mqSender;

    @Override
    //使用lua脚本完成库存与一人一单的原子操作
    public Result getSeckillVoucher(Long voucherId) {

        /*//因为是在子线程处理,子线程直接构造proxy是拿不到主线程的bean的所以要提前拿到然后子线程使用
        proxy = (VoucherOrderServiceImpl) AopContext.currentProxy();*/
        //令牌桶算法 限流
        if (!rateLimiter.tryAcquire(1000, TimeUnit.MILLISECONDS)){
            return Result.fail("目前网络正忙，请重试");
        }
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        if (result != 0) {
            return result == 1 ? Result.fail("库存不足") : Result.fail("用户无购买限额");
        }
        Long orderId = redisWorker.nextId("voucherOrder");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        //*orderQueue.add(voucherOrder);
        mqSender.sendSeckillMessage(JSON.toJSONString(voucherOrder));
        return Result.ok(orderId);
    }

    /*//设计多张表的共同修改加事务
    @Override
    @Transactional
    public void creatVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        //扣减库存
        seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        save(voucherOrder);
    }*/

    /*public Result getSeckillVoucher(Long voucherId) {
        //1.根据id获取优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("该优惠券不存在");
        }
        //2.判断优惠券是否在时效内
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(seckillVoucher.getEndTime()) || now.isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("优惠券不在时效内");
        }
        //3.1判断库存是否充足
        int stock = seckillVoucher.getStock();
        if (stock <= 0) {
            return Result.fail("优惠券已被抢完");
        }
        Long userId = UserHolder.getUser().getId();
        String key="lock:order:"+userId;
        RLock lock = redissonClient.getLock(key);
        //锁加在外面防止锁先释放事务后提交造成的安全问题
        if(!lock.tryLock()){
            return Result.fail("当前用户正在下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
        *//*如果用this会锁住其他用户(因为this单例是同一个实例,全部被锁)
        如果不加intern()方法,每一次toString都是在堆内创建的新对象
        需要使用intern()方法返回常量池中的地址*//**//*
        synchronized (userId.toString().intern()){
            //如果不用代理对象直接调用方法会使事务失效,因为事务是通过代理机制实现的
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        }*//*
    }*/

   /* //设计多张表的共同修改加事务
    @Transactional
    public Result creatVoucherOrder(Long voucherId) {
        //3.2判断用户是否购买限额
        Long userId = UserHolder.getUser().getId();
        if (query().eq("user_id", userId)
                .eq("voucher_id", voucherId).one() != null) {
            return Result.fail("你已达到购买限额");
        }
        //4.扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if (success != true) {
            return Result.fail("优惠券已被抢完!");
        }
        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisWorker.nextId("voucherOrder"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }*/
}
