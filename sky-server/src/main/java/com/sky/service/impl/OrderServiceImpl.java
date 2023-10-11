package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.github.pagehelper.Page;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WebSocketServer webSocketServer;
    @Value("${sky.shop.address}")
    private String shopAddress;
    @Value("${sky.baidu.ak}")
    private String ak;

    /**
     * 客户地址是否超过了配送距离
     */
    private void checkOutOfAddress(String address){
        Map map = new LinkedHashMap<String, String>();
        map.put("address",shopAddress);
        map.put("output", "json");
        map.put("ak", ak);
        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }
    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        //距离大于5km拒收
        checkOutOfAddress("湖南省株洲市天元区泰山西路88号");
        //处理业务异常 地址为空 购物车为空
        AddressBook addressBook=addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        ShoppingCart shoppingCart=ShoppingCart.builder()
                .userId(BaseContext.getCurrentId())
                .build();
        List<ShoppingCart> list=shoppingCartMapper.list(shoppingCart);
        if(list==null&&list.size()==0){
            throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }


        //向订单表插入一条数据
        Orders orders=new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setAddress(addressBook.getProvinceName()+addressBook.getCityName()+addressBook.getDistrictName());
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(BaseContext.getCurrentId());
        orderMapper.insert(orders);
        //向订单明细表插入若干条数据
        List<OrderDetail> orderDetails=new ArrayList<>();
        for (ShoppingCart cart : list) {
            OrderDetail orderDetail=new OrderDetail();
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetails.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetails);
        //清空购物车
        shoppingCartMapper.deleteById(BaseContext.getCurrentId());

        //封装返回
        OrderSubmitVO orderSubmitVO=OrderSubmitVO.builder()
                .id(orders.getId())
                .orderAmount(orders.getAmount())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .build();
        return orderSubmitVO;
    }
    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
//        // 当前登录用户id
//        Long userId = BaseContext.getCurrentId();
//        User user = userMapper.getByOpenid(userId);
//
//        //调用微信支付接口，生成预支付交易单
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "苍穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
//        JsonObject jsonObject=new JsonObject();
//        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
//            throw new OrderBusinessException("该订单已支付");
//        }

//        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
//        vo.setPackageStr(jsonObject.getString("package"));
        OrderPaymentVO orderPaymentVO=new OrderPaymentVO();
        return orderPaymentVO;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        Map map=new HashMap();
        map.put("type",1);//1表示订单提醒 2表示客户催单
        map.put("orderId",ordersDB.getId());
        map.put("content","订单号:"+outTradeNo);
        String json= JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);//通过webSocket向客户端浏览器推送消息

    }

    /**
     * 历史订单查询
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult pageQurey(int pageNum, int pageSize, Integer status) {
        //设置分页
        PageHelper.startPage(pageNum,pageSize);
        //包装DTO 获得不同状态的订单
        OrdersPageQueryDTO ordersPageQueryDTO=new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);
        Page<Orders> page=orderMapper.pageQurey(ordersPageQueryDTO);
        List<OrderVO> list=new ArrayList<>();
        //查询订单明细 封装成VO返回
        if(page!=null&&page.getTotal()>0){
            for (Orders orders : page) {
                List<OrderDetail> orderDetails=orderDetailMapper.getByOrderId(orders.getId());
                OrderVO orderVO=new OrderVO();
                BeanUtils.copyProperties(orders,orderVO);
                orderVO.setOrderDetailList(orderDetails);
                list.add(orderVO);
            }
        }

        return new PageResult(page.getTotal(),list);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO getOrderDetailById(Long id) {
        Orders orders=orderMapper.getById(id);
        List<OrderDetail> orderDetailList=orderDetailMapper.getByOrderId(id);
        OrderVO orderVO=new OrderVO();
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 取消订单
     * 业务逻辑是如果订单处于1 2状态 修改数据库中订单的信息
     * @param id
     */
    @Override
    public void deleteOrderById(Long id) {
        Orders orderDB=orderMapper.getById(id);
        if(orderDB==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if(orderDB.getStatus()>2){
             throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders=new Orders();
        orders.setId(orderDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 再来一单
     * 业务逻辑:将具体的菜品重新加入购物车
     * @param id
     */
    @Override
    public void repetOrderById(Long id) {
        Long userId=BaseContext.getCurrentId();
        List<OrderDetail> orderDetails=orderDetailMapper.getByOrderId(id);
        //将具体菜品转化成购物车对象
        List<ShoppingCart> shoppingCarts=orderDetails.stream().map(x->{
                ShoppingCart shoppingCart=new ShoppingCart();
                //将忽略id
                BeanUtils.copyProperties(x,shoppingCart,"id");
                shoppingCart.setUserId(userId);
                shoppingCart.setCreateTime(LocalDateTime.now());
                return shoppingCart;
                }).collect(Collectors.toList());
        shoppingCartMapper.insertBatch(shoppingCarts);
    }

    /**
     * 订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<Orders> ordersPage=orderMapper.pageQurey(ordersPageQueryDTO);
        List<OrderVO> orderVOList=getOrderVOList(ordersPage);
        return new PageResult(ordersPageQueryDTO.getPage(),orderVOList);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO orderStatisticsVO=new OrderStatisticsVO();
        orderStatisticsVO.setConfirmed(orderMapper.getConfirmedNumber());
        orderStatisticsVO.setDeliveryInProgress(orderMapper.getDeliveryInProgressNumber());
        orderStatisticsVO.setToBeConfirmed(orderMapper.getToBeConfirmed());
        return orderStatisticsVO;
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        Orders orders=orderMapper.getById(id);
        OrderVO orderVO=new OrderVO();
        BeanUtils.copyProperties(orders,orderVO);
        List<OrderDetail> orderDetailList=orderDetailMapper.getByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 接单
     * @param  ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders= Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        //根据id得到订单
        Orders orderDB=orderMapper.getById(ordersRejectionDTO.getId());
        //订单只有存在且状态为2（待接单）才可以拒单
        if(orderDB==null||orderDB.getStatus()!=2){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //拒单需要退款，根据订单id更新订单状态、拒单原因、取消时间
        Orders order=Orders.builder()
                .id(ordersRejectionDTO.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .build();
        orderMapper.update(order);
    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        // 根据id查询订单
        Orders ordersDB=orderMapper.getById(ordersCancelDTO.getId());
        if(ordersDB.getStatus()>=3){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
        Orders orders=Orders.builder()
                .id(ordersCancelDTO.getId())
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .cancelReason(ordersCancelDTO.getCancelReason())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 派送订单
     * @param id
     */
    @Override
    public void delivery(Long id) {
        // 根据id查询订单
        Orders orders=orderMapper.getById(id);
        // 校验订单是否存在，并且状态为3
        if(orders==null||orders.getStatus()!=3){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 更新订单状态,状态转为派送中
        Orders ordersnew=Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(ordersnew);
    }

    /**
     * 完成订单
     * @param id
     */
    @Override
    public void complete(Long id) {
        Orders ordersDB=orderMapper.getById(id);
        if(ordersDB==null||ordersDB.getStatus()!=4){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders=Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        Orders ordersDB=orderMapper.getById(id);
        if(ordersDB==null){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Map map=new HashMap();
        map.put("type",2);
        map.put("orderId",id);
        map.put("content","订单号:"+ordersDB.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }


    /**
     * Orders->OrderVO
     * @param ordersPage
     * @return
     */
    private List<OrderVO> getOrderVOList(Page<Orders> ordersPage) {
        List<OrderVO> orderVOList=new ArrayList<>();
        List<Orders> ordersList=ordersPage.getResult();
        if(!CollectionUtils.isEmpty(ordersList)){
            for (Orders orders : ordersList) {
                OrderVO orderVO=new OrderVO();
                BeanUtils.copyProperties(orders,orderVO);
                String orderDishs=getOrderDishstr(orders);
                orderVO.setOrderDishes(orderDishs);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    /**
     * get all Order dish name
     * @param orders
     * @return
     */
    private String getOrderDishstr(Orders orders) {
        //先获取菜品明细表中的数据
        List<OrderDetail> orderDetailList=orderDetailMapper.getByOrderId(orders.getId());
        //将数据转化成字符串类型
        List<String> stringList=orderDetailList.stream().map(x->{
            String  orderDish= x.getName()+"*"+x.getNumber()+";";
            return  orderDish;
        }).collect(Collectors.toList());
        //拼接返回
        return String.join("",stringList);
    }
}
