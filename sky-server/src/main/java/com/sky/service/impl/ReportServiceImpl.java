package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    @Autowired
    private WorkspaceService workspaceService;

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

    @Override
    public void export(HttpServletResponse response) {
        LocalDate dataBegin=LocalDate.now().minusDays(30);
        LocalDate dataEnd=LocalDate.now().minusDays(1);

        //查询数据库
        BusinessDataVO businessDataVO=workspaceService.getBusinessData(LocalDateTime.of(dataBegin,LocalTime.MIN),LocalDateTime.of(dataEnd,LocalTime.MAX));
        //通过poi将数据写入excel
        InputStream in=this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try {
            XSSFWorkbook excel=new XSSFWorkbook(in);
            XSSFSheet sheet=excel.getSheet("Sheet1");
            sheet.getRow(1).getCell(1).setCellValue("时间:"+dataBegin+"至"+dataEnd);

            XSSFRow row=sheet.getRow(3);
            row.getCell(2).setCellValue(businessDataVO.getTurnover());
            row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessDataVO.getNewUsers());

            row=sheet.getRow(4);
            row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
            row.getCell(4).setCellValue(businessDataVO.getUnitPrice());

            for(int i=0;i<30;++i){
                LocalDate time=dataBegin.plusDays(i);
                BusinessDataVO businessData=workspaceService.getBusinessData(LocalDateTime.of(time,LocalTime.MIN),LocalDateTime.of(time,LocalTime.MAX));
                row=sheet.getRow(i+7);
                row.getCell(1).setCellValue(time.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }

            //通过输出流将excel文件下载到客户端
            ServletOutputStream outputStream = response.getOutputStream();
            excel.write(outputStream);
            outputStream.close();
            excel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

}
