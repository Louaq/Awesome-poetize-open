package com.ld.poetry.config;

import com.ld.poetry.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.IOException;

/**
 * WebP图片格式支持配置
 * 验证cwebp命令行工具是否可用
 * 由于移除了webp-imageio JNI依赖（避免Alpine环境下的兼容性问题），
 * 现在通过外部cwebp命令行工具实现WebP转换
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
            // 验证cwebp命令行工具是否可用
            log.info("验证cwebp命令行工具是否可用...");
            
            ProcessBuilder processBuilder = new ProcessBuilder("cwebp", "-version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("cwebp命令行工具可用，WebP转换功能正常");
            } else {
                log.warn("cwebp命令执行异常，退出代码: {}，WebP转换将使用备用方案", exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("cwebp版本检查被中断", e);
        } catch (IOException e) {
            log.warn("cwebp命令行工具不可用: {}，WebP转换将使用备用方案", e.getMessage());
        } catch (Exception e) {
            log.error("WebP支持初始化失败: {}，图像将使用标准格式", e.getMessage());
        }
    }
}