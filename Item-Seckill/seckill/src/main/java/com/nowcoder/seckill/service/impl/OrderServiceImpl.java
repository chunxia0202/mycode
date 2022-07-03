package com.nowcoder.seckill.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.nowcoder.seckill.common.BusinessException;
import com.nowcoder.seckill.common.ErrorCode;
import com.nowcoder.seckill.common.Toolbox;
import com.nowcoder.seckill.dao.OrderMapper;
import com.nowcoder.seckill.dao.SerialNumberMapper;
import com.nowcoder.seckill.entity.*;
import com.nowcoder.seckill.service.ItemService;
import com.nowcoder.seckill.service.OrderService;
import com.nowcoder.seckill.service.UserService;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;

@Service
public class OrderServiceImpl implements OrderService, ErrorCode {

    private Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private SerialNumberMapper serialNumberMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 格式：日期 + 流水
     * 示例：20210123000000000001
     *
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateOrderID() {
        StringBuilder sb = new StringBuilder();

        // 拼入日期
        sb.append(Toolbox.format(new Date(), "yyyyMMdd"));

        // 获取流水号
        SerialNumber serial = serialNumberMapper.selectByPrimaryKey("order_serial");
        Integer value = serial.getValue();

        // 更新流水号
        serial.setValue(value + serial.getStep());
        serialNumberMapper.updateByPrimaryKey(serial);

        // 拼入流水号
        String prefix = "000000000000".substring(value.toString().length());
        sb.append(prefix).append(value);

        return sb.toString();
    }

    @Transactional
    //有事务保障：订单创建与更新流水同时成功同时失败
    public Order createOrder(int userId, int itemId, int amount, Integer promotionId, String itemStockLogId) {
        // 校验参数
        if (amount < 1 || (promotionId != null && promotionId.intValue() <= 0)) {
            throw new BusinessException(PARAMETER_ERROR, "指定的参数不合法！");
        }
        //将下单前的验证环节迁移出去了，为了削峰限流
        //从缓存中读取商品
        Item item = itemService.findItemInCache(itemId);
        if (item == null) {
            throw new BusinessException(PARAMETER_ERROR, "指定的商品不存在！");
        }
        boolean successful = itemService.decreaseStockInCache(itemId, amount);
        //若扣减库存失败返回库存不足
        logger.debug("预扣减库存完成 [" + successful + "]"
        );
        if (!successful) {
            throw new BusinessException(STOCK_NOT_ENOUGH, "库存不足！");
        }
        // 生成订单
        Order order = new Order();
        order.setId(this.generateOrderID());
        order.setUserId(userId);
        order.setItemId(itemId);
        order.setPromotionId(promotionId);
        order.setOrderPrice(promotionId != null ? item.getPromotion().getPromotionPrice() : item.getPrice());
        order.setOrderAmount(amount);
        order.setOrderTotal(order.getOrderPrice().multiply(new BigDecimal(amount)));
        order.setOrderTime(new Timestamp(System.currentTimeMillis()));
        orderMapper.insert(order);
        logger.debug("生成订单完成 [" + order.getId() + "]");
        //异步更小销量
        JSONObject body = new JSONObject();
        body.put("itemId", itemId);
        body.put("amount", amount);
        Message msg = MessageBuilder.withPayload(body.toString()).build();
        //将后续消费信息的格式封装到JSONObject格式的数据中，然后转换成字符串封装成消息传入异步消息方法中
        //更新订单的消息发送成功与否通过sendcallback方法回传
        rocketMQTemplate.asyncSend("seckill:increase_sales", msg, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                logger.debug("投递增加商品销量消息成功");
            }
            @Override
            public void onException(Throwable e) {
                logger.error("投递增加商品销量消息失败", e);
            }
        }, 60 * 1000);//超时时间60秒，若60秒还未被消费就不重发了
        itemService.updateItemStockLogStatus(itemStockLogId, 1);
        logger.debug("更新流水完成 [" + itemStockLogId + "]");
        return order;
    }
    @Override
    public void createOrderAsync(int userId, int itemId, int amount, Integer promotionId) {
        // 售罄标识
        if (redisTemplate.hasKey("item:stock:over:" + itemId)) {
            throw new BusinessException(STOCK_NOT_ENOUGH, "已经售罄！");
        }
        // 生产者给代理服务器发的消息itemStockLog，里面有初始的库存流水状态值0
        ItemStockLog itemStockLog = itemService.createItemStockLog(itemId, amount);
        logger.debug("生成库存流水完成 [" + itemStockLog.getId() + "]");

        // 消息体
        //broker server存的最后给consumer使用的消费的消息
        //最后的consumer扣减库存是根据id与购买的amount来进行扣减的；
        //当然如果最终没有到consumer,需要回查的时候要带着库存流水id差库存状态的
        //这是第一步生产者给broker发消息，发的消息最终是要给消费者使用的
        //body是为了给消费者消费的
        //生产者此处发的是半消息
        //然后执行生产者的事务型消息，若生产者事务型消息commit,则将消息传给消费者消费
        JSONObject body = new JSONObject();
        body.put("itemId", itemId);
        body.put("amount", amount);
        body.put("itemStockLogId", itemStockLog.getId());//库存流水最终是为了回查使用的

        // 本地事务参数
        //最终用于事务操作中的创建订单
        //args是为了生产者完成事务操作的
        JSONObject arg = new JSONObject();
        arg.put("userId", userId);
        arg.put("itemId", itemId);
        arg.put("amount", amount);
        arg.put("promotionId", promotionId);
        arg.put("itemStockLogId", itemStockLog.getId());

        String dest = "seckill:decrease_stock";
        Message msg = MessageBuilder.withPayload(body.toString()).build();
        try {
            logger.debug("尝试投递扣减库存消息 [" + body.toString() + "]");
            //rocket发送事务型消息
            TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction(dest, msg, arg);
            if (sendResult.getLocalTransactionState() == LocalTransactionState.UNKNOW) {
                throw new BusinessException(UNDEFINED_ERROR, "创建订单失败！");
            } else if (sendResult.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE) {
                throw new BusinessException(CREATE_ORDER_FAILURE, "创建订单失败！");
            }
        } catch (MessagingException e) {
            throw new BusinessException(CREATE_ORDER_FAILURE, "创建订单失败！");
        }
    }
}
