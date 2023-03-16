package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private StringRedisTemplate stringRedisTemplate;

    private IUserService userService;

    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate, IUserService userService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userService = userService;
    }

    // 重写doFollow方法，实现关注和取消关注的功能
    @Override
    public boolean doFollow(Long followUserId, Boolean followable) {
        // 获取当前用户的ID
        Long userId = UserHolder.getUser().getId();
        // 如果followable为true，即关注
        if (followable) {
            // 新建一个关注对象
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            // 将关注对象保存到数据库
            save(follow);

            stringRedisTemplate.opsForSet().add(RedisConstants.FOLLOW_COMMON_KEY + userId, followUserId.toString());
            return true;
        } else {
            // 如果followable为false，即取消关注
            LambdaQueryWrapper<Follow> lambdaQueryWrapper = new LambdaQueryWrapper();
            // 构造查询条件，查询当前用户是否已经关注了followUserId
            lambdaQueryWrapper.eq(Follow::getFollowUserId, followUserId).eq(Follow::getUserId, userId);
            // 根据查询条件删除相应的关注记录
            remove(lambdaQueryWrapper);

            stringRedisTemplate.opsForSet().remove(RedisConstants.FOLLOW_COMMON_KEY + userId, followUserId.toString());
            return false;
        }
    }

    /**
     * @Description: 判断是否关注了该用户<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/13 22:52 <br/>
     * @param: Long followUserId 关注的用户id<br/>
     * @Return: boolean <br/>
     * @Throws:
     */
    @Override
    public boolean isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("follow_user_id", followUserId).eq("user_id", userId).count();
        return (count > 0);
    }

    /**
     * @Description: 查看共同关注用户<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/15 12:43 <br/>
     * @param: Long followUserId <br/>
     * @Return: java.util.List<com.hmdp.dto.UserDTO> <br/>
     * @Throws:
     */
    @Override
    public List<UserDTO> followCommon(Long followUserId) {
        // 获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        // 使用Redis获取双方的关注set，取交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(RedisConstants.FOLLOW_COMMON_KEY + userId, RedisConstants.FOLLOW_COMMON_KEY + followUserId);

        // 如果没有交集，则返回空列表
        if (intersect == null || intersect.isEmpty()) {
            return Collections.emptyList();
        }

        // 从userService中查找id在交集中的用户，并将其转换为UserDTO对象
        List<UserDTO> userDTOS = userService.query().in("id", intersect).list().stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

        // 返回UserDTO对象列表
        return userDTOS;
    }

}
