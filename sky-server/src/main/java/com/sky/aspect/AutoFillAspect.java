package com.sky.aspect;

import com.fasterxml.jackson.databind.ser.Serializers;
import com.sky.annotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    /**
     * 切入点
     */
    @Pointcut("execution(* com.sky.mapper.*.*(..))&& @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointCut(){}

    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint){
        log.info("开始进行公共字段自动填充....");
        MethodSignature methodSignature= (MethodSignature) joinPoint.getSignature();//方法签名对象
        AutoFill autoFIll=methodSignature.getMethod().getAnnotation(AutoFill.class);//注解对象
        OperationType operationType=autoFIll.value();//数据库对象

        Object[] objects=joinPoint.getArgs();
        if(objects==null||objects.length==0){
            return;
        }
        Object entiy=objects[0];
        LocalDateTime now= LocalDateTime.now();
        Long currentId= BaseContext.getCurrentId();

        if(operationType==OperationType.INSERT){
            try {
                Method setCreateTime=entiy.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME,LocalDateTime.class);
                Method setCreateUser=entiy.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER,Long.class);
                Method setUpdateTime=entiy.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME,LocalDateTime.class);
                Method setUpdateUser=entiy.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER,Long.class);

                setCreateTime.invoke(entiy,now);
                setCreateUser.invoke(entiy,currentId);
                setUpdateTime.invoke(entiy,now);
                setUpdateUser.invoke(entiy,currentId);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }

        }
        else if(operationType==OperationType.UPDATE){
            try {
                Method setUpdateTime=entiy.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME,LocalDateTime.class);
                Method setUpdateUser=entiy.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER,Long.class);

                setUpdateUser.invoke(entiy,currentId);
                setUpdateTime.invoke(entiy,now);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }
}
