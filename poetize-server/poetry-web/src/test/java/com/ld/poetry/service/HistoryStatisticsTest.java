package com.ld.poetry.service;

import com.ld.poetry.dao.HistoryInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

/**
 * 访问统计测试类
 * 用于验证总访问量统计bug的修复效果
 */
@SpringBootTest
@Slf4j
public class HistoryStatisticsTest {

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private CacheService cacheService;

    /**
     * 测试数据库中的总访问量查询
     */
    @Test
    public void testHistoryCount() {
        try {
            log.info("开始测试数据库总访问量查询");
            
            // 直接查询数据库
            Long totalCount = historyInfoMapper.getHistoryCount();
            log.info("数据库总访问量: {}", totalCount);
            
            // 查询省份统计
            List<Map<String, Object>> provinceStats = historyInfoMapper.getHistoryByProvince();
            log.info("省份统计数据条数: {}", provinceStats != null ? provinceStats.size() : 0);
            if (provinceStats != null && !provinceStats.isEmpty()) {
                log.info("省份统计示例: {}", provinceStats.get(0));
            }
            
            // 查询IP统计
            List<Map<String, Object>> ipStats = historyInfoMapper.getHistoryByIp();
            log.info("IP统计数据条数: {}", ipStats != null ? ipStats.size() : 0);
            if (ipStats != null && !ipStats.isEmpty()) {
                log.info("IP统计示例: {}", ipStats.get(0));
            }
            
            // 查询24小时统计
            List<Map<String, Object>> hourStats = historyInfoMapper.getHistoryBy24Hour();
            log.info("24小时统计数据条数: {}", hourStats != null ? hourStats.size() : 0);
            
        } catch (Exception e) {
            log.error("测试数据库访问量查询失败", e);
            throw e;
        }
    }

    /**
     * 测试缓存获取
     */
    @Test
    public void testCacheStatistics() {
        try {
            log.info("开始测试缓存统计数据获取");
            
            // 获取缓存数据
            Map<String, Object> cachedStats = cacheService.getCachedIpHistoryStatisticsSafely();
            log.info("缓存中的总访问量: {}", cachedStats.get("ip_history_count"));
            log.info("缓存刷新标记: {}", cachedStats.get("_cache_refresh_needed"));
            
            // 检查缓存状态
            Object rawCache = cacheService.getCachedIpHistoryStatistics();
            if (rawCache == null) {
                log.warn("原始缓存为null");
            } else {
                log.info("原始缓存类型: {}", rawCache.getClass().getSimpleName());
            }
            
        } catch (Exception e) {
            log.error("测试缓存统计数据获取失败", e);
            throw e;
        }
    }

    /**
     * 手动刷新缓存测试
     */
    @Test
    public void testRefreshCache() {
        try {
            log.info("开始测试手动刷新缓存");
            
            // 清理缓存
            cacheService.evictIpHistoryStatistics();
            log.info("已清理缓存");
            
            // 重新获取缓存（应该触发自动刷新）
            Map<String, Object> stats = cacheService.getCachedIpHistoryStatisticsSafely();
            log.info("自动刷新后的总访问量: {}", stats.get("ip_history_count"));
            log.info("是否需要刷新: {}", stats.get("_cache_refresh_needed"));
            
        } catch (Exception e) {
            log.error("测试手动刷新缓存失败", e);
            throw e;
        }
    }
}