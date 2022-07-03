package com.nowcoder.seckill;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

//@SpringBootApplication
public class RocketMQTestInSpring implements CommandLineRunner {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public static void main(String[] args) {
        //启动环境
        //将线程放在main方法中，因为test方法在线程执行完之后方法就挂掉了；而main方法不会挂掉
        SpringApplication.run(RocketMQTestInSpring.class, args);
    }

    @Override
    //在run方法中执行逻辑
    public void run(String... args) throws Exception {
//        testProduce();
        testProduceInTransaction();
    }

    private void testProduce() throws Exception {
        for (int i = 0; i < 10; i++) {
            String destination = "seckillTest:tag" + (i % 2);//声明主题：标签
            Message message = MessageBuilder.withPayload("message " + i).build();
            rocketMQTemplate.asyncSend(destination, message, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {
                    System.out.println("SUCCESS: " + sendResult);
                }

                @Override
                public void onException(Throwable e) {
                    e.printStackTrace();
                }
            }, 3000);//超时处理方式：超过3000毫秒还没有响应就认为不会响应了
        }
    }
    //给broker server发送信息
    private void testProduceInTransaction() throws Exception {
        String destination = "seckillTest:tagT";
        for (int i = 0; i < 10; i++) {
            Message message = MessageBuilder.withPayload("message " + i).build();
            TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction(destination, message, null);
            System.out.println(sendResult);
        }
    }
    //执行本地事务与check(查询数据库的事务状态）
    //listener只能有一个
//    @RocketMQTransactionListener
    private class TransactionListenerImpl implements RocketMQLocalTransactionListener {

        @Override
        //提交事务
        public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
            System.out.println("executeLocalTransaction: " + msg + ", " + arg);
            return RocketMQLocalTransactionState.COMMIT;
            //broker的信息给消费者消费
            //rollback则服务器撤销消息，unknown不提交也不撤销
        }

        @Override
        //check
        public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
            System.out.println("checkLocalTransaction: " + msg);
            return RocketMQLocalTransactionState.COMMIT;
        }
    }
    //在spring 下的consumer方法，独立的consumer有独立的方法；通过@service与@RocketMQMessageListener可以创建消费者
    //RocketMQMessageListener的底层handleMessage帮助实现消费者消费操作，我们只需调用这个即可
    @Service
    @RocketMQMessageListener(topic = "seckillTest",
            consumerGroup = "seckill_consumer_0", selectorExpression = "tag0")
    private class StringConsumer0 implements RocketMQListener<String> {

        @Override
        public void onMessage(String message) {
            System.out.println("StringConsumer0: " + message);
        }
    }

    @Service
    @RocketMQMessageListener(topic = "seckillTest",
            consumerGroup = "seckill_consumer_1", selectorExpression = "tag1")
    private class StringConsumer1 implements RocketMQListener<String> {

        @Override
        public void onMessage(String message) {
            System.out.println("StringConsumer1: " + message);
        }
    }

    @Service
    @RocketMQMessageListener(topic = "seckillTest",
            consumerGroup = "seckill_consumer_x", selectorExpression = "*")
    private class StringConsumerX implements RocketMQListener<String> {

        @Override
        public void onMessage(String message) {
            System.out.println("StringConsumerX: " + message);
        }
    }

}
