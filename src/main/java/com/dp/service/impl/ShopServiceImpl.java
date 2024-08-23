package com.dp.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.bloom.BloomFilterService;
import com.dp.dto.Result;
import com.dp.entity.Shop;
import com.dp.mapper.ShopMapper;
import com.dp.service.IShopService;
import com.dp.utils.RedisData;
import com.dp.utils.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static com.dp.utils.RedisConstants.*;

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
    @Resource
    private RedisUtils redisUtils;
    @Autowired
    private BloomFilterService bloomFilterService;

    //根据id查询商户
    @Override
    public Result queryById(Long id) {
        //互斥锁解决缓存击穿,此处暂未写逻辑过期方法
        //Shop shop = queryWithMutex(id);
        Shop shop = redisUtils.queryNew(CACHE_SHOP_KEY, id, Shop.class, this::getById);
        if (shop == null) {
            return Result.fail("商户不存在");
        }
        return Result.ok(shop);
    }

    /*public Shop queryWithMutex(Long id) {
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
                    return JSON.parseObject(shopJson, Shop.class);
                }
                //防止缓存穿透
                if (shopJson != null) {
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
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            //3.不存在返回错误
            if (shop == null) {
                //防止缓存穿透,向redis保存空数据
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //4.存在保存至redis并返回数据
            shopJson = JSON.toJSONString(shop);
            stringRedisTemplate.opsForValue().set(key, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lock);
        }
        return shop;
    }*/

    //根据id修改商户
    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("id为空");
        }
        updateById(shop);

        //采用延迟双删策略
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    /*//用于缓存击穿互斥锁的获取与释放
    public boolean getLock(String key) {
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(lock);
    }

    public void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
*/
    //用于逻辑过期数据预热
    public void saveShop2Redis(Long id, Long expire) {
        Shop shop = getById(id);
        if (shop == null) {
            System.out.println("添加数据失败");
        }
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expire));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSON.toJSONString(redisData));
        System.out.println("添加成功");
    }
}
