package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.ArticleTranslationMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.ArticleTranslation;
import com.ld.poetry.service.TranslationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 翻译服务实现类
 * 通过HTTP调用Python端的翻译服务
 */
@Service
@Slf4j
public class TranslationServiceImpl implements TranslationService {
    
    @Value("${PYTHON_SERVICE_URL:http://localhost:5000}")
    private String pythonServiceUrl;
    
    @Autowired
    private ArticleMapper articleMapper;
    
    @Autowired
    private ArticleTranslationMapper articleTranslationMapper;
    
    private final RestTemplate restTemplate;
    
    @Autowired
    private com.ld.poetry.utils.PrerenderClient prerenderClient;
    
    public TranslationServiceImpl() {
        this.restTemplate = new RestTemplate();
    }
    
    @Override
    public void translateAndSaveArticle(Integer articleId) {
        log.info("开始翻译并保存文章，ID: {}", articleId);
        
        try {
            // 1. 获取文章内容
            Article article = articleMapper.selectById(articleId);
            if (article == null) {
                log.warn("文章不存在，ID: {}", articleId);
                return;
            }
            
            // 检查文章是否有内容
            if (article.getArticleTitle() == null || article.getArticleTitle().trim().isEmpty() ||
                article.getArticleContent() == null || article.getArticleContent().trim().isEmpty()) {
                log.warn("文章标题或内容为空，跳过翻译，ID: {}", articleId);
                return;
            }
            
            // 2. 翻译标题
            String translatedTitle = translateText(article.getArticleTitle(), "zh", "en");
            if (translatedTitle == null || translatedTitle.trim().isEmpty()) {
                log.warn("标题翻译失败，使用原标题，文章ID: {}", articleId);
                translatedTitle = article.getArticleTitle();
            }
            
            // 3. 翻译内容（分段处理长文本）
            String translatedContent = translateLongText(article.getArticleContent(), "zh", "en");
            if (translatedContent == null || translatedContent.trim().isEmpty()) {
                log.warn("内容翻译失败，使用原内容，文章ID: {}", articleId);
                translatedContent = article.getArticleContent();
            }
            
            // 4. 保存或更新翻译结果（使用事务和重试机制处理并发）
            boolean success = saveOrUpdateTranslation(articleId, translatedTitle, translatedContent);
            
            if (success) {
                // 触发静态预渲染
                prerenderClient.renderArticle(articleId);
            }
            
        } catch (Exception e) {
            log.error("翻译文章失败，文章ID: {}, 错误: {}", articleId, e.getMessage(), e);
        }
    }
    
    /**
     * 保存或更新翻译结果，处理并发重复插入问题
     */
    private boolean saveOrUpdateTranslation(Integer articleId, String translatedTitle, String translatedContent) {
        // 使用重试机制处理并发问题
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 再次检查是否已存在翻译记录（防止并发情况下的重复插入）
                LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                           .eq(ArticleTranslation::getLanguage, "en");
                
                ArticleTranslation existingTranslation = articleTranslationMapper.selectOne(queryWrapper);
                
                if (existingTranslation != null) {
                    // 更新现有翻译
                    existingTranslation.setTitle(translatedTitle);
                    existingTranslation.setContent(translatedContent);
                    existingTranslation.setUpdateTime(LocalDateTime.now());
                    articleTranslationMapper.updateById(existingTranslation);
                    log.info("更新文章翻译成功，文章ID: {} (尝试第{}次)", articleId, attempt);
                    return true;
                } else {
                    // 创建新翻译
                    ArticleTranslation newTranslation = new ArticleTranslation();
                    newTranslation.setArticleId(articleId);
                    newTranslation.setLanguage("en");
                    newTranslation.setTitle(translatedTitle);
                    newTranslation.setContent(translatedContent);
                    newTranslation.setCreateTime(LocalDateTime.now());
                    newTranslation.setUpdateTime(LocalDateTime.now());
                    
                    try {
                        articleTranslationMapper.insert(newTranslation);
                        log.info("创建文章翻译成功，文章ID: {} (尝试第{}次)", articleId, attempt);
                        return true;
                    } catch (org.springframework.dao.DuplicateKeyException e) {
                        // 如果遇到重复键异常，说明在我们检查后有其他线程插入了记录
                        log.warn("检测到并发插入，尝试更新现有记录，文章ID: {} (尝试第{}次)", articleId, attempt);
                        if (attempt < maxRetries) {
                            Thread.sleep(100 * attempt); // 短暂等待后重试
                            continue;
                        } else {
                            // 最后一次尝试：直接尝试更新
                            existingTranslation = articleTranslationMapper.selectOne(queryWrapper);
                            if (existingTranslation != null) {
                                existingTranslation.setTitle(translatedTitle);
                                existingTranslation.setContent(translatedContent);
                                existingTranslation.setUpdateTime(LocalDateTime.now());
                                articleTranslationMapper.updateById(existingTranslation);
                                log.info("最终更新文章翻译成功，文章ID: {}", articleId);
                                return true;
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("翻译保存被中断，文章ID: {}", articleId);
                return false;
            } catch (Exception e) {
                log.error("保存翻译失败，文章ID: {}, 尝试第{}次, 错误: {}", articleId, attempt, e.getMessage());
                if (attempt == maxRetries) {
                    return false;
                }
            }
        }
        
        log.error("保存翻译最终失败，文章ID: {}", articleId);
        return false;
    }

    /**
     * 删除文章的所有翻译
     */
    public void refreshArticleTranslation(Integer articleId) {
        try {
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId);
            int rows = articleTranslationMapper.delete(queryWrapper);
            if (rows > 0) {
                translateAndSaveArticle(articleId); // 重新翻译并将在内部触发 prerender
            }
            log.info("删除文章翻译成功，文章ID: {}", articleId);
        } catch (Exception e) {
            log.error("删除文章翻译失败，文章ID: {}, 错误: {}", articleId, e.getMessage(), e);
        }
    }
    
    /**
     * 翻译长文本
     */
    private String translateLongText(String content, String sourceLang, String targetLang) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        
        try {
            // 直接翻译整个文本，保持上下文连贯性
            String translatedContent = translateText(content, sourceLang, targetLang);
            if (translatedContent != null && !translatedContent.trim().isEmpty()) {
                return translatedContent;
            } else {
                // 翻译失败时保留原文
                return content;
            }
            
        } catch (Exception e) {
            log.error("长文本翻译失败: {}", e.getMessage(), e);
            return content; // 翻译失败时返回原文
        }
    }
    
    @Override
    public Map<String, String> getArticleTranslation(Integer articleId, String language) {
        Map<String, String> result = new HashMap<>();
        
        if (articleId == null || language == null || language.trim().isEmpty()) {
            result.put("error", "文章ID或语言参数无效");
            return result;
        }
        
        try {
            // 查询文章翻译
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                       .eq(ArticleTranslation::getLanguage, language);
            
            ArticleTranslation translation = articleTranslationMapper.selectOne(queryWrapper);
            
            if (translation != null) {
                result.put("title", translation.getTitle() != null ? translation.getTitle() : "");
                result.put("content", translation.getContent() != null ? translation.getContent() : "");
                result.put("language", translation.getLanguage());
                result.put("status", "success");
                log.debug("成功获取文章翻译，文章ID: {}, 语言: {}", articleId, language);
            } else {
                result.put("error", "未找到对应语言的翻译");
                result.put("status", "not_found");
                log.warn("未找到文章翻译，文章ID: {}, 语言: {}", articleId, language);
            }
            
        } catch (Exception e) {
            log.error("获取文章翻译失败，文章ID: {}, 语言: {}, 错误: {}", articleId, language, e.getMessage(), e);
            result.put("error", "获取翻译失败: " + e.getMessage());
            result.put("status", "error");
        }
        
        return result;
    }
    
    @Override
    public String translateText(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        try {
            // 构建请求参数，设置默认中文转英文
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);
            requestBody.put("source_lang", sourceLang != null ? sourceLang : "zh");  // 默认中文
            requestBody.put("target_lang", targetLang != null ? targetLang : "en");  // 默认英文
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "poetize-java");
            headers.set("User-Agent", "poetize-java/1.0.0");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // 调用Python端翻译服务
            String url = pythonServiceUrl + "/api/translation/translate";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer code = (Integer) responseBody.get("code");
                
                if (code != null && code == 200) {
                    Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                    if (data != null) {
                        String translatedText = (String) data.get("translated_text");
                        if (translatedText != null && !translatedText.trim().isEmpty()) {
                            log.debug("翻译成功: {} -> {}", 
                                    text.length() > 50 ? text.substring(0, 50) + "..." : text,
                                    translatedText.length() > 50 ? translatedText.substring(0, 50) + "..." : translatedText);
                            return translatedText;
                        }
                    }
                } else {
                    String message = (String) responseBody.get("message");
                    log.warn("翻译失败: {}", message);
                }
            }
            
            log.warn("翻译服务返回异常响应，使用原文");
            return text; // 翻译失败时返回原文
            
        } catch (Exception e) {
            log.error("调用翻译服务失败: {}", e.getMessage(), e);
            return text; // 翻译失败时返回原文
        }
    }

    @Override
    public void deleteArticleTranslation(Integer articleId) {
        try {
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId);
            int rows = articleTranslationMapper.delete(queryWrapper);
            log.info("仅删除文章翻译，无重译，文章ID: {}, 行数: {}", articleId, rows);
        } catch (Exception e) {
            log.error("删除文章翻译失败，文章ID: {}", articleId, e);
        }
    }
} 