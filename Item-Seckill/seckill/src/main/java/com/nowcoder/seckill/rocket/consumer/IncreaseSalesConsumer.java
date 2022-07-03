package com.nowcoder.seckill.rocket.consumer;

import com.alibaba.fastjson.JSONObject;
import com.nowcoder.seckill.service.ItemService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
//增加效率消费者
@Service
@RocketMQMessageListener(topic = "seckill",
        consumerGroup = "seckill_sales", selectorExpression = "increase_sales")
//主题是seckill,消费组是seckill_sales,tag选择increase_sales
//这里泛型定义消息的类型是字符串，所以将之前生产者存的二进制数据转换为字符串
public class IncreaseSalesConsumer implements RocketMQListener<String> {
    private Logger logger = LoggerFactory.getLogger(DecreaseStockConsumer.class);

    @Autowired
    private ItemService itemService;
    //队列接收到消息时，会通知消费者开始消费这个消息
    @Override
    public void onMessage(String message) {
        JSONObject param = JSONObject.parseObject(message);
        int itemId = (int) param.get("itemId");
        int amount = (int) param.get("amount");

        try {
            //根据itemId与amount开始更新数据库的销量
            itemService.increaseSales(itemId, amount);
            logger.debug("更新销量完成 [" + itemId + "]");
        } catch (Exception e) {
            logger.error("更新销量失败", e);
        }
    }

}
