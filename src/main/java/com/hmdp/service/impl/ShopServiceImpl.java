package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY+id;
        //1.从redis查看缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            return Result.ok(JSON.parseObject(shopJson, Shop.class));
        }
        //如果你确定查询的是主键字段，getById(id) 通常会比 query().eq("id", id).one() 更高效，因为它是针对主键的优化查询。
        //query().eq("id", id).one() 在没有主键索引或复杂查询条件下可能会稍慢。
        Shop shop = /*query().eq("id", id).one();*/ getById(id);
        //3.不存在返回错误
        if(shop == null){
            return Result.fail("商户不存在");
        }
        //4.存在保存至redis并返回数据
        shopJson = JSON.toJSONString(shop);
        stringRedisTemplate.opsForValue().set(key, shopJson);
        return Result.ok(shop);
    }
}
