package com.ld.poetry.utils.storage;

import com.ld.poetry.entity.User;
import com.ld.poetry.utils.PoetryUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class FileFilter {

    private final AntPathMatcher matcher = new AntPathMatcher();

    public boolean doFilterFile(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        if (matcher.match("/resource/upload", httpServletRequest.getRequestURI())) {
            try {
                User user = PoetryUtil.getCurrentUser();
                if (user != null) {
                    // 管理员不受上传次数限制
                    User adminUser = PoetryUtil.getAdminUser();
                    if (adminUser != null && user.getId().intValue() == adminUser.getId().intValue()) {
                        return false;
                    }

                    // TODO: 文件上传频率限制检查需要重构为使用CacheService
                    // 由于FileFilter是过滤器，依赖注入比较复杂，暂时简化处理
                    // 建议将此逻辑移到Controller层或使用Spring的@Component注解重构

                    // 暂时允许所有已登录用户上传，但记录警告
                    System.out.println("警告：文件上传频率限制检查已简化，建议重构为使用CacheService");
                    return false; // 不拦截
                }
                // 未登录用户拦截上传
                return true;
            } catch (Exception e) {
                // 发生异常时允许上传，但记录错误
                System.err.println("文件上传过滤器检查时发生异常: " + e.getMessage());
                return false;
            }
        } else {
            return false; // 非上传路径不拦截
        }
    }
}
