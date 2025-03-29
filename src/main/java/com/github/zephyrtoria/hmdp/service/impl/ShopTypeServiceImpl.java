package com.github.zephyrtoria.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.ShopType;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.service.IShopTypeService;
import com.github.zephyrtoria.hmdp.mapper.ShopTypeMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.github.zephyrtoria.hmdp.consts.ShopConstants.SHOP_TYPE_CACHE_REDIS_KEY;

/**
 * @author 23240
 * @description 针对表【tb_shop_type】的数据库操作Service实现
 * @createDate 2025-03-27 13:33:15
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType>
        implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        // 1. 查询Redis
        List<ShopType> shopTypes;
        List<String> shopTypesStr = stringRedisTemplate.opsForList().range(SHOP_TYPE_CACHE_REDIS_KEY, 0, -1);
        if (shopTypesStr != null && !shopTypesStr.isEmpty()) {
            // 2. Redis中有，返回
            shopTypes = new ArrayList<>();
            // shopTypesStr.forEach(each -> shopTypes.add(JSONUtil.toBean(each, ShopType.class)));
            return Result.ok(shopTypesStr);
        }

        // 3. Redis中无，查询数据库
        shopTypes = query().orderByAsc("sort").list();
        // 4. 数据库中无，返回
        if (shopTypes == null || shopTypes.isEmpty()) {
            return Result.fail("无商铺类型值存在");
        }
        // 5. 数据库中有，保存到Redis
        shopTypes.forEach(each ->
                stringRedisTemplate.opsForList().leftPush(SHOP_TYPE_CACHE_REDIS_KEY, JSONUtil.toJsonStr(each)));
        // 6. 返回
        return Result.ok(shopTypes);
    }
}




