package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;
    /**
     * 处理超时订单的方法
     */
    @Scheduled(cron = "0 * * * * ?")//每分钟触发一次
    public void prossTimeoutOrder(){
        log.info("定时处理超时订单:{}", LocalDateTime.now());
        LocalDateTime time=LocalDateTime.now().plusMinutes(-15);
        List<Orders> ordersList=orderMapper.getByStatusAndOrderTime(Orders.PENDING_PAYMENT,time);
        if(ordersList!=null&&ordersList.size()>0){
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("订单超时，自动取消");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }
    }

    /**
     * 处理一直派送中的订单
     */
    @Scheduled(cron = "0 0 1 * * *")//每天凌晨一点
    public void prossDeliveryOrder(){
        log.info("定时处理已派送订单:{}",LocalDateTime.now());
        LocalDateTime time=LocalDateTime.now().plusHours(-1);
        List<Orders> ordersList=orderMapper.getByStatusAndOrderTime(Orders.DELIVERY_IN_PROGRESS,time);
        if(ordersList!=null&&ordersList.size()>0){
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            }
        }
    }
}
