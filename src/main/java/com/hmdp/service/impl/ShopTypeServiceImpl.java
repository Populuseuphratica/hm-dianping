package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public List<ShopType> queryTypeList() {
        String redisKey = RedisConstants.CACHE_SHOP_KEY + "typeList";
        Object o = redisTemplate.opsForValue().get(redisKey);
        if (o != null) {
            return (List<ShopType>) o;
        }

        List<ShopType> typeList = query().orderByAsc("sort").list();

        redisTemplate.opsForValue().set(redisKey, typeList, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return typeList;
    }

}
