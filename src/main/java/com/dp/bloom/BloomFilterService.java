package com.dp.bloom;

import com.dp.entity.Shop;
import com.dp.service.IShopService;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class BloomFilterService {
    private static final String BLOOM_FILTER_NAME = "myBloomFilter";

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private IShopService shopServiceImpl;  // 假设这是你的数据访问层

    private RBloomFilter<Long> bloomFilter;

    @PostConstruct
    public void init() {
        // 初始化布隆过滤器
        bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_NAME);

        // 配置布隆过滤器的参数
        long expectedInsertions = 1000000L; // 预计数据量
        double falseProbability = 0.01;     // 误判率
        bloomFilter.tryInit(expectedInsertions, falseProbability);

        // 加载数据库中的所有ID到布隆过滤器中
        List<Shop> list = shopServiceImpl.query().list();
        list.forEach(shop -> {
            Long id = shop.getId();
            bloomFilter.add(id);
        });
    }

    public boolean mightContain(Long id) {
        return bloomFilter.contains(id);
    }

    public void addId(Long id) {
        bloomFilter.add(id);
    }
}
