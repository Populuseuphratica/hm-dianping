package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    long saveBlog(Blog blog);

    ScrollResult queryFollowBlog(Long lastId, Integer offset);

    void likeBlog(Long id);

    List<UserDTO> getBlogLikes(Long blogId);

    Blog getBlogById(Long id);

    List<Blog> getHotBlog(Integer current);
}
