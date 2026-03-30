package com.hmdp.service.impl;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

  @Resource
  private StringRedisTemplate stringRedisTemplate;

  @Resource
  private CacheClient cacheClient;

  @Override
  public Result queryById(Long id){
    // 三选一，注释掉不用的

    // 缓存穿透
    // Shop shop = cacheClient.queryWithPassThrough(
    //     CACHE_SHOP_KEY, id, Shop.class,
    //     this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

    // 互斥锁防击穿
    Shop shop = cacheClient.queryWithMutex(
        CACHE_SHOP_KEY, id, Shop.class,
        this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

    // 逻辑过期防击穿
    // Shop shop = cacheClient.queryWithLogicalExpire(
    //     CACHE_SHOP_KEY, id, Shop.class,
    //     this::getById, 20L, TimeUnit.SECONDS);

    if (shop == null) {
      return Result.fail("store does not exist");
    }
    return Result.ok(shop);
  }

  @Override
  @Transactional
  public Result updateShop(Shop shop) {
    Long id = shop.getId();
    if (id == null) {
      return Result.fail("shop id cannot be null");
    }
    updateById(shop);
    stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
    return Result.ok();
  }
}
