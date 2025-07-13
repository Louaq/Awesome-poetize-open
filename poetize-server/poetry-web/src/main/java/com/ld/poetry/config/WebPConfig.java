package com.ld.poetry.config;

import com.ld.poetry.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.util.Arrays;

/**
 * WebP图片格式支持配置
 * 初始化WebP编码器和解码器
 */
@Slf4j
@Configuration
public class WebPConfig {
    
    @Autowired
    private SysConfigService sysConfigService;

    @PostConstruct
    public void initWebPSupport() {
        // 从系统配置中读取WebP转换的启用状态
        String webpEnabledStr = sysConfigService.getConfigValueByKey("image.webp.enabled");
        boolean webpEnabled = webpEnabledStr == null || "true".equalsIgnoreCase(webpEnabledStr);
        
        if (!webpEnabled) {
            log.info("WebP支持已在系统配置中禁用");
            return;
        }
        
        try {
            // 尝试初始化WebP支持
            log.info("初始化WebP图像格式支持...");
            
            // 检查是否成功加载
            String[] writerFormats = ImageIO.getWriterFormatNames();
            String[] readerFormats = ImageIO.getReaderFormatNames();
            
            if (writerFormats != null && readerFormats != null) {
                log.info("系统支持的图像写入格式: {}", Arrays.toString(writerFormats));
                log.info("系统支持的图像读取格式: {}", Arrays.toString(readerFormats));
                
                boolean webpWriteSupported = Arrays.asList(writerFormats).contains("webp");
                boolean webpReadSupported = Arrays.asList(readerFormats).contains("webp");
                
                if (webpWriteSupported && webpReadSupported) {
                    log.info("WebP格式支持已成功初始化，可以进行WebP转换");
                } else {
                    log.warn("WebP格式支持未完全初始化，图像仍将使用标准格式");
                    if (!webpWriteSupported) {
                        log.warn("WebP编码器未找到，无法转换为WebP格式");
                    }
                    if (!webpReadSupported) {
                        log.warn("WebP解码器未找到，无法读取WebP格式图片");
                    }
                }
            } else {
                log.warn("无法获取系统支持的图像格式信息");
            }
        } catch (Exception e) {
            log.error("WebP支持初始化失败: {}，图像将使用标准格式", e.getMessage());
        }
    }
} 