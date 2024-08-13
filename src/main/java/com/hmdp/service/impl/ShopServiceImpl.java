package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //根据id查询商户
    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String lock = LOCK_SHOP_KEY + id;
        Shop shop = null;
        String shopJson = null;
        try {
            //解决缓存穿透也可以用递归,此处用迭代
            while (true) {
                //1.从redis查看缓存
                shopJson = stringRedisTemplate.opsForValue().get(key);
                //2.判断缓存是否存在,不存在就向数据库查询
                if (StrUtil.isNotBlank(shopJson)) {
                    return Result.ok(JSON.parseObject(shopJson, Shop.class));
                }
                //防止缓存穿透
                if (shopJson != null) {
                    return Result.fail("商户不存在");
                }
                //防止缓存击穿使用互斥锁
                if (getLock(lock)) {
                    break;
                }
                Thread.sleep(50);
            }
            //如果你确定查询的是主键字段，getById(id) 通常会比 query().eq("id", id).one() 更高效，因为它是针对主键的优化查询。
            //query().eq("id", id).one() 在没有主键索引或复杂查询条件下可能会稍慢。
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            //3.不存在返回错误
            if (shop == null) {
                //防止缓存穿透,向redis保存空数据
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("商户不存在");
            }
            //4.存在保存至redis并返回数据
            shopJson = JSON.toJSONString(shop);
            stringRedisTemplate.opsForValue().set(key, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            unLock(lock);
        }
        return Result.ok(shop);
    }

    //根据id修改商户
    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("id为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    //用于缓存击穿互斥锁的获取
    public boolean getLock(String key) {
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(lock);
    }

    public void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
