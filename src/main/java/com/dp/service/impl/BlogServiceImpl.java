package com.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.dto.Result;
import com.dp.dto.UserDTO;
import com.dp.entity.Blog;
import com.dp.entity.User;
import com.dp.mapper.BlogMapper;
import com.dp.service.IBlogService;
import com.dp.service.IUserInfoService;
import com.dp.service.IUserService;
import com.dp.utils.RedisConstants;
import com.dp.utils.SystemConstants;
import com.dp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dp.utils.RedisConstants.BLOG_LIKE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    IUserService userServiceImpl;
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getBlog(Long id) {
        Blog blog = query().eq("id", id).one();
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        UserDTO userNow = UserHolder.getUser();
        User user = userServiceImpl.getById(blog.getUserId());
        if (stringRedisTemplate.opsForZSet().score(BLOG_LIKE_KEY + id, userNow.getId().toString()) != null) {
            blog.setIsLike(true);
        } else {
            blog.setIsLike(false);
        }
        blog.setUserId(user.getId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            Long userId = blog.getUserId();
            UserDTO userNow = UserHolder.getUser();
            if (userNow!=null) {
                Double success = stringRedisTemplate.opsForZSet().score(BLOG_LIKE_KEY + blog.getId(), userNow.getId().toString());
                blog.setIsLike(success != null);
            }
            User user = userServiceImpl.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        });
        return Result.ok(records);
    }

    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        UserDTO userNow = UserHolder.getUser();
        Long userNowId = userNow.getId();
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKE_KEY + id, userNowId.toString());
        if (score != null) {

            //lombok根据islike开头的类给的setter方法是like
            boolean success = update().setSql("liked = liked - 1")
                    .eq("id", id)
                    .update();
            if (success) {
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKE_KEY + id, userNowId.toString());
            }
        } else {
            boolean success = update().setSql("liked = liked + 1")
                    .eq("id", id)
                    .update();
            if (success) {
                stringRedisTemplate.opsForZSet().add(BLOG_LIKE_KEY + id, userNowId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryLikes(Long id) {
        //这里set是LinkedHashSet所以可以保证有序
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(BLOG_LIKE_KEY + id, 0, 4);
        if(top5==null||top5.size()==0){
            return Result.ok();
        }
        List<Long> collect = top5.stream().map(s -> Long.valueOf(s)).collect(Collectors.toList());
        //这里查询无法有序是因为条件时where in(id...),会默认按id大小排,所以想要有序得自己手写
        List<UserDTO> users = userServiceImpl.listByIds(collect).stream().map(user ->
                BeanUtil.copyProperties(user, UserDTO.class)
        ).collect(Collectors.toList());
        return Result.ok(users);
    }
}
