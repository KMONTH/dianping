package com.dp;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.dp.dto.UserDTO;
import com.dp.entity.User;
import com.dp.service.impl.ShopServiceImpl;
import com.dp.service.impl.UserServiceImpl;
import com.dp.utils.JwtUtils;
import com.dp.utils.RedisWorker;
import lombok.Cleanup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.dp.utils.RedisConstants.LOGIN_USER_KEY;

@SpringBootTest
@Slf4j
class DianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private RedisWorker redisWorker;
    @Autowired
    private UserServiceImpl userService;
@Autowired
StringRedisTemplate stringRedisTemplate;

    @Test
    void TestJwt() {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", 1011L);
        userMap.put("nickName", "0djLjdisdK");
        userMap.put("icon", "");
        String s = JwtUtils.generateJwt(userMap);
        System.out.println(s);
    }

    @Test
    void testShopSave() {
        shopService.saveShop2Redis(1L, 10L);
    }

    ExecutorService ex = Executors.newFixedThreadPool(500);
    @Test
    void testTimeStamp() throws InterruptedException {
        int taskCount = 300;
        CountDownLatch latch = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
                ex.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        Long test = redisWorker.nextId("test");
                        System.out.println(test);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有任务完成，最多等待60秒
        latch.await(60, TimeUnit.SECONDS);
    }
    @Test
    void testMultiLogin() throws IOException {
        List<User> userList = userService.lambdaQuery().last("limit 1000").list();
        Map<String, Object> userMap = new HashMap<>();
        for (User user : userList) {
            String token = UUID.randomUUID().toString().substring(0, 32);
            try {
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                userMap.put("id",userDTO.getId().toString());
                userMap.put("nickName",userDTO.getNickName());
                userMap.put("icon",userDTO.getIcon());
            } catch (Exception e) {
                log.error("Failed to process user: " + user.getId(), e);
            }
            String tokenKey = LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            stringRedisTemplate.expire(tokenKey, 30, TimeUnit.DAYS);
        }

        Set<String> keys = stringRedisTemplate.keys(LOGIN_USER_KEY + "*");
        @Cleanup FileWriter fileWriter = new FileWriter(new File(System.getProperty("user.dir") + "/tokens.txt"));
        @Cleanup BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
        assert keys != null;
        for (String key : keys) {
            String token = key.substring(LOGIN_USER_KEY.length());
            String text = token + "\n";
            bufferedWriter.write(text);
        }
    }



}
