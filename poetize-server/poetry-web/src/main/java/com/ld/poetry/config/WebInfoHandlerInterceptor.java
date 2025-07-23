package com.ld.poetry.config;

import com.alibaba.fastjson.JSON;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Slf4j
public class WebInfoHandlerInterceptor implements HandlerInterceptor {

    @Autowired
    private CacheService cacheService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            // 检查CacheService是否正确注入
            if (cacheService == null) {
                log.error("CacheService未正确注入到WebInfoHandlerInterceptor中");
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(JSON.toJSONString(PoetryResult.fail(CodeMsg.SYSTEM_REPAIR.getCode(), "系统初始化中，请稍后重试")));
                return false;
            }

            // 使用CacheService获取网站信息
            WebInfo webInfo = cacheService.getCachedWebInfo();

            if (webInfo == null || !webInfo.getStatus()) {
                log.debug("网站维护中或网站信息不存在，拦截请求: {}", request.getRequestURI());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(JSON.toJSONString(PoetryResult.fail(CodeMsg.SYSTEM_REPAIR.getCode(), CodeMsg.SYSTEM_REPAIR.getMsg())));
                return false;
            } else {
                log.debug("网站信息验证通过，允许请求: {}", request.getRequestURI());
                return true;
            }
        } catch (Exception e) {
            log.error("获取网站信息时发生错误，拦截请求: {}", request.getRequestURI(), e);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSON.toJSONString(PoetryResult.fail(CodeMsg.SYSTEM_REPAIR.getCode(), CodeMsg.SYSTEM_REPAIR.getMsg())));
            return false;
        }
    }
}
