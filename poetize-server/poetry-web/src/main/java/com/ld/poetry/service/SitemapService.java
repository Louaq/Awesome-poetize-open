package com.ld.poetry.service;

import com.ld.poetry.entity.Article;

import java.util.List;
import java.util.Map;

/**
 * Sitemap管理服务接口
 * 
 * @author LeapYa
 * @since 2025-09-22
 */
public interface SitemapService {

    /**
     * 生成完整的sitemap.xml内容
     * 
     * @return sitemap.xml内容
     */
    String generateSitemap();

    /**
     * 生成sitemap.xml内容（不使用缓存）
     * 
     * @return sitemap.xml内容
     */
    String generateSitemapDirect();

    /**
     * 更新文章相关的sitemap条目
     * 
     * @param articleId 文章ID
     */
    void updateArticleSitemap(Integer articleId);

    /**
     * 内容变化时更新sitemap并推送到搜索引擎
     * 只在真正的内容变化时调用，不是缓存重建
     * 
     * @param reason 更新原因，用于日志记录
     */
    void updateSitemapAndPush(String reason);

    /**
     * 清除sitemap缓存
     */
    void clearSitemapCache();

    /**
     * 获取所有可见文章列表
     * 
     * @return 文章列表
     */
    List<Article> getVisibleArticles();

    /**
     * 获取网站基础URL
     * 
     * @return 网站URL
     */
    String getSiteBaseUrl();


    /**
     * 推送sitemap到搜索引擎
     * 
     * @return 推送结果
     */
    Map<String, Object> pingSitemapToSearchEngines();

    /**
     * 推送sitemap到搜索引擎（不使用缓存）
     * 
     * @return 推送结果
     */
    Map<String, Object> pingSitemapToSearchEnginesDirect();

    /**
     * 检查搜索引擎推送是否启用
     * 
     * @return 是否启用
     */
    boolean isSearchEnginePingEnabled();
}

