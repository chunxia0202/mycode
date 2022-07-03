package com.nowcoder.seckill.rocket.consumer;

import com.alibaba.fastjson.JSONObject;
import com.nowcoder.seckill.service.ItemService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
//selectorExpression实现定向发送和拉取，意味着这个消费者只消费这个tag
//被@RocketMQMessageListener注解的并且实现了RocketMQListener的bean作为一个消费者监听指定队列中的消息
@RocketMQMessageListener(topic = "seckill", consumerGroup = "seckill_stock", selectorExpression = "decrease_stock")
//最终消费者扣减库存的逻辑
public class DecreaseStockConsumer implements RocketMQListener<String> {

    private Logger logger = LoggerFactory.getLogger(DecreaseStockConsumer.class);

    @Autowired
    private ItemService itemService;

    @Override
    public void onMessage(String message) {
        JSONObject param = JSONObject.parseObject(message);
        int itemId = (int) param.get("itemId");
        int amount = (int) param.get("amount");

        try {
            itemService.decreaseStock(itemId, amount);
            logger.debug("最终扣减库存完成 [" + param.get("itemStockLogId") + "]");
        } catch (Exception e) {
            logger.error("从DB扣减库存失败", e);
        }
    }

}
