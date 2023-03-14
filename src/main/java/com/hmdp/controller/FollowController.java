package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    private final IFollowService followService;

    public FollowController(IFollowService followService) {
        this.followService = followService;
    }

    /**
     * @Description: 关注<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/14 22:15 <br/>
     * @param: Long followUserId
     * @param: Boolean followable <br/>
     * @Return: com.hmdp.dto.Result 关注成功：true，取关：false<br/>
     * @Throws:
     */
    @PutMapping("/{id}/{followable}")
    public Result doFollow(@PathVariable("id") Long followUserId, @PathVariable("followable") Boolean followable) {
        boolean isFollowed = followService.doFollow(followUserId, followable);
        return Result.ok(isFollowed);
    }

    /**
     * @Description: 判断是否关注了该用户<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/13 22:52 <br/>
     * @param: Long followUserId 关注的用户id<br/>
     * @Return: boolean <br/>
     * @Throws:
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        boolean follow = followService.isFollow(followUserId);
        return Result.ok(follow);
    }

    /**
     * @Description: 查看共同关注用户<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/14 22:03 <br/>
     * @param: Long followUserId <br/>
     * @Return: com.hmdp.dto.Result <br/>
     * @Throws:
     */
    @GetMapping("/common/{id}")
    public Result followCommon(@PathVariable("id") Long followUserId) {
        List<UserDTO> userDTOS = followService.followCommon(followUserId);
        return Result.ok(userDTOS);
    }

}
