package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    /**
     *  营业额统计接口
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> localDateList=new ArrayList<>();
        List<Double> doubleList=new ArrayList<>();
        for(;!begin.equals(end);begin=begin.plusDays(1)){
            localDateList.add(begin);
            LocalDateTime beginTime=LocalDateTime.of(begin, LocalTime.MIN);
            LocalDateTime endTime=LocalDateTime.of(begin,LocalTime.MAX);
            Map map=new HashMap<>();
            map.put("begin",beginTime);
            map.put("end",endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover=orderMapper.sumByMap(map);
            turnover=turnover==null?0.0:turnover;
            doubleList.add(turnover);
        }
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(localDateList,','))
                .turnoverList(StringUtils.join(doubleList,','))
                .build();
    }

    /**
     * 用户统计接口
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO userStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> localDateList=new ArrayList<>();
        List<Integer> newUserList=new ArrayList<>();
        List<Integer> totalUserList=new ArrayList<>();
        for(;!begin.equals(end);begin=begin.plusDays(1)){
            localDateList.add(begin);
            Map map=new HashMap();
            LocalDateTime begintime=LocalDateTime.of(begin,LocalTime.MIN);
            LocalDateTime endtime=LocalDateTime.of(begin,LocalTime.MAX);
            map.put("end",endtime);
            Integer totalUsernumber=userMapper.countByMap(map);
            totalUserList.add(totalUsernumber);
            map.put("begin",begintime);
            Integer newUsernumber=userMapper.countByMap(map);
            newUserList.add(newUsernumber);
        }
        return UserReportVO.builder()
                .dateList(StringUtils.join(localDateList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .newUserList(StringUtils.join(newUserList,","))
                .build();

    }

    /**
     * 订单统计接口
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO ordersStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> localDateList=new ArrayList<>();
        List<Integer> orderCountList=new ArrayList<>();
        List<Integer> validOrderCountList=new ArrayList<>();
        Integer sumOrder=0;
        Integer sumvalidOrder=0;
        for(;!begin.equals(end);begin=begin.plusDays(1)){
            localDateList.add(begin);
            Map map=new HashMap();
            LocalDateTime begintime=LocalDateTime.of(begin,LocalTime.MIN);
            LocalDateTime endtime=LocalDateTime.of(begin,LocalTime.MAX);
            map.put("begin",begintime);
            map.put("end",endtime);
            Integer num1=orderMapper.countOrderByMap(map);
            map.put("status",Orders.COMPLETED);
            Integer num2=orderMapper.countOrderByMap(map);
            sumOrder+=num1;
            sumvalidOrder+=num2;
            orderCountList.add(num1);
            validOrderCountList.add(num2);
        }

        return OrderReportVO.builder()
                .dateList(StringUtils.join(localDateList,","))
                .totalOrderCount(sumOrder)
                .validOrderCount(sumvalidOrder)
                .orderCountList(StringUtils.join(orderCountList,","))
                .validOrderCountList(StringUtils.join(validOrderCountList,","))
                .orderCompletionRate((double) sumvalidOrder/(double) sumOrder)
                .build();
    }

    /**
     * 查询销量排名top10接口
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO top10(LocalDate begin, LocalDate end) {
        LocalDateTime begintime=LocalDateTime.of(begin,LocalTime.MIN);
        LocalDateTime endtime=LocalDateTime.of(end,LocalTime.MAX);

        List<GoodsSalesDTO> goodsSalesDTOList=orderMapper.getSalesTop(begintime,endtime);
        List<String> name=goodsSalesDTOList.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String topname=StringUtils.join(name,",");
        List<Integer> count=goodsSalesDTOList.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String topcount=StringUtils.join(count,",");
        return SalesTop10ReportVO.builder()
                .nameList(topname)
                .numberList(topcount)
                .build();

    }

}
