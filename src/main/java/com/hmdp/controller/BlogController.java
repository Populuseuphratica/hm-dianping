package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
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
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;


    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        blogService.save(blog);
        // 返回id
        return Result.ok(blog.getId());
    }

    /**
     * @Description: 点赞<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/12 18:14 <br/>
     * @param: Long id 探店笔记Id<br/>
     * @Return: com.hmdp.dto.Result <br/>
     * @Throws:
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        blogService.likeBlog(id);
        return Result.ok();
    }

    /**
     * @Description: 笔记详细页面最早点赞人Top5<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/13 17:24 <br/>
     * @param: Long id 探店笔记id<br/>
     * @Return: com.hmdp.dto.Result <br/>
     * @Throws:
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        List<UserDTO> userDTOS = blogService.getBlogLikes(id);
        return Result.ok(userDTOS);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * @Description: 查询热门笔记<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/12 18:26 <br/>
     * @param: Integer current <br/>
     * @Return: com.hmdp.dto.Result <br/>
     * @Throws:
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {

        List<Blog> hotBlog = blogService.getHotBlog(current);
        return Result.ok(hotBlog);
    }

    /**
     * @Description: 查询笔记详细<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/12 18:26 <br/>
     * @param: Long id <br/>
     * @Return: com.hmdp.dto.Result <br/>
     * @Throws:
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        Blog blogById = blogService.getBlogById(id);
        return Result.ok(blogById);
    }


    /**
     * @Description: 查询用户写的探店笔记<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/14 0:12 <br/>
     * @param: Integer current
     * @param: Long id <br/>
     * @Return: com.hmdp.dto.Result <br/>
     * @Throws:
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                    @RequestParam("id") Long id) {
        Page<Blog> page = blogService.query().eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

}
