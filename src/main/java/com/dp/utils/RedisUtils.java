package com.dp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.dp.bloom.BloomFilterService;
import com.google.common.hash.BloomFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.dp.utils.RedisConstants.*;

@Component
public class RedisUtils {

    private static final Logger log = LoggerFactory.getLogger(RedisUtils.class);
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private BloomFilterService bloomFilterService;

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
        T t = null;
        String tJson = null;
        try {
            // 1. 使用布隆过滤器检查ID是否可能存在
            if (!bloomFilterService.mightContain((Long) id)) {
                // 如果布隆过滤器判定这个ID不存在，直接返回null，避免缓存穿透
                log.info("布隆过滤器查询不存在");
                return null;
            }
            // 2. 从Redis查看缓存
            tJson = stringRedisTemplate.opsForValue().get(key);
            // 3. 判断缓存是否存在，不存在就向数据库查询
            if (StrUtil.isNotBlank(tJson)) {
                return JSON.parseObject(tJson, type);
            }
            // 防止缓存穿透（虽然不太可能因为布隆过滤器已经排除）
            if (tJson != null) {
                return null;
            }
            // 4. 防止缓存击穿使用互斥锁
            while (true) {
                if (getLock(key, 1000L)) {
                    break;
                }
            }
            // 5. 从数据库查询数据
            t = function.apply(id);
            // 模拟重建延时
            // Thread.sleep(200);
            // 6. 数据不存在，防止缓存穿透，向Redis保存空数据
            if (t == null) {
                this.set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 7. 数据存在，保存至Redis并返回数据
            tJson = JSON.toJSONString(t);
            this.set(key, tJson, 30L, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 8. 释放锁
            unLock(key);
        }
        return t;
    }

    /*public <T, ID> T queryNew(String keyPre, ID id, Class<T> type, Function<ID, T> function) {
        String key = keyPre + id;
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
                if (getLock(key, 1000L)) {
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
            unLock(key);
        }
        return t;
    }*/

    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    //用于互斥锁的获取与释放
    public boolean getLock(String key, Long TTL) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent("lock:" + key, threadId, TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(lock);
    }

    public void unLock(String key) {
        String flag = stringRedisTemplate.opsForValue().get("lock:" + key);
        if (flag != null && flag.equals(ID_PREFIX + Thread.currentThread().getId())) {
            stringRedisTemplate.delete("lock:" + key);
        }
    }
}
