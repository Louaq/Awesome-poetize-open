package com.ld.poetry.service;

/**
 * Robots.txt服务接口
 * 负责生成和管理robots.txt文件
 */
public interface RobotsService {
    
    /**
     * 生成robots.txt内容
     * @return robots.txt内容字符串，如果生成失败返回null
     */
    String generateRobots();
    
    /**
     * 获取robots.txt内容（优先从缓存获取）
     * @return robots.txt内容字符串，如果获取失败返回null
     */
    String getRobots();
    
    /**
     * 清除robots.txt缓存
     */
    void clearRobotsCache();
    
    /**
     * 检查robots.txt是否需要更新
     * @return true 如果需要更新，false 否则
     */
    boolean needsUpdate();
}
