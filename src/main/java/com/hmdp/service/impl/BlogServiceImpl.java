package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

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

    private final IUserService userService;

    private final StringRedisTemplate stringRedisTemplate;

    private RedisTemplate redisTemplate;

    public BlogServiceImpl(IUserService userService, StringRedisTemplate stringRedisTemplate, RedisTemplate redisTemplate) {
        this.userService = userService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * @Description: 点赞<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/12 14:16 <br/>
     * @param: Long id 探店笔记Id(BlogId)<br/>
     * @Return: void <br/>
     * @Throws:
     */
    @Override
    public void likeBlog(Long blogId) {
        Long userId = UserHolder.getUser().getId();
        String redisKey = RedisConstants.BLOG_LIKED_KEY + blogId;
        ZSetOperations<String, String> stringStringZSetOperations = stringRedisTemplate.opsForZSet();
        // 查询是否在 redis Set中
        Double score = stringStringZSetOperations.score(redisKey, userId.toString());

        // 存在则取消点赞，不存在则点赞
        if (score != null) {
            boolean updated = update().setSql("liked = liked - 1 ").eq("id", blogId).update();
            if (updated) {
                stringStringZSetOperations.remove(redisKey, userId.toString());
            }
        } else {
            boolean updated = update().setSql("liked = liked + 1 ").eq("id", blogId).update();
            if (updated) {
                stringStringZSetOperations.add(redisKey, userId.toString(), System.currentTimeMillis());
            }
        }
    }

    /**
     * @Description: 笔记详细页面最早点赞人Top5<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/13 17:24 <br/>
     * @param: Long id 探店笔记id<br/>
     * @Return: com.hmdp.dto.Result <br/>
     * @Throws:
     */
    @Override
    public List<UserDTO> getBlogLikes(Long blogId) {
        String redisKey = RedisConstants.BLOG_LIKED_KEY + blogId;
        // 取点赞时间排名前5的用户id
        Set<String> userIds = stringRedisTemplate.opsForZSet().range(redisKey, 0, 4);
        if (userIds == null || userIds.isEmpty()) {
            return ListUtil.empty();
        }
        String userId = StrUtil.join(",", userIds);
        List<User> userList = userService.query().in("id", userIds).last("ORDER BY FIELD(id," + userId + ")").list();
        List<UserDTO> userDTOS = BeanUtil.copyToList(userList, UserDTO.class);
        return userDTOS;
    }

    @Override
    public Blog getBlogById(Long id) {
        Blog blog = getById(id);
        setInfoForBlog(blog);
        return blog;
    }

    @Override
    public List<Blog> getHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::setInfoForBlog);
        return records;
    }

    /**
     * @Description: 给笔记设定用户信息和是否被当前用户点赞<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/13 17:03 <br/>
     * @param: Blog blog <br/>
     * @Return: void <br/>
     * @Throws:
     */
    private void setInfoForBlog(Blog blog) {
        // 设定笔记作者信息
        Long blogUserId = blog.getUserId();
        User user = userService.getById(blogUserId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());

        // 查看是笔记是否被该user点赞
        String redisKey = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        UserDTO userDTO = UserHolder.getUser();
        // 如果是主页未登录时，则不判断
        if (userDTO == null) {
            return;
        }
        // 查询是否在 redis Set中
        Double score = stringRedisTemplate.opsForZSet().score(redisKey, userDTO.getId().toString());
        blog.setIsLike(score != null);
    }
}
