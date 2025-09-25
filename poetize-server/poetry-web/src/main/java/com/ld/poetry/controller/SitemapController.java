package com.ld.poetry.controller;

import com.ld.poetry.service.SitemapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Sitemap控制器
 * 提供sitemap.xml访问接口
 * 
 * @author LeapYa
 * @since 2025-09-22
 */
@RestController
@Slf4j
public class SitemapController {

    @Autowired
    private SitemapService sitemapService;

    /**
     * 获取sitemap.xml
     * 访问路径：/sitemap.xml
     * 
     * @param request HTTP请求
     * @return sitemap.xml内容
     */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getSitemap(HttpServletRequest request) {
        try {
            log.info("收到sitemap.xml请求，来源IP: {}", getClientIpAddress(request));
            
            // 生成sitemap内容
            String sitemapContent = sitemapService.generateSitemap();
            
            if (StringUtils.hasText(sitemapContent)) {
                log.info("成功返回sitemap.xml，长度: {} 字符", sitemapContent.length());
                
                // 设置响应头
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_XML);
                headers.setCacheControl("max-age=3600"); // 缓存1小时
                headers.add("X-Robots-Tag", "noindex"); // 防止搜索引擎索引sitemap本身
                
                return ResponseEntity.ok()
                        .headers(headers)
                        .body(sitemapContent);
            } else {
                log.warn("Sitemap生成失败或已禁用");
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("获取sitemap.xml时发生错误", e);
            return ResponseEntity.internalServerError()
                    .body("<?xml version=\"1.0\" encoding=\"UTF-8\"?><error>Sitemap生成失败</error>");
        }
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor) && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp) && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}


