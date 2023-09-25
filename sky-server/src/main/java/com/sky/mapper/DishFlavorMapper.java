package com.sky.mapper;

import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishFlavorMapper {

    /**
     * 批量插入数据
     * @param flavors
     */
    void insertBatch(List<DishFlavor> flavors);

    /**
     *  根据主键删除口味
     * @param dishId
     */
    @Delete("delete from dish_flavor where dish_id=#{dishId}")
    void deleteByDishId(Long dishId);

    /**
     * 根据主键们删除口味
     * @param ids
     */
    void deleteByDishIds(List<Long> ids);

    /**
     * 根据id查询菜品
     * @param dishId
     * @return
     */
    @Select("select * from dish_flavor where dish_id=#{dishId}")
    List<DishFlavor> getByDishId(Long dishId);
}
