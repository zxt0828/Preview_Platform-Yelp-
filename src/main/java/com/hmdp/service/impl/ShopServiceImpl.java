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
  private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

  @Resource
  private StringRedisTemplate stringRedisTemplate;

  //
  public void saveShop2Redis(Long id, Long expireSeconds){
    //query shop info from database
    Shop shop = getById(id);

    //encapsulation it to redisData
    RedisData redisData = new RedisData();
    redisData.setData(shop);

    //this key will not expire
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
  }

  @Override
  public Result queryById(Long id){
    //互斥锁的方案，要使用就取消下面的注释
    //Shop shop = queryWithMutex(id);

    //logic expire method
    Shop shop = queryWithLogicalExpire(id);

    //if it is not a hot key, using mutex locker to query
    if(shop == null){
      shop = queryWithMutex();
    }
    if(shop == null){
      return Result.fail("store do not exist");
    }
    return Result.ok(shop);
  }

  // method used by queryById, using mutex locker, also avoid cache penetration
  private Shop queryWithMutex(Long id){
    //get the key of redis
    String key = CACHE_SHOP_KEY + id;

    //query from redis
    String shopJson = stringRedisTemplate.opsForValue().get(key);

    //if exist in redis
    if(StrUtil.isNotBlank(shopJson)){
      return JSONUtil.toBean(shopJson, Shop.class);
    }

    //if hit a empty value
    if(shopJson != null){
      return null;
    }

    //if not exist in redis, try mutex locker
    String lockKey = LOCK_SHOP_KEY + id;
    Shop shop = null;

    //build a mutex locker
    boolean isLock = tryLock(lockKey);
    try{
      //recursion
      if(!isLock){
        Thread.sleep(50);
        return queryWithMutex(id);
      }

      //query database, mybatis plus
      shop = getById(id);

      //if shop is null, store a empty value in redis, avoid penetration
      if(shop == null){
        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        return null;
      }

      //if database has this shop, set in redis
      stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }catch(InterruptedException e){
      throw new RuntimeException(e);
    }finally{
      //anyway, unlock the locker
      unlock(lockKey);
    }

    return shop;
  }

  //query with logical expire
  private Shop queryWithLogicalExpire(Long id){
    String key = CACHE_SHOP_KEY + id;

    //query from redis
    String shopJson = stringRedisTemplate.opsForValue().get(key);

    //if not exist in redis, it is not a hot key
    if (StrUtil.isBlank(shopJson)) {
      return null;
    }

    //if exist
    RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
    Shop shop = JSONUtil.toBean((JSONObject)  redisData.getData(), Shop.class);
    LocalDateTime expireTime = redisData.getExpireTime();

    //check whether it is expired
    if(expireTime.isAfter(LocalDateTime.now())){
      return shop;
    }

    String lockKey = LOCK_SHOP_KEY + id;
    boolean isLock = tryLock(lockKey);
    if(isLock){
      CACHE_REBUILD_EXECUTOR.submit(() -> {
        try {
          this.saveShop2Redis(id, 20L);
        } catch (Exception e) {
          throw new RuntimeException(e);
        } finally {
          unlock(lockKey);
        }
      });
    }

    return shop;
  }

  @Override
  @Transactional
  public Result updateShop(Shop shop){
    //get a shop from frontend, then get the id from it
    Long id = shop.getId();
    if(id == null){
      return Result.fail("shop id cannot be null");
    }
    //update the database， this is mybatis plus method
    updateById(shop);

    //delete the cache
    stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
    return Result.ok();
  }

  //mutex locker
  private boolean tryLock(String key){
    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    return BooleanUtil.isTrue(flag);
  }

  //unlock
  private void unlock(String key){
    stringRedisTemplate.delete(key);
  }
}
