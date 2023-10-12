package com.sky.mapper;

import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.github.pagehelper.Page;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {



    /**
     * 用户下单
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单名查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 历史订单查询
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> pageQurey(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据id获取订单
     * @param id
     * @return
     */
    @Select("select * from orders where id=#{id}")
    Orders getById(Long id);

    @Select("select count(*) from orders where status=3")
    Integer getConfirmedNumber();

    @Select("select count(*) from orders where status=4")
    Integer getDeliveryInProgressNumber();

    @Select("select count(*) from orders where status=2")
    Integer getToBeConfirmed();

    @Select("select * from orders where status=#{status} and order_time<#{time}")
    List<Orders> getByStatusAndOrderTime(Integer status, LocalDateTime time);

    /**
     *  营业额统计接口
     * @param map
     * @return
     */
    Double sumByMap(Map map);

    Integer countOrderByMap(Map map);

    List<GoodsSalesDTO> getSalesTop(LocalDateTime begin,LocalDateTime end);
}
