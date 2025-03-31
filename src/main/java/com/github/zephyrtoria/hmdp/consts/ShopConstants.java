package com.github.zephyrtoria.hmdp.consts;

public class ShopConstants {
    // shop cache
    public static final String SHOP_CACHE_REDIS_PREFIX = "cache:shop:";
    public static final Long SHOP_CACHE_TTL = 30L;
    public static final Long SHOP_CACHE_NULL_TTL = 2L;
    public static final String SHOP_TYPE_CACHE_REDIS_KEY = "cache:shop:type";

    // lock
    public static final String SHOP_LOCK_REDIS_PREFIX = "lock:shop:";
    public static final Long SHOP_LOCK_TTL = 10L;
    public static final Long SHOP_LOCK_SLEEP = 50L;
}
