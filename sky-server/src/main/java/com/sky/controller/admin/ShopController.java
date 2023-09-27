package com.sky.controller.admin;


import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController ")
@Slf4j
@RequestMapping("/admin/shop")
@Api(tags = "店铺操作相关接口")
public class ShopController {

    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 设置店铺状态
     * @return
     */
    @PutMapping("/{status}")
    @ApiOperation("设置店铺状态")
    public Result setShopStatus(@PathVariable Integer status){
        log.info("设置店铺状态:{}",status);
        redisTemplate.opsForValue().set("SHOP_STATUS",status);
        return Result.success();
    }

    /**
     * 查询店铺状态
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("查询店铺状态")
    public Result<Integer> getShopStatus(){
        log.info("查询店铺状态:{}");
        Integer status= (Integer) redisTemplate.opsForValue().get("SHOP_STATUS");
        return Result.success(status);
    }
}
