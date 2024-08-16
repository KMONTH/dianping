package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@Component
public class RedisUtils {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long TTL, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), TTL, timeUnit);
    }

    public void setLogicExpire(String key, Object value, Long TTL, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(TTL)));
        redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <T, ID> T queryNew(String keyPre, ID id, Class<T> type, Function<ID, T> function) {
        String key = keyPre + id;
        String lock = "lock:" + keyPre + id;
        T t = null;
        String tJson = null;
        try {
            //解决缓存穿透也可以用递归,此处用迭代
            while (true) {
                //1.从redis查看缓存
                tJson = stringRedisTemplate.opsForValue().get(key);
                //2.判断缓存是否存在,不存在就向数据库查询
                if (StrUtil.isNotBlank(tJson)) {
                    return JSON.parseObject(tJson, type);
                }
                //防止缓存穿透
                if (tJson != null) {
                    return null;
                }
                //防止缓存击穿使用互斥锁
                if (getLock(lock)) {
                    break;
                }
                Thread.sleep(50);
            }
            //如果你确定查询的是主键字段，getById(id) 通常会比 query().eq("id", id).one() 更高效，因为它是针对主键的优化查询。
            //query().eq("id", id).one() 在没有主键索引或复杂查询条件下可能会稍慢。
            t = function.apply(id);
            //模拟重建延时
            //Thread.sleep(200);
            //3.不存在返回错误
            if (t == null) {
                //防止缓存穿透,向redis保存空数据
                this.set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //4.存在保存至redis并返回数据
            tJson = JSON.toJSONString(t);
            this.set(key, tJson, 30L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lock);
        }
        return t;
    }

    //用于互斥锁的获取与释放
    public boolean getLock(String key) {
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(lock);
    }

    public void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
