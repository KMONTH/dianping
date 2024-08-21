package com.dp.service.impl;

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
        if (stringRedisTemplate.opsForSet().isMember(BLOG_LIKE_KEY + id, userNow.getId().toString())) {
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
            Boolean success = stringRedisTemplate.opsForSet().isMember(BLOG_LIKE_KEY + blog.getId(), userNow.toString());
            blog.setIsLike(BooleanUtil.isTrue(success));
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
        Boolean like = stringRedisTemplate.opsForSet().isMember(BLOG_LIKE_KEY + id, userNowId.toString());
        if (like == true) {

            //lombok根据islike开头的类给的setter方法是like
            boolean success = update().setSql("liked = liked - 1")
                    .eq("id", id)
                    .update();
            if(success){
                stringRedisTemplate.opsForSet().remove(BLOG_LIKE_KEY + id, userNowId.toString());
            }
        } else {
            boolean success = update().setSql("liked = liked + 1")
                    .eq("id", id)
                    .update();
            if(success){
                stringRedisTemplate.opsForSet().add(BLOG_LIKE_KEY + id, userNowId.toString());
            }
        }
        return Result.ok();
    }
}
