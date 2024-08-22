package com.dp.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 消息发送者
 */
@Slf4j
@Service
public class MqSender {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String ROUTINGKEY = "seckill.message";
    private static final String EXCHANGE = "seckillExchange";
    /**
     * 发送秒杀信息
     * @param msg
     */
    public void sendSeckillMessage(String msg){
        log.info("发送消息"+msg);
        rabbitTemplate.convertAndSend(EXCHANGE,ROUTINGKEY,msg);
    }
}
