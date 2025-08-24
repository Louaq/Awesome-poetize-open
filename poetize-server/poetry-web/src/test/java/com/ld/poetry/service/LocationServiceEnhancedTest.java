package com.ld.poetry.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * LocationService增强功能测试
 * 验证淘宝IP服务和IP2Region备用方案的功能
 */
@Slf4j
@SpringBootTest
public class LocationServiceEnhancedTest {

    @Autowired
    private LocationService locationService;

    @Test
    public void testServiceStatus() {
        log.info("=== LocationService状态检查 ===");
        log.info("缓存大小: {}", locationService.getCacheSize());
        
        // 显示工厂模式下的提供者状态
        String providersStatus = locationService.getProvidersStatus();
        log.info("提供者状态:\n{}", providersStatus);
    }

    @Test
    public void testIpResolution() {
        log.info("=== IP解析功能测试 ===");
        
        // 测试各种类型的IP
        String[] testIps = {
            "8.8.8.8",                    // 谷歌DNS - 美国
            "114.114.114.114",            // 114DNS - 中国
            "2001:4860:4860::8888",       // 谷歌DNS IPv6 - 美国
            "2001:da8:200:900e::6",       // 中国教育网IPv6
            "::1",                        // IPv6本地回环
            "127.0.0.1",                  // IPv4本地回环
            "192.168.1.1",                // 内网IP
            "invalid-ip"                  // 无效IP
        };
        
        for (String ip : testIps) {
            log.info("测试IP: {}", ip);
            String result = locationService.getLocationByIp(ip);
            log.info("解析结果: {}", result);
            log.info("详细测试结果:\n{}", locationService.testIpResolution(ip));
            log.info("---");
        }
    }

    @Test
    public void testIPv6Support() {
        log.info("=== IPv6支持测试 ===");
        
        // 测试IPv6地址
        String[] ipv6Addresses = {
            "2001:4860:4860::8888",       // 谷歌DNS IPv6
            "2001:4860:4860::8844",       // 谷歌DNS IPv6 备用
            "2606:4700:4700::1111",       // Cloudflare DNS IPv6
            "2001:da8:200:900e::6",       // 中国教育网IPv6
            "::1",                        // IPv6本地回环
            "fe80::1",                    // IPv6链路本地地址
            "fc00::1"                     // IPv6唯一本地地址
        };
        
        for (String ipv6 : ipv6Addresses) {
            log.info("测试IPv6地址: {}", ipv6);
            String result = locationService.getLocationByIp(ipv6);
            log.info("解析结果: {}", result);
            
            // 详细测试信息
            String detailedResult = locationService.testIpResolution(ipv6);
            log.info("详细测试结果:\n{}", detailedResult);
            log.info("---");
        }
    }

    @Test
    public void testMixedIPAddresses() {
        log.info("=== 混合IP地址类型测试 ===");
        
        // 混合IPv4和IPv6地址测试
        String[] mixedIps = {
            "8.8.8.8",                    // IPv4
            "2001:4860:4860::8888",       // IPv6
            "114.114.114.114",            // IPv4中国
            "2001:da8:200:900e::6",       // IPv6中国
            "127.0.0.1",                  // IPv4本地
            "::1"                         // IPv6本地
        };
        
        for (String ip : mixedIps) {
            long startTime = System.currentTimeMillis();
            String result = locationService.getLocationByIp(ip);
            long endTime = System.currentTimeMillis();
            
            log.info("IP: {} -> 结果: {}, 耗时: {}ms", ip, result, endTime - startTime);
        }
        
        log.info("当前缓存大小: {}", locationService.getCacheSize());
    }

    @Test
    public void testTaobaoServiceAvailability() {
        log.info("=== 淘宝IP服务可用性测试 ===");
        
        // 测试几个不同的IP，验证淘宝IP服务是否正常
        String[] publicIps = {
            "8.8.8.8",           // 谷歌DNS
            "1.1.1.1",           // Cloudflare DNS
            "114.114.114.114",   // 114DNS
            "223.5.5.5"          // 阿里DNS
        };
        
        for (String ip : publicIps) {
            String result = locationService.getLocationByIp(ip);
            log.info("IP: {} -> 位置: {}", ip, result);
            
            // 验证结果不应该都是"未知"
            if (!"未知".equals(result)) {
                log.info("✅ 淘宝IP服务对 {} 解析成功", ip);
            } else {
                log.warn("⚠️ IP {} 解析结果为未知，可能服务有问题", ip);
            }
        }
    }

    @Test
    public void testRateLimiting() throws InterruptedException {
        log.info("=== 限流机制测试 ===");
        
        String testIp = "8.8.8.8";
        
        // 快速连续请求，测试限流
        for (int i = 0; i < 3; i++) {
            long startTime = System.currentTimeMillis();
            String result = locationService.getLocationByIp(testIp);
            long endTime = System.currentTimeMillis();
            
            log.info("第{}次请求: 耗时{}ms, 结果: {}", i + 1, endTime - startTime, result);
            
            if (i < 2) {
                Thread.sleep(500); // 短暂等待，测试限流
            }
        }
    }

    @Test
    public void testCacheFunction() {
        log.info("=== 缓存功能测试 ===");
        
        String testIp = "114.114.114.114";
        
        // 第一次请求
        log.info("第一次请求（应该调用API）");
        long startTime = System.currentTimeMillis();
        String result1 = locationService.getLocationByIp(testIp);
        long time1 = System.currentTimeMillis() - startTime;
        log.info("结果: {}, 耗时: {}ms", result1, time1);
        
        // 第二次请求（应该从缓存获取）
        log.info("第二次请求（应该从缓存获取）");
        startTime = System.currentTimeMillis();
        String result2 = locationService.getLocationByIp(testIp);
        long time2 = System.currentTimeMillis() - startTime;
        log.info("结果: {}, 耗时: {}ms", result2, time2);
        
        // 验证结果一致性和性能提升
        if (result1.equals(result2)) {
            log.info("✅ 缓存结果一致");
        } else {
            log.warn("❌ 缓存结果不一致");
        }
        
        if (time2 < time1) {
            log.info("✅ 缓存提升了响应速度");
        }
        
        log.info("当前缓存大小: {}", locationService.getCacheSize());
    }
    
    @Test
    public void testFactoryPatternIntegration() {
        log.info("=== 工厂模式集成测试 ===");
        
        // 测试提供者状态
        String providersStatus = locationService.getProvidersStatus();
        log.info("提供者状态:\n{}", providersStatus);
        
        // 测试不同类型的IP
        String[] testIps = {
            "8.8.8.8",                    // IPv4 - 谷歌DNS
            "114.114.114.114",            // IPv4 - 中国114DNS
            "2001:4860:4860::8888",       // IPv6 - 谷歌DNS
            "2001:da8:200:900e::6"        // IPv6 - 中国教育网
        };
        
        for (String ip : testIps) {
            log.info("测试工厂模式集成 - IP: {}", ip);
            
            // 使用详细测试方法，会显示工厂模式的状态
            String detailedResult = locationService.testIpResolution(ip);
            log.info("详细测试结果:\n{}", detailedResult);
            
            // 获取最终解析结果
            String result = locationService.getLocationByIp(ip);
            log.info("最终解析结果: {} -> {}", ip, result);
            log.info("---");
        }
        
        log.info("工厂模式优势：");
        log.info("1. 自动选择最优提供者");
        log.info("2. 支持自动降级策略");
        log.info("3. 统一管理多个提供者");
        log.info("4. 易于扩展和维护");
    }
}