package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CAHE_SHOPTYPE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryAll() {
        String key = CAHE_SHOPTYPE_KEY;
        //1.从redis中查询数据,有返回,无查数据库
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopTypeJson)) {
            return Result.ok(JSON.parseObject(shopTypeJson,
                    new TypeReference<List<ShopType>>(){}));
        }
        //2.查数据库,有保存至redis并返回,无就报错
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        if(shopTypes == null){
            return Result.fail("无任何商品类型");
        }
        shopTypeJson = JSON.toJSONString(shopTypes);
        stringRedisTemplate.opsForValue().set(key, shopTypeJson);
        return Result.ok(shopTypes);
    }
}
