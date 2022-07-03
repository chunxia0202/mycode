package com.nowcoder.seckill.service.impl;

import com.nowcoder.seckill.common.BusinessException;
import com.nowcoder.seckill.common.ErrorCode;
import com.nowcoder.seckill.entity.Item;
import com.nowcoder.seckill.entity.User;
import com.nowcoder.seckill.service.ItemService;
import com.nowcoder.seckill.service.PromotionService;
import com.nowcoder.seckill.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PromotionServiceImpl implements PromotionService, ErrorCode {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private ItemService itemService;

    @Override
    public String generateToken(int userId, int itemId, int promotionId) {
        if (userId < 0 || itemId < 0 || promotionId < 0) {
            return null;
        }

        // 售罄标识
        if (redisTemplate.hasKey("item:stock:over:" + itemId)) {
            return null;
        }

        // 校验用户
        User user = userService.findUserFromCache(userId);
        if (user == null) {
            return null;
        }

        // 校验商品
        Item item = itemService.findItemInCache(itemId);
        if (item == null) {
            return null;
        }

        // 校验活动
        if (item.getPromotion() == null
                || !item.getPromotion().getId().equals(promotionId)
                || item.getPromotion().getStatus() != 0) {//
            return null;
        }

        // 秒杀大闸--控制的是商品token,大闸可以放入商品的十倍数量的用户，目的是生成可以抢占当前商品的token
        //用于商品token的用户才能进一步秒杀商品
        ValueOperations v = redisTemplate.opsForValue();
        if (v.decrement("promotion:gate:" + promotionId, 1) < 0) {
            return null;
        }

        String key = "promotion:token:" + userId + ":" + itemId + ":" + promotionId;
        //令牌的生成方式是UUID
        //生成不重复的字符串UUID.randomUUID().toString()
        String token = UUID.randomUUID().toString().replace("-", "");
        //有效时间1分钟
        v.set(key, token, 10, TimeUnit.MINUTES);

        return token;
    }

}
