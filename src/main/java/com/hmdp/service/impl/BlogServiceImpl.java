package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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

    private IFollowService followService;

    private final StringRedisTemplate stringRedisTemplate;

    private RedisTemplate redisTemplate;

    public BlogServiceImpl(IUserService userService, IFollowService followService, StringRedisTemplate stringRedisTemplate, RedisTemplate redisTemplate) {
        this.userService = userService;
        this.followService = followService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * @Description: 发布探店笔记，同时将其推送给关注我的人<br/>
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/16 12:01 <br/>
     * @param: Blog blog <br/>
     * @Return: long <br/>
     * @Throws:
     */
    @Override
    public long saveBlog(Blog blog) {
        // 获取当前登录用户的 ID，并将其设置为博客的作者 ID
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);

        // 保存博客
        save(blog);

        // 获取博客的 ID
        Long blogId = blog.getId();

        // 将博客添加到关注用户的收件箱中
        List<Follow> followUsers = followService.query().eq("follow_user_id", userId).select("user_id").list();
        for (Follow followUser : followUsers) {
            Long followId = followUser.getUserId();
            stringRedisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + followId, blogId.toString(), System.currentTimeMillis());
        }

        // 返回博客的 ID
        return blogId;
    }


    /**
     * @Description: <br/>
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/16 16:25 <br/>
     * @param: Long lastId 上次查询的最后一个博客的权重
     * @param: Integer offset 同一权重存在的条数，分页的偏移量<br/>
     * @Return: com.hmdp.dto.ScrollResult 滚动结果对象，包含博客列表和分页信息<br/>
     * @Throws:
     */
    @Override
    public ScrollResult queryFollowBlog(Long lastId, Integer offset) {
        // 获取当前用户的 ID
        Long userId = UserHolder.getUser().getId();

        // 如果 lastId 为空，则将其设置为当前时间的毫秒数
        if (lastId == null) {
            lastId = System.currentTimeMillis();
        }

        // 如果 offset 为空，则将其设置为 0
        if (offset == null) {
            offset = 0;
        }

        // 从 Redis 中获取博客 ID 的有序集合，并按照分数（时间）从小到大排序
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().rangeByScoreWithScores(RedisConstants.FEED_KEY + userId, 0, lastId, offset, 5l);

        // 将博客 ID 添加到 ArrayList 中，并求出下次应该的偏移量
        List<String> blogIds = new ArrayList<>();
        Double prev = Double.valueOf(lastId);
        Integer nextOffset = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String blogId = typedTuple.getValue();
            blogIds.add(blogId);
            Double score = typedTuple.getScore();
            if (prev == score) {
                nextOffset++;
            } else {
                prev = score;
                nextOffset = 1;
            }
        }

        // 查询博客列表，按照博客 ID 在 ArrayList 中的顺序排序
        List<Blog> blogs = Collections.emptyList();
        if (!blogIds.isEmpty()) {
            // 将博客 ID 倒序排列，并用逗号分隔
            Collections.reverse(blogIds);
            String blogIdsStr = StrUtil.join(",", blogIds);
            // 根据博客发布时间倒叙查询出博客
            blogs = query().in("id", blogIds).last("ORDER BY FIELD(id," + blogIdsStr + ")").list();
        }

        // 为每个博客设置详细信息
        if (blogs != null && !blogs.isEmpty()) {
            blogs.stream().forEach(this::setInfoForBlog);
        }

        // 创建滚动结果对象，并设置分页信息和博客列表
        ScrollResult<Blog> scrollResult = new ScrollResult();
        scrollResult.setOffset(nextOffset);
        scrollResult.setMinTime(prev.longValue());
        scrollResult.setList(blogs);

        return scrollResult;
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

        // 根据博客ID构造Redis缓存Key
        String redisKey = RedisConstants.BLOG_LIKED_KEY + blogId;

        // 获取点赞次数排名前5的用户ID
        Set<String> userIds = stringRedisTemplate.opsForZSet().range(redisKey, 0, 4);

        // 如果用户ID集合为null或空，则返回空列表
        if (userIds == null || userIds.isEmpty()) {
            return ListUtil.empty();
        }

        // 将用户ID集合转为逗号分隔的字符串
        String userId = StrUtil.join(",", userIds);

        // 根据用户ID查询用户信息并按ID顺序排序
        List<User> userList = userService.query().in("id", userIds).last("ORDER BY FIELD(id," + userId + ")").list();

        // 将用户信息转为 UserDTO 类型的列表
        List<UserDTO> userDTOS = BeanUtil.copyToList(userList, UserDTO.class);

        // 返回 UserDTO 类型的列表
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
