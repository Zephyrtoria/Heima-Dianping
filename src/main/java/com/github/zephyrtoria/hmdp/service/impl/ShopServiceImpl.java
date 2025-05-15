package com.github.zephyrtoria.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.Shop;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.mapper.ShopMapper;
import com.github.zephyrtoria.hmdp.service.IShopService;
import com.github.zephyrtoria.hmdp.utils.RedisClient;
import jakarta.annotation.Resource;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.github.zephyrtoria.hmdp.consts.RedisConstants.SHOP_GEO_PREFIX;
import static com.github.zephyrtoria.hmdp.consts.ShopConstants.SHOP_CACHE_NULL_TTL;
import static com.github.zephyrtoria.hmdp.consts.ShopConstants.SHOP_CACHE_REDIS_PREFIX;
import static com.github.zephyrtoria.hmdp.consts.SystemConstants.DEFAULT_PAGE_SIZE;

/**
 * @author 23240
 * @description 针对表【tb_shop】的数据库操作Service实现
 * @createDate 2025-03-27 13:33:15
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop>
        implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 由Spring IoC创建的单例模式
    @Resource
    private RedisClient redisClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透实现
        Shop shop = redisClient.queryWithPassThrough(SHOP_CACHE_REDIS_PREFIX, id, Shop.class,
                this::getById, SHOP_CACHE_NULL_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿实现
        // Shop shop = redisClient.queryWithLogicExpire(SHOP_CACHE_REDIS_PREFIX, id, Shop.class, this::getById, SHOP_CACHE_NULL_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺ID错误");
        }
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(SHOP_CACHE_REDIS_PREFIX + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1. 判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，直接查询数据库
            Page<Shop> page = query().eq("type_id", typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            return Result.ok(page);
        }

        // 2. 计算分页参数
        int from = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;

        // 3. 查询 Redis，按照距离排序并分页。结果：shopId + distance
        String key = SHOP_GEO_PREFIX + typeId;
        // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),  // 不指定单位则默认为米，返回结果也为米
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );

        // 4. 解析id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        // 4.1 不能指定from，只能获取后手动截取
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>();

        // 注意这里使用skip跳过后，会导致result为空。需要提前判断
        list.stream().skip(from).forEach(result -> {
            // 4.2 获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3 获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        // 5. 根据id查询shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        // 6. 店铺对应距离
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        // 7. 返回
        return Result.ok(shops);
    }
}




