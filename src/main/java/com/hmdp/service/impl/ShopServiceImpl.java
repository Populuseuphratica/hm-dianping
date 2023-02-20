package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public Result queryById(Long id) {
        Shop shop = redisUtil.queryByIdWithPassThrough(id, Shop.class, queryId -> getById(queryId));
        if (shop == null) {
            return Result.fail("商铺不存在");
        }

        // 如果没查询到返回错误
        return Result.ok(shop);
    }
    
    @Override
    @Transactional
    public Result updateShopById(Shop shop) {

        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺不存在");
        }

        // 1.更新数据库
        updateById(shop);

        // 2.删除redis中缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);

        return Result.ok();
    }
}
