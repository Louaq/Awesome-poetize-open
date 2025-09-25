package com.ld.poetry.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.aop.ResourceCheck;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.entity.*;
import com.ld.poetry.enums.CommentTypeEnum;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.service.SysConfigService;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.mail.MailUtil;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import java.util.regex.Pattern;
import org.springframework.cache.annotation.Cacheable;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import com.ld.poetry.service.TranslationService;
import java.util.Map;
import java.util.HashMap;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.ld.poetry.utils.PrerenderClient;
import com.ld.poetry.utils.SmartSummaryGenerator;
import com.ld.poetry.utils.TextRankSummaryGenerator;
import com.ld.poetry.service.SummaryService;
import java.util.concurrent.ConcurrentHashMap;
import com.ld.poetry.service.SeoService;
import com.ld.poetry.event.ArticleSavedEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * <p>
 * 文章表 服务实现类
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@SuppressWarnings("unchecked")
@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private UserService userService;

    @Autowired
    private MailUtil mailUtil;

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Autowired
    private PrerenderClient prerenderClient;

    @Autowired
    private TranslationService translationService;
    
    @Autowired
    private SummaryService summaryService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SeoService seoService;

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public PoetryResult saveArticle(ArticleVO articleVO) {
        long startTime = System.currentTimeMillis();
        log.info("【Service性能监控】开始保存文章到数据库");
        
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && !StringUtils.hasText(articleVO.getPassword())) {
            return PoetryResult.fail("请设置文章密码！");
        }
        Article article = new Article();
        if (StringUtils.hasText(articleVO.getArticleCover())) {
            article.setArticleCover(articleVO.getArticleCover());
        }
        if (StringUtils.hasText(articleVO.getVideoUrl())) {
            article.setVideoUrl(articleVO.getVideoUrl());
        }
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && StringUtils.hasText(articleVO.getPassword())) {
            article.setPassword(articleVO.getPassword());
            article.setTips(articleVO.getTips());
        }
        article.setViewStatus(articleVO.getViewStatus());
        article.setCommentStatus(articleVO.getCommentStatus());
        article.setRecommendStatus(articleVO.getRecommendStatus());
        article.setSubmitToSearchEngine(articleVO.getSubmitToSearchEngine());
        article.setArticleTitle(articleVO.getArticleTitle());
        article.setArticleContent(articleVO.getArticleContent());
        
        // 立即生成AI摘要，确保预渲染时有正确的摘要
        if (StringUtils.hasText(articleVO.getArticleContent())) {
            try {
                String aiSummary = summaryService.generateSummarySync(articleVO.getArticleContent());
                article.setSummary(StringUtils.hasText(aiSummary) ? aiSummary : "");
                log.info("AI摘要生成成功，长度: {}", aiSummary.length());
            } catch (Exception e) {
                log.warn("AI摘要生成失败，使用空摘要: {}", e.getMessage());
                article.setSummary("");
            }
        } else {
            article.setSummary("");
        }
        
        article.setSortId(articleVO.getSortId());
        article.setLabelId(articleVO.getLabelId());
        
        // 增强的用户ID设置逻辑
        Integer userId = null;
        if (articleVO.getUserId() != null) {
            userId = articleVO.getUserId();
        } else {
            userId = PoetryUtil.getUserId();
            if (userId == null) {
                log.error("保存文章失败：无法获取用户ID");
                return PoetryResult.fail("无法确定文章作者，请重新登录后再试");
            }
        }
        
        article.setUserId(userId);
        
        long beforeSaveTime = System.currentTimeMillis();
        log.info("【Service性能监控】准备保存到数据库，耗时: {}ms", beforeSaveTime - startTime);
        
        boolean saved = save(article);
        if (!saved) {
            return PoetryResult.fail("保存文章失败");
        }
        
        long afterSaveTime = System.currentTimeMillis();
        log.info("【Service性能监控】数据库保存完成，耗时: {}ms", afterSaveTime - beforeSaveTime);
        
        // 将文章ID回填到VO对象
        articleVO.setId(article.getId());

        // 异步发送订阅邮件，避免阻塞保存操作
        if (articleVO.getViewStatus()) {
            final Integer labelId = articleVO.getLabelId();
            final String articleTitle = articleVO.getArticleTitle();
            
            new Thread(() -> {
                long mailStartTime = System.currentTimeMillis();
                log.info("【Service性能监控】开始异步发送订阅邮件");
                
                try {
                    List<User> users = userService.lambdaQuery().select(User::getEmail, User::getSubscribe).eq(User::getUserStatus, PoetryEnum.STATUS_ENABLE.getCode()).list();
                    List<String> emails = users.stream().filter(u -> {
                        List<Integer> sub = JSON.parseArray(u.getSubscribe(), Integer.class);
                        return !CollectionUtils.isEmpty(sub) && sub.contains(labelId);
                    }).map(User::getEmail).collect(Collectors.toList());

                    if (!CollectionUtils.isEmpty(emails)) {
                        LambdaQueryChainWrapper<Label> wrapper = new LambdaQueryChainWrapper<>(labelMapper);
                        Label label = wrapper.select(Label::getLabelName).eq(Label::getId, labelId).one();
                        String text = getSubscribeMail(label.getLabelName(), articleTitle);
                        WebInfo webInfo = cacheService.getCachedWebInfo();
                        mailUtil.sendMailMessage(emails, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！", text);
                        
                        long mailEndTime = System.currentTimeMillis();
                        log.info("【Service性能监控】订阅邮件发送完成，发送给{}个用户，耗时: {}ms", emails.size(), mailEndTime - mailStartTime);
                    } else {
                        log.info("【Service性能监控】无需发送订阅邮件（无匹配用户）");
                    }
                } catch (Exception e) {
                    long mailErrorTime = System.currentTimeMillis();
                    log.error("【Service性能监控】订阅邮件发送失败，耗时: {}ms，错误: {}", mailErrorTime - mailStartTime, e.getMessage(), e);
                }
            }).start();
        }
        
        // 异步翻译完成后内部会触发预渲染
        new Thread(() -> translationService.translateAndSaveArticle(article.getId())).start();
        
        // 手动清除Redis中的分类文章列表缓存，确保首页能显示新文章
        try {
            cacheService.evictSortArticleList();
            log.info("已清除分类文章列表缓存，确保首页显示最新文章");
        } catch (Exception e) {
            log.error("清除分类文章列表缓存失败: {}", e.getMessage(), e);
        }
        
        // 发布文章保存事件，触发预渲染更新（在事务提交后执行）
        try {
            eventPublisher.publishEvent(new ArticleSavedEvent(article.getId(), article.getSortId(), 
                                                            article.getViewStatus(), "CREATE"));
            log.info("已发布文章保存事件，将触发首页预渲染更新");
        } catch (Exception e) {
            log.error("发布文章保存事件失败: {}", e.getMessage(), e);
        }
        
        long endTime = System.currentTimeMillis();
        log.info("【Service性能监控】saveArticle方法总耗时: {}ms", endTime - startTime);
        
        return PoetryResult.success(article.getId());
    }

    /**
     * 异步保存文章（快速响应版本）
     */
    @Override
    public PoetryResult<String> saveArticleAsync(ArticleVO articleVO) {
        // 调用重载方法，使用默认参数
        return saveArticleAsync(articleVO, false, null);
    }

    /**
     * 异步保存文章（快速响应版本，支持翻译参数）
     */
    @Override
    public PoetryResult<String> saveArticleAsync(ArticleVO articleVO, boolean skipAiTranslation, Map<String, String> pendingTranslation) {
        // 生成任务ID
        String taskId = "article_save_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        
        // 基础验证
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && !StringUtils.hasText(articleVO.getPassword())) {
            return PoetryResult.fail("请设置文章密码！");
        }
        
        Integer userId = PoetryUtil.getUserId();
        if (userId == null) {
            return PoetryResult.fail("无法确定文章作者，请重新登录后再试");
        }
        
        // 在主线程中获取用户信息，避免异步线程中无法访问RequestContext
        String currentUsername = null;
        try {
            currentUsername = PoetryUtil.getUsername();
        } catch (Exception e) {
            log.warn("【异步保存】无法获取当前用户名，使用默认值: {}", e.getMessage());
            currentUsername = "System";
        }
        final String finalUsername = currentUsername;
        
        // 初始化保存状态
        ArticleSaveStatus initialStatus = new ArticleSaveStatus(taskId, "processing", "正在保存文章...", null);
        ARTICLE_SAVE_STATUS.put(taskId, initialStatus);
        log.info("【异步保存】初始化保存状态，任务ID: {}, 当前任务总数: {}", taskId, ARTICLE_SAVE_STATUS.size());
        
        // 异步执行保存
        new Thread(() -> {
            try {
                log.info("【异步保存】开始异步保存文章，任务ID: {}", taskId);
                
                // 创建文章对象
                Article article = new Article();
                if (StringUtils.hasText(articleVO.getArticleCover())) {
                    article.setArticleCover(articleVO.getArticleCover());
                }
                if (StringUtils.hasText(articleVO.getVideoUrl())) {
                    article.setVideoUrl(articleVO.getVideoUrl());
                }
                if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && StringUtils.hasText(articleVO.getPassword())) {
                    article.setPassword(articleVO.getPassword());
                    article.setTips(articleVO.getTips());
                }
                article.setViewStatus(articleVO.getViewStatus());
                article.setCommentStatus(articleVO.getCommentStatus());
                article.setRecommendStatus(articleVO.getRecommendStatus());
                article.setSubmitToSearchEngine(articleVO.getSubmitToSearchEngine());
                article.setArticleTitle(articleVO.getArticleTitle());
                article.setArticleContent(articleVO.getArticleContent());
                article.setSortId(articleVO.getSortId());
                article.setLabelId(articleVO.getLabelId());
                article.setUserId(userId);
                
                // 设置必要的基础字段
                article.setCreateTime(LocalDateTime.now());
                article.setUpdateTime(LocalDateTime.now());
                article.setUpdateBy(finalUsername);
                
                // 更新状态：正在生成摘要
                updateSaveStatus(taskId, "processing", "正在生成AI摘要...");
                
                // 生成AI摘要
                if (StringUtils.hasText(articleVO.getArticleContent())) {
                    try {
                        log.info("【异步保存】开始生成AI摘要，任务ID: {}, 文章内容长度: {}", taskId, articleVO.getArticleContent().length());
                        String aiSummary = summaryService.generateSummarySync(articleVO.getArticleContent());
                        article.setSummary(StringUtils.hasText(aiSummary) ? aiSummary : "");
                        log.info("【异步保存】AI摘要生成成功，任务ID: {}, 摘要长度: {}", taskId, aiSummary != null ? aiSummary.length() : 0);
                    } catch (Exception e) {
                        log.error("【异步保存】AI摘要生成失败，任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
                        article.setSummary("");
                    }
                } else {
                    log.info("【异步保存】文章内容为空，跳过AI摘要生成，任务ID: {}", taskId);
                    article.setSummary("");
                }
                
                // 更新状态：正在保存到数据库
                updateSaveStatus(taskId, "processing", "正在保存到数据库...");
                
                // 保存到数据库
                log.info("【异步保存】开始保存到数据库，任务ID: {}", taskId);
                boolean saved = save(article);
                if (!saved) {
                    log.error("【异步保存】数据库保存失败，任务ID: {}", taskId);
                    updateSaveStatus(taskId, "failed", "数据库保存失败");
                    return;
                }
                log.info("【异步保存】数据库保存成功，任务ID: {}, 文章ID: {}", taskId, article.getId());

                // 手动清除Redis中的分类文章列表缓存，确保首页能显示新文章
                try {
                    cacheService.evictSortArticleList();
                    log.info("【异步保存】已清除分类文章列表缓存，确保首页显示最新文章，任务ID: {}", taskId);
                } catch (Exception e) {
                    log.error("【异步保存】清除分类文章列表缓存失败，任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
                }
                
                // 发布文章保存事件，触发预渲染更新（在事务提交后执行）
                try {
                    eventPublisher.publishEvent(new ArticleSavedEvent(article.getId(), article.getSortId(), 
                                                                    article.getViewStatus(), "CREATE"));
                    log.info("【异步保存】已发布文章保存事件，将触发首页预渲染更新，任务ID: {}", taskId);
                } catch (Exception e) {
                    log.error("【异步保存】发布文章保存事件失败，任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
                }
                
                // 更新状态：后台处理中
                updateSaveStatus(taskId, "processing", "文章已保存，正在处理邮件通知和翻译...");
                
                // 异步发送订阅邮件
                if (articleVO.getViewStatus()) {
                    try {
                        log.info("【异步保存】开始发送订阅邮件，任务ID: {}", taskId);
                        sendSubscriptionEmails(articleVO.getLabelId(), articleVO.getArticleTitle());
                        log.info("【异步保存】订阅邮件发送完成，任务ID: {}", taskId);
                    } catch (Exception e) {
                        log.error("【异步保存】订阅邮件发送失败，任务ID: {}", taskId, e);
                    }
                } else {
                    log.info("【异步保存】文章不可见，跳过订阅邮件发送，任务ID: {}", taskId);
                }
                
                // 异步翻译（翻译完成后内部会触发预渲染）
                try {
                    log.info("【异步保存】开始文章翻译，任务ID: {}, 跳过AI翻译: {}, 有暂存翻译: {}",
                            taskId, skipAiTranslation, pendingTranslation != null && !pendingTranslation.isEmpty());
                    translationService.translateAndSaveArticle(article.getId(), skipAiTranslation, pendingTranslation);
                    log.info("【异步保存】文章翻译完成（包含预渲染），任务ID: {}", taskId);
                } catch (Exception e) {
                    log.error("【异步保存】文章翻译失败，任务ID: {}", taskId, e);
                }
                // 更新状态：翻译完成，正在处理sitemap更新和SEO推送
                updateSaveStatus(taskId, "processing", "文章已保存，正在处理sitemap更新和SEO推送...");

                // 注意：sitemap更新已经通过 ArticleEventListener.updateSitemapAsync() 处理
                // 无需在这里重复处理sitemap更新
                log.info("【异步保存】文章ID {} 保存成功，sitemap更新将通过事件监听器自动处理，任务ID: {}", article.getId(), taskId);
                
                // 如果需要推送至搜索引擎且文章可见，异步处理
                if (Boolean.TRUE.equals(articleVO.getSubmitToSearchEngine())) {
                    log.info("【异步保存】文章ID {} 标记为需要推送至搜索引擎，开始异步处理，任务ID: {}", article.getId(), taskId);
                    
                    // 异步执行SEO推送，避免阻塞用户操作
                    new Thread(() -> {
                        try {
                            boolean seoResult = seoService.submitToSearchEngines(article.getId());
                            log.info("【异步保存】文章ID {} 搜索引擎推送完成，结果: {}，任务ID: {}", article.getId(), seoResult ? "成功" : "失败", taskId);
                        } catch (Exception e) {
                            log.error("【异步保存】搜索引擎推送失败，但不影响文章保存，文章ID: " + article.getId() + ", 任务ID: " + taskId, e);
                        }
                    }).start();
                } else {
                    log.info("【异步保存】文章ID {} 未标记为需要推送至搜索引擎，任务ID: {}", article.getId(), taskId);
                }
                
                // 最终成功状态
                updateSaveStatus(taskId, "success", "文章保存成功！AI摘要已生成", article.getId());
                log.info("【异步保存】文章保存完成，任务ID: {}, 文章ID: {}", taskId, article.getId());
                
            } catch (Exception e) {
                log.error("【异步保存】文章保存失败，任务ID: {}", taskId, e);
                updateSaveStatus(taskId, "failed", "保存失败：" + e.getMessage());
            }
        }).start();
        
        return PoetryResult.success(taskId);
    }
    
    /**
     * 查询文章保存状态
     */
    @Override
    public PoetryResult<ArticleSaveStatus> getArticleSaveStatus(String taskId) {
        // 轮询期间的日志降级为DEBUG，减少噪音
        log.debug("【异步保存】查询保存状态，任务ID: {}", taskId);
        
        ArticleSaveStatus status = ARTICLE_SAVE_STATUS.get(taskId);
        if (status == null) {
            log.warn("【异步保存】任务不存在，任务ID: {}, 当前存在的任务: {}", taskId, ARTICLE_SAVE_STATUS.keySet());
            return PoetryResult.fail("任务不存在或已过期");
        }
        
        // 只在状态变化或首次查询时输出详细日志
        if (log.isDebugEnabled()) {
            log.debug("【异步保存】找到状态 - 任务ID: {}, 状态: {}, 消息: {}, 文章ID: {}, 最后更新时间: {}", 
                    taskId, status.getStatus(), status.getMessage(), status.getArticleId(), status.getLastUpdateTime());
        }
        
        // 如果任务完成（成功或失败），5分钟后自动清理
        if (("success".equals(status.getStatus()) || "failed".equals(status.getStatus())) 
            && System.currentTimeMillis() - status.getLastUpdateTime() > 5 * 60 * 1000) {
            log.info("【异步保存】任务已过期，清理任务，任务ID: {}", taskId);
            ARTICLE_SAVE_STATUS.remove(taskId);
            return PoetryResult.fail("任务已过期");
        }
        
        return PoetryResult.success(status);
    }
    

    
    // 文章保存状态缓存（内存级别，重启后清空）
    private static final Map<String, ArticleSaveStatus> ARTICLE_SAVE_STATUS = new ConcurrentHashMap<>();
    
    /**
     * 更新保存状态
     */
    private void updateSaveStatus(String taskId, String status, String message) {
        updateSaveStatus(taskId, status, message, null);
    }
    
    private void updateSaveStatus(String taskId, String status, String message, Integer articleId) {
        ArticleSaveStatus saveStatus = ARTICLE_SAVE_STATUS.get(taskId);
        if (saveStatus != null) {
            log.info("【异步保存】更新状态 - 任务ID: {}, 状态: {}, 消息: {}, 文章ID: {}", taskId, status, message, articleId);
            saveStatus.setStatus(status);
            saveStatus.setMessage(message);
            saveStatus.setArticleId(articleId);
            saveStatus.setLastUpdateTime(System.currentTimeMillis());
        } else {
            log.warn("【异步保存】状态更新失败 - 任务ID不存在: {}, 尝试更新状态: {}, 消息: {}", taskId, status, message);
        }
    }
    
    /**
     * 发送订阅邮件（从原方法提取）
     */
    private void sendSubscriptionEmails(Integer labelId, String articleTitle) {
        try {
            List<User> users = userService.lambdaQuery().select(User::getEmail, User::getSubscribe).eq(User::getUserStatus, PoetryEnum.STATUS_ENABLE.getCode()).list();
            List<String> emails = users.stream().filter(u -> {
                List<Integer> sub = JSON.parseArray(u.getSubscribe(), Integer.class);
                return !CollectionUtils.isEmpty(sub) && sub.contains(labelId);
            }).map(User::getEmail).collect(Collectors.toList());

            if (!CollectionUtils.isEmpty(emails)) {
                LambdaQueryChainWrapper<Label> wrapper = new LambdaQueryChainWrapper<>(labelMapper);
                Label label = wrapper.select(Label::getLabelName).eq(Label::getId, labelId).one();
                String text = getSubscribeMail(label.getLabelName(), articleTitle);
                WebInfo webInfo = cacheService.getCachedWebInfo();
                mailUtil.sendMailMessage(emails, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！", text);
                log.info("订阅邮件发送完成，发送给{}个用户", emails.size());
            }
        } catch (Exception e) {
            log.error("订阅邮件发送失败", e);
        }
    }
    
    /**
     * 文章保存状态类
     */
    public static class ArticleSaveStatus {
        private String taskId;
        private String status; // processing, success, failed
        private String message;
        private Integer articleId;
        private long lastUpdateTime;
        
        public ArticleSaveStatus(String taskId, String status, String message, Integer articleId) {
            this.taskId = taskId;
            this.status = status;
            this.message = message;
            this.articleId = articleId;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        // getters and setters
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public Integer getArticleId() { return articleId; }
        public void setArticleId(Integer articleId) { this.articleId = articleId; }
        
        public long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    }

    private String getSubscribeMail(String labelName, String articleTitle) {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        String webName = (webInfo == null ? "POETIZE" : webInfo.getWebName());
        
        // 从数据库获取订阅模板
        String subscribeTemplate = sysConfigService.getConfigValueByKey("user.subscribe.format");
        if (subscribeTemplate == null || subscribeTemplate.trim().isEmpty()) {
            // 如果数据库中没有配置，使用默认模板
            subscribeTemplate = "【POETIZE】您订阅的专栏【%s】新增一篇文章：%s。";
            log.warn("数据库中未找到订阅模板配置，使用默认模板");
        }
        
        log.info("使用订阅邮件模板: {}", subscribeTemplate); // 添加日志记录使用的模板
        
        return String.format(mailUtil.getMailText(),
                webName,
                String.format(MailUtil.notificationMail, PoetryUtil.getAdminUser().getUsername()),
                PoetryUtil.getAdminUser().getUsername(),
                String.format(subscribeTemplate, labelName, articleTitle),
                "",
                webName);
    }

    @Override
    public PoetryResult deleteArticle(Integer id) {
        Integer userId = PoetryUtil.getUserId();
        
        // 检查文章是否存在
        Article article = lambdaQuery().eq(Article::getId, id).one();
        if (article == null) {
            return PoetryResult.fail("文章不存在！");
        }
        
        // 如果是文章作者或管理员，允许删除
        boolean canDelete = article.getUserId().equals(userId) || PoetryUtil.isBoss();
        if (!canDelete) {
            return PoetryResult.fail("没有权限删除此文章！");
        }
        
        // 删除文章
        removeById(id);

        // 使用Redis缓存清理替换PoetryCache
        cacheService.evictArticleRelatedCache(id);

        return PoetryResult.success();
    }

    @Override
    public PoetryResult updateArticle(ArticleVO articleVO) {
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && !StringUtils.hasText(articleVO.getPassword())) {
            return PoetryResult.fail("请设置文章密码！");
        }

        Integer userId = PoetryUtil.getUserId();
        
        LambdaUpdateChainWrapper<Article> updateChainWrapper = lambdaUpdate()
                .eq(Article::getId, articleVO.getId())
                .eq(Article::getUserId, userId)
                .set(Article::getLabelId, articleVO.getLabelId())
                .set(Article::getSortId, articleVO.getSortId())
                .set(Article::getArticleTitle, articleVO.getArticleTitle())
                .set(Article::getUpdateBy, PoetryUtil.getUsername())
                .set(Article::getUpdateTime, LocalDateTime.now())
                .set(Article::getVideoUrl, StringUtils.hasText(articleVO.getVideoUrl()) ? articleVO.getVideoUrl() : null)
                .set(Article::getArticleContent, articleVO.getArticleContent());

        if (StringUtils.hasText(articleVO.getArticleCover())) {
            updateChainWrapper.set(Article::getArticleCover, articleVO.getArticleCover());
        }
        if (articleVO.getCommentStatus() != null) {
            updateChainWrapper.set(Article::getCommentStatus, articleVO.getCommentStatus());
        }
        if (articleVO.getRecommendStatus() != null) {
            updateChainWrapper.set(Article::getRecommendStatus, articleVO.getRecommendStatus());
        }
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && StringUtils.hasText(articleVO.getPassword())) {
            updateChainWrapper.set(Article::getPassword, articleVO.getPassword());
            updateChainWrapper.set(StringUtils.hasText(articleVO.getTips()), Article::getTips, articleVO.getTips());
        }
        if (articleVO.getViewStatus() != null) {
            updateChainWrapper.set(Article::getViewStatus, articleVO.getViewStatus());
        }
        if (articleVO.getSubmitToSearchEngine() != null) {
            updateChainWrapper.set(Article::getSubmitToSearchEngine, articleVO.getSubmitToSearchEngine());
        }
        // 同步更新摘要（如果内容有变化）
        if (StringUtils.hasText(articleVO.getArticleContent())) {
            try {
                String aiSummary = summaryService.generateSummarySync(articleVO.getArticleContent());
                updateChainWrapper.set(Article::getSummary, StringUtils.hasText(aiSummary) ? aiSummary : "");
                log.info("文章更新：AI摘要生成成功，长度: {}", aiSummary.length());
            } catch (Exception e) {
                log.warn("文章更新：AI摘要生成失败，保持原摘要: {}", e.getMessage());
            }
        }
        
        updateChainWrapper.update();
        
        // 手动清除Redis中的分类文章列表缓存，确保首页能显示更新后的文章
        try {
            cacheService.evictSortArticleList();
            log.info("已清除分类文章列表缓存，确保首页显示更新后的文章");
        } catch (Exception e) {
            log.error("清除分类文章列表缓存失败: {}", e.getMessage(), e);
        }

        // 发布文章更新事件，触发预渲染更新（在事务提交后执行）
        try {
            eventPublisher.publishEvent(new ArticleSavedEvent(articleVO.getId(), articleVO.getSortId(), 
                                                            articleVO.getViewStatus(), "UPDATE"));
            log.info("已发布文章更新事件，将触发首页预渲染更新");
        } catch (Exception e) {
            log.error("发布文章更新事件失败: {}", e.getMessage(), e);
        }

        // 更新后重新翻译，TranslationService 内部完毕后预渲染
        new Thread(() -> translationService.refreshArticleTranslation(articleVO.getId())).start();

        return PoetryResult.success();
    }

    @Override
    public PoetryResult<Page> listArticle(BaseRequestVO baseRequestVO) {
        List<Integer> ids = null;
        List<List<Integer>> idList = null;
        if (StringUtils.hasText(baseRequestVO.getArticleSearch())) {
            idList = commonQuery.getArticleIds(baseRequestVO.getArticleSearch());
            ids = idList.stream().flatMap(Collection::stream).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(ids)) {
                baseRequestVO.setRecords(new ArrayList<>());
                return PoetryResult.success(baseRequestVO);
            }
        }

        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.in(!CollectionUtils.isEmpty(ids), Article::getId, ids);
        lambdaQuery.like(StringUtils.hasText(baseRequestVO.getSearchKey()), Article::getArticleTitle, baseRequestVO.getSearchKey());
        lambdaQuery.eq(baseRequestVO.getRecommendStatus() != null && baseRequestVO.getRecommendStatus(), Article::getRecommendStatus, PoetryEnum.STATUS_ENABLE.getCode());
        
        // 添加对可见文章的过滤，确保预渲染和前端只获取可见的文章
        lambdaQuery.eq(Article::getViewStatus, true);

        if (baseRequestVO.getLabelId() != null) {
            lambdaQuery.eq(Article::getLabelId, baseRequestVO.getLabelId());
        } else if (baseRequestVO.getSortId() != null) {
            lambdaQuery.eq(Article::getSortId, baseRequestVO.getSortId());
        }

        lambdaQuery.orderByDesc(Article::getCreateTime);

        Page<Article> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        lambdaQuery.page(page);

        List<Article> records = page.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            List<ArticleVO> articles = new ArrayList<>();
            List<ArticleVO> titles = new ArrayList<>();
            List<ArticleVO> contents = new ArrayList<>();

            for (Article article : records) {
                // 保存原始内容用于显示前的高亮处理
                String originalContent = article.getArticleContent();
                String originalTitle = article.getArticleTitle();
                
                ArticleVO articleVO = buildArticleVO(article, false);
                
                // 直接使用数据库中存储的摘要（仅在非搜索场景下设置）
                if (!StringUtils.hasText(baseRequestVO.getArticleSearch()) && StringUtils.hasText(article.getSummary())) {
                    articleVO.setSummary(article.getSummary());
                }
                
                articleVO.setHasVideo(StringUtils.hasText(articleVO.getVideoUrl()));
                articleVO.setPassword(null);
                articleVO.setVideoUrl(null);
                
                // 如果是搜索结果，进行高亮处理
                if (StringUtils.hasText(baseRequestVO.getArticleSearch())) {
                    String searchText = baseRequestVO.getArticleSearch();
                    
                    // 检测是否为正则表达式搜索
                    boolean isRegexSearch = searchText.startsWith("/") && searchText.endsWith("/") && searchText.length() > 2;
                    String actualSearchText = isRegexSearch ? searchText.substring(1, searchText.length() - 1) : searchText;
                    
                    // 使用高亮标签进行处理
                    String highlightStart = "<span class='search-highlight' style='color: var(--lightGreen); font-weight: bold;'>";
                    String highlightEnd = "</span>";
                    
                    // 检查原文是否匹配
                    boolean originalTitleMatches = false;
                    boolean originalContentMatches = false;
                    
                    if (isRegexSearch) {
                        Pattern pattern = Pattern.compile(actualSearchText, Pattern.CASE_INSENSITIVE);
                        originalTitleMatches = originalTitle != null && pattern.matcher(originalTitle).find();
                        originalContentMatches = originalContent != null && pattern.matcher(originalContent).find();
                    } else {
                        originalTitleMatches = originalTitle != null && originalTitle.toLowerCase().contains(searchText.toLowerCase());
                        originalContentMatches = originalContent != null && originalContent.toLowerCase().contains(searchText.toLowerCase());
                    }
                    
                    boolean originalMatches = originalTitleMatches || originalContentMatches;
                    
                    // 检查翻译是否匹配
                    String matchedLanguage = commonQuery.getMatchedTranslationLanguage(articleVO.getId(), searchText);
                    boolean translationMatches = matchedLanguage != null;
                    
                    if (originalMatches && translationMatches) {
                        // 原文和翻译都匹配：优先显示原文，但标记翻译也匹配
                        articleVO.setIsTranslationMatch(false); // 优先显示原文
                        articleVO.setMatchedLanguage(matchedLanguage); // 保存匹配的翻译语言信息
                        
                        // 可以添加一个字段标识翻译也匹配了
                        articleVO.setHasTranslationMatch(true);
                        
                        log.debug("文章ID {} 原文和翻译都匹配搜索词，优先显示原文，翻译语言: {}", articleVO.getId(), matchedLanguage);
                        
                    } else if (originalMatches) {
                        // 只有原文匹配
                        articleVO.setIsTranslationMatch(false);
                        log.debug("文章ID {} 仅原文匹配搜索词", articleVO.getId());
                        
                    } else if (translationMatches) {
                        // 只有翻译匹配
                        Map<String, String> matchedTranslation = commonQuery.getMatchedTranslation(articleVO.getId(), searchText, matchedLanguage);
                        if (matchedTranslation != null) {
                            articleVO.setIsTranslationMatch(true);
                            articleVO.setMatchedLanguage(matchedLanguage);
                            
                            // 高亮翻译标题和内容，并替换原文显示
                            String translatedTitle = matchedTranslation.get("title");
                            String translatedContent = matchedTranslation.get("content");
                            
                            if (translatedTitle != null) {
                                String highlightedTitle;
                                if (isRegexSearch) {
                                    highlightedTitle = StringUtil.highlightTextWithRegex(translatedTitle, actualSearchText, highlightStart, highlightEnd);
                                } else {
                                    highlightedTitle = StringUtil.highlightText(translatedTitle, searchText, highlightStart, highlightEnd);
                                }
                                articleVO.setArticleTitle(highlightedTitle); // 替换显示的标题
                            }
                            if (translatedContent != null) {
                                // 智能截取包含搜索关键词的内容片段
                                String contentSnippet = getContentSnippetWithKeyword(translatedContent, searchText, CommonConst.SUMMARY);
                                String highlightedContent;
                                if (isRegexSearch) {
                                    highlightedContent = StringUtil.highlightTextWithRegex(contentSnippet, actualSearchText, highlightStart, highlightEnd);
                                } else {
                                    highlightedContent = StringUtil.highlightText(contentSnippet, searchText, highlightStart, highlightEnd);
                                }
                                articleVO.setArticleContent(highlightedContent); // 替换显示的内容
                            }
                            
                            log.debug("文章ID {} 仅翻译匹配搜索词，显示翻译内容，语言: {}", articleVO.getId(), matchedLanguage);
                        }
                    } else {
                        // 原文和翻译都不匹配（理论上不应该出现）
                        articleVO.setIsTranslationMatch(false);
                        log.warn("文章ID {} 在搜索结果中但原文和翻译都不匹配搜索词: {}", articleVO.getId(), searchText);
                    }
                    
                    // 对标题和内容进行高亮处理（原有逻辑）
                    if (idList.get(0).contains(articleVO.getId())) {
                        // 标题匹配的文章
                        if (!Boolean.TRUE.equals(articleVO.getIsTranslationMatch())) {
                            String highlightedTitle;
                            if (isRegexSearch) {
                                highlightedTitle = StringUtil.highlightTextWithRegex(originalTitle, actualSearchText, highlightStart, highlightEnd);
                            } else {
                                highlightedTitle = StringUtil.highlightText(originalTitle, searchText, highlightStart, highlightEnd);
                            }
                            articleVO.setArticleTitle(highlightedTitle);
                        }
                        titles.add(articleVO);
                    } else if (idList.get(1).contains(articleVO.getId())) {
                        // 内容匹配的文章
                        if (!Boolean.TRUE.equals(articleVO.getIsTranslationMatch())) {
                            // 智能截取包含搜索关键词的原文内容片段（使用原始内容）
                            String contentSnippet = getContentSnippetWithKeyword(originalContent, searchText, CommonConst.SUMMARY);
                            String highlightedContent;
                            if (isRegexSearch) {
                                highlightedContent = StringUtil.highlightTextWithRegex(contentSnippet, actualSearchText, highlightStart, highlightEnd);
                            } else {
                                highlightedContent = StringUtil.highlightText(contentSnippet, searchText, highlightStart, highlightEnd);
                            }
                            articleVO.setArticleContent(highlightedContent);
                        }
                        contents.add(articleVO);
                    } else if (Boolean.TRUE.equals(articleVO.getIsTranslationMatch())) {
                        // 翻译匹配的文章，统一添加到内容匹配列表
                        contents.add(articleVO);
                    }
                } else {
                    // 非搜索情况下，对内容进行默认截断处理
                    if (originalContent.length() > CommonConst.SUMMARY) {
                        String truncatedContent = originalContent.substring(0, CommonConst.SUMMARY)
                            .replace("`", "").replace("#", "").replace(">", "") + "...";
                        articleVO.setArticleContent(truncatedContent);
                    }
                    articles.add(articleVO);
                }
                
                // 搜索场景下，确保summary为空，强制前端使用articleContent
                if (StringUtils.hasText(baseRequestVO.getArticleSearch())) {
                    articleVO.setSummary(null);
                }
            }

            List<ArticleVO> collect = new ArrayList<>();
            collect.addAll(articles);
            collect.addAll(titles);
            collect.addAll(contents);
            baseRequestVO.setRecords(collect);
        }
        return PoetryResult.success(baseRequestVO);
    }

    @Override
    @ResourceCheck(CommonConst.RESOURCE_ARTICLE_DOC)
    public PoetryResult<ArticleVO> getArticleById(Integer id, String password) {
        return getArticleById(id, password, true);
    }
    
    /**
     * 获取文章详情，可以选择是否增加浏览量
     * @param id 文章ID
     * @param password 密码
     * @param incrementViewCount 是否增加浏览量
     * @return 文章详情
     */
    public PoetryResult<ArticleVO> getArticleById(Integer id, String password, boolean incrementViewCount) {
        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.eq(Article::getId, id);

        Article article = lambdaQuery.one();
        if (article == null) {
            return PoetryResult.success();
        }
        if (!article.getViewStatus() && (!StringUtils.hasText(password) || !password.equals(article.getPassword()))) {
            return PoetryResult.fail("密码错误" + (StringUtils.hasText(article.getTips()) ? article.getTips() : "请联系作者获取密码"));
        }
        
        // 只有当需要增加浏览量时才调用updateViewCount
        if (incrementViewCount) {
            articleMapper.updateViewCount(id);
        }
        
        article.setPassword(null);
        if (StringUtils.hasText(article.getVideoUrl())) {
            article.setVideoUrl(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).encryptBase64(article.getVideoUrl()));
        }
        
        ArticleVO articleVO = buildArticleVO(article, false);
        
        // 直接使用数据库中存储的摘要
        if (StringUtils.hasText(article.getSummary())) {
            articleVO.setSummary(article.getSummary());
        }
        
        return PoetryResult.success(articleVO);
    }

    @Override
    public PoetryResult<Page> listAdminArticle(BaseRequestVO baseRequestVO, Boolean isBoss) {
        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.select(Article.class, a -> !a.getColumn().equals("article_content"));
        if (!isBoss) {
            lambdaQuery.eq(Article::getUserId, PoetryUtil.getUserId());
        } else {
            if (baseRequestVO.getUserId() != null) {
                lambdaQuery.eq(Article::getUserId, baseRequestVO.getUserId());
            }
        }
        if (StringUtils.hasText(baseRequestVO.getSearchKey())) {
            lambdaQuery.like(Article::getArticleTitle, baseRequestVO.getSearchKey());
        }
        if (baseRequestVO.getRecommendStatus() != null && baseRequestVO.getRecommendStatus()) {
            lambdaQuery.eq(Article::getRecommendStatus, PoetryEnum.STATUS_ENABLE.getCode());
        }

        if (baseRequestVO.getLabelId() != null) {
            lambdaQuery.eq(Article::getLabelId, baseRequestVO.getLabelId());
        }

        if (baseRequestVO.getSortId() != null) {
            lambdaQuery.eq(Article::getSortId, baseRequestVO.getSortId());
        }

        Page<Article> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        lambdaQuery.orderByDesc(Article::getCreateTime).page(page);

        List<Article> records = page.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            List<ArticleVO> collect = records.stream().map(article -> {
                article.setPassword(null);
                ArticleVO articleVO = buildArticleVO(article, true);
                return articleVO;
            }).collect(Collectors.toList());
            baseRequestVO.setRecords(collect);
        }
        return PoetryResult.success(baseRequestVO);
    }

    @Override
    public PoetryResult<ArticleVO> getArticleByIdForUser(Integer id) {
        // 先检查当前用户是否创建了这篇文章
        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.eq(Article::getId, id).eq(Article::getUserId, PoetryUtil.getUserId());
        Article article = lambdaQuery.one();
        
        // 如果当前用户不是文章创建者，检查是否为管理员访问API创建的文章
        if (article == null) {
            // 检查当前用户是否有权限(Boss角色)
            if (PoetryUtil.isBoss()) {
                // 尝试直接通过ID获取文章
                article = lambdaQuery().eq(Article::getId, id).one();
                if (article != null) {
                    ArticleVO articleVO = new ArticleVO();
                    BeanUtils.copyProperties(article, articleVO);
                    return PoetryResult.success(articleVO);
                }
            }
            return PoetryResult.fail("文章不存在！");
        }
        
        ArticleVO articleVO = new ArticleVO();
        BeanUtils.copyProperties(article, articleVO);
        return PoetryResult.success(articleVO);
    }

    @Override
    public PoetryResult<Map<Integer, List<ArticleVO>>> listSortArticle() {
        // 使用Redis缓存替换PoetryCache
        Map<Integer, List<Article>> cachedResult = cacheService.getCachedSortArticleList();
        if (cachedResult != null) {
            // 转换为ArticleVO
            Map<Integer, List<ArticleVO>> result = new HashMap<>();
            for (Map.Entry<?, List<Article>> entry : cachedResult.entrySet()) {
                // 安全地转换键类型，处理String到Integer的转换
                Integer sortId = convertToInteger(entry.getKey());
                if (sortId != null) {
                    List<ArticleVO> articleVOList = entry.getValue().stream().map(article -> {
                        ArticleVO vo = buildArticleVO(article, false);
                        if (StringUtils.hasText(article.getSummary())) {
                            vo.setSummary(article.getSummary());
                        }
                        vo.setHasVideo(StringUtils.hasText(article.getVideoUrl()));
                        vo.setPassword(null);
                        vo.setVideoUrl(null);
                        return vo;
                    }).collect(Collectors.toList());
                    result.put(sortId, articleVOList);
                } else {
                    log.warn("无法转换分类ID: {}, 类型: {}", entry.getKey(),
                            entry.getKey() != null ? entry.getKey().getClass().getSimpleName() : "null");
                }
            }
            return PoetryResult.success(result);
        }

        synchronized (CommonConst.SORT_ARTICLE_LIST.intern()) {
            // 双重检查锁定
            cachedResult = cacheService.getCachedSortArticleList();
            if (cachedResult == null) {
                Map<Integer, List<Article>> articleMap = new HashMap<>();
                Map<Integer, List<ArticleVO>> resultMap = new HashMap<>();

                List<Sort> sorts = new LambdaQueryChainWrapper<>(sortMapper).select(Sort::getId).list();
                for (Sort sort : sorts) {
                    LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery()
                            .eq(Article::getSortId, sort.getId())
                            .eq(Article::getViewStatus, true)  // 添加对可见文章的过滤
                            .orderByDesc(Article::getCreateTime)
                            .last("limit 6");
                    List<Article> articleList = lambdaQuery.list();
                    if (CollectionUtils.isEmpty(articleList)) {
                        continue;
                    }

                    // 处理文章内容用于缓存
                    List<Article> processedArticles = articleList.stream().map(article -> {
                        Article processedArticle = new Article();
                        BeanUtils.copyProperties(article, processedArticle);
                        // 如果内容太长，截取用于缓存
                        if (processedArticle.getArticleContent().length() > CommonConst.SUMMARY) {
                            processedArticle.setArticleContent(processedArticle.getArticleContent().substring(0, CommonConst.SUMMARY).replace("`", "").replace("#", "").replace(">", "") + "...");
                        }
                        return processedArticle;
                    }).collect(Collectors.toList());

                    List<ArticleVO> articleVOList = processedArticles.stream().map(article -> {
                        ArticleVO vo = buildArticleVO(article, false);

                        // 直接使用数据库中存储的摘要
                        if (StringUtils.hasText(article.getSummary())) {
                            vo.setSummary(article.getSummary());
                        }

                        vo.setHasVideo(StringUtils.hasText(article.getVideoUrl()));
                        vo.setPassword(null);
                        vo.setVideoUrl(null);
                        return vo;
                    }).collect(Collectors.toList());

                    articleMap.put(sort.getId(), processedArticles);
                    resultMap.put(sort.getId(), articleVOList);
                }

                // 缓存到Redis
                cacheService.cacheSortArticleList(articleMap);
                return PoetryResult.success(resultMap);
            } else {
                // 转换缓存结果为ArticleVO
                Map<Integer, List<ArticleVO>> resultMap = new HashMap<>();
                for (Map.Entry<?, List<Article>> entry : cachedResult.entrySet()) {
                    // 安全地转换键类型，处理String到Integer的转换
                    Integer sortId = convertToInteger(entry.getKey());
                    if (sortId != null) {
                        List<ArticleVO> articleVOList = entry.getValue().stream().map(article -> {
                            ArticleVO vo = buildArticleVO(article, false);
                            if (StringUtils.hasText(article.getSummary())) {
                                vo.setSummary(article.getSummary());
                            }
                            vo.setHasVideo(StringUtils.hasText(article.getVideoUrl()));
                            vo.setPassword(null);
                            vo.setVideoUrl(null);
                            return vo;
                        }).collect(Collectors.toList());
                        resultMap.put(sortId, articleVOList);
                    } else {
                        log.warn("无法转换分类ID: {}, 类型: {}", entry.getKey(),
                                entry.getKey() != null ? entry.getKey().getClass().getSimpleName() : "null");
                    }
                }
                return PoetryResult.success(resultMap);
            }
        }
    }

    private ArticleVO buildArticleVO(Article article, Boolean isAdmin) {
        ArticleVO articleVO = new ArticleVO();
        BeanUtils.copyProperties(article, articleVO);
        if (!isAdmin) {
            if (!StringUtils.hasText(articleVO.getArticleCover())) {
                articleVO.setArticleCover(PoetryUtil.getRandomCover(articleVO.getId().toString()));
            }
        }

        User user = commonQuery.getUser(articleVO.getUserId());
        if (user != null && StringUtils.hasText(user.getUsername())) {
            articleVO.setUsername(user.getUsername());
        } else if (!isAdmin) {
            articleVO.setUsername(PoetryUtil.getRandomName(articleVO.getUserId().toString()));
        }
        if (articleVO.getCommentStatus()) {
            articleVO.setCommentCount(commonQuery.getCommentCount(articleVO.getId(), CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode()));
        } else {
            articleVO.setCommentCount(0);
        }

        List<Sort> sortInfo = commonQuery.getSortInfo();
        if (!CollectionUtils.isEmpty(sortInfo)) {
            for (Sort s : sortInfo) {
                if (s.getId().intValue() == articleVO.getSortId().intValue()) {
                    Sort sort = new Sort();
                    BeanUtils.copyProperties(s, sort);
                    sort.setLabels(null);
                    articleVO.setSort(sort);
                    if (!CollectionUtils.isEmpty(s.getLabels())) {
                        for (int j = 0; j < s.getLabels().size(); j++) {
                            Label l = s.getLabels().get(j);
                            if (l.getId().intValue() == articleVO.getLabelId().intValue()) {
                                Label label = new Label();
                                BeanUtils.copyProperties(l, label);
                                articleVO.setLabel(label);
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
        return articleVO;
    }

    /**
     * 为Python端提供的摘要生成API
     */
    @Override
    public PoetryResult<String> generateSummary(String content, Integer maxLength) {
        try {
            if (!StringUtils.hasText(content)) {
                return PoetryResult.fail("内容不能为空");
            }
            
            int targetLength = (maxLength != null && maxLength > 0) ? maxLength : 150;
            String summary = SmartSummaryGenerator.generateAdvancedSummary(content, targetLength);
            
            if (StringUtils.hasText(summary)) {
                return PoetryResult.success(summary);
            } else {
                return PoetryResult.fail("摘要生成失败");
            }
            
        } catch (Exception e) {
            log.error("摘要生成失败", e);
            return PoetryResult.fail("摘要生成异常: " + e.getMessage());
        }
    }
    
    /**
     * 安全地将对象转换为Integer类型
     * 处理Redis序列化导致的类型转换问题
     * @param obj 要转换的对象
     * @return 转换后的Integer，转换失败返回null
     */
    private Integer convertToInteger(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof Integer) {
            return (Integer) obj;
        }

        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }

        if (obj instanceof String) {
            try {
                return Integer.valueOf((String) obj);
            } catch (NumberFormatException e) {
                log.warn("无法将字符串转换为Integer: {}", obj);
                return null;
            }
        }

        log.warn("不支持的类型转换: {} -> Integer", obj.getClass().getSimpleName());
        return null;
    }

    /**
     * 智能摘要生成（优先AI，回退TextRank）
     * @param content 文章内容
     * @return 生成的摘要
     */
    private String generateArticleSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        
        try {
            // 尝试调用Python端的AI摘要服务
            String aiSummary = callPythonAiSummary(content);
            if (StringUtils.hasText(aiSummary)) {
                log.info("使用AI生成文章摘要成功，长度: {}", aiSummary.length());
                return aiSummary;
            }
        } catch (Exception e) {
            log.warn("AI摘要生成失败，回退到TextRank算法: {}", e.getMessage());
        }
        
        // 回退到TextRank算法
        try {
            String textRankSummary = SmartSummaryGenerator.generateAdvancedSummary(content, 150);
            if (StringUtils.hasText(textRankSummary)) {
                log.info("使用TextRank生成文章摘要成功，长度: {}", textRankSummary.length());
                return textRankSummary;
            }
        } catch (Exception e) {
            log.error("TextRank摘要生成也失败: {}", e.getMessage());
        }
        
        // 最后的回退：简单截取
        log.warn("所有摘要生成方法都失败，使用简单截取");
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }
    
    /**
     * 调用Python端的AI摘要生成服务
     * @param content 文章内容
     * @return AI生成的摘要，失败时返回null
     */
    private String callPythonAiSummary(String content) {
        try {
            String pythonServerUrl = System.getenv().getOrDefault("PYTHON_SERVICE_URL", "http://localhost:5000");
            String pythonApiUrl = pythonServerUrl + "/api/translation/generate-summary";
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("content", content);
            requestBody.put("max_length", 150);
            requestBody.put("style", "concise");
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "poetize-java");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // 发送请求
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(pythonApiUrl, request, Map.class);
            
            if (response != null && "200".equals(String.valueOf(response.get("code")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data != null && data.get("summary") != null) {
                    String summary = data.get("summary").toString();
                    String method = data.get("method") != null ? data.get("method").toString() : "unknown";
                    log.info("AI摘要生成成功，使用方法: {}, 摘要长度: {}", method, summary.length());
                    return summary;
                }
            } else {
                String message = response != null ? String.valueOf(response.get("message")) : "Unknown error";
                log.warn("Python AI摘要服务返回错误: {}", message);
            }
        } catch (Exception e) {
            log.warn("调用Python AI摘要服务失败: {}", e.getMessage());
        }
        
        return null;
    }

    @Override
    public PoetryResult<List<ArticleVO>> getArticlesByLikesTop() {
        try {
            // 查询可见的文章，获取所有需要计算热度的字段
            LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery()
                    .select(Article::getId, Article::getUserId, Article::getSortId, Article::getLabelId, 
                            Article::getArticleCover, Article::getArticleTitle, Article::getArticleContent,
                            Article::getSummary, Article::getViewCount, Article::getLikeCount, 
                            Article::getCommentStatus, Article::getRecommendStatus, Article::getViewStatus,
                            Article::getCreateTime, Article::getUpdateTime, Article::getVideoUrl)
                    .eq(Article::getViewStatus, true)  // 只查询可见的文章
                    .orderByDesc(Article::getCreateTime);  // 先按时间排序，后面会重新排序

            List<Article> articles = lambdaQuery.list();

            if (CollectionUtils.isEmpty(articles)) {
                return PoetryResult.success(new ArrayList<>());
            }

            // 转换为ArticleVO并计算热度分数
            List<ArticleVO> articleVOList = articles.stream().map(article -> {
                // 如果内容太长，截取用于显示
                if (StringUtils.hasText(article.getArticleContent()) && article.getArticleContent().length() > CommonConst.SUMMARY) {
                    article.setArticleContent(article.getArticleContent().substring(0, CommonConst.SUMMARY)
                            .replace("`", "").replace("#", "").replace(">", "") + "...");
                }

                ArticleVO articleVO = buildArticleVO(article, false);
                
                // 使用数据库中存储的摘要
                if (StringUtils.hasText(article.getSummary())) {
                    articleVO.setSummary(article.getSummary());
                }
                
                // 设置视频标识
                articleVO.setHasVideo(StringUtils.hasText(article.getVideoUrl()));
                
                // 清空敏感信息
                articleVO.setPassword(null);
                articleVO.setVideoUrl(null);
                
                return articleVO;
            }).collect(Collectors.toList());

            // 计算每篇文章的热度分数并排序
            articleVOList = articleVOList.stream()
                    .sorted((a1, a2) -> {
                        double score1 = calculateHotScore(a1);
                        double score2 = calculateHotScore(a2);
                        return Double.compare(score2, score1);  // 降序排列
                    })
                    .limit(10)  // 限制返回前10篇
                    .collect(Collectors.toList());

            log.info("获取热门文章成功，返回{}篇文章", articleVOList.size());
            return PoetryResult.success(articleVOList);

        } catch (Exception e) {
            log.error("获取热门文章失败", e);
            return PoetryResult.fail("获取热门文章失败: " + e.getMessage());
        }
    }

    /**
     * 计算文章热度分数的智能算法
     * 综合考虑浏览量、点赞数、评论数、发布时间等因素
     * 
     * @param articleVO 文章VO对象
     * @return 热度分数（越高越热门）
     */
    private double calculateHotScore(ArticleVO articleVO) {
        // 基础数据
        int viewCount = articleVO.getViewCount() != null ? articleVO.getViewCount() : 0;
        int likeCount = articleVO.getLikeCount() != null ? articleVO.getLikeCount() : 0;
        int commentCount = articleVO.getCommentCount() != null ? articleVO.getCommentCount() : 0;
        LocalDateTime createTime = articleVO.getCreateTime();
        
        // 1. 浏览量权重 (40%) - 标准化处理
        double viewScore = Math.log10(Math.max(viewCount, 1)) * 40;
        
        // 2. 点赞数权重 (30%) - 点赞的价值比浏览更高
        double likeScore = Math.log10(Math.max(likeCount, 1)) * 30 * 3; // 点赞权重加强
        
        // 3. 评论数权重 (20%) - 评论表示深度参与
        double commentScore = Math.log10(Math.max(commentCount, 1)) * 20 * 5; // 评论权重更高
        
        // 4. 时间衰减因子 (10%) - 新文章有加成，但不会完全压倒旧的热门文章
        double timeScore = 0;
        if (createTime != null) {
            long daysSinceCreation = java.time.Duration.between(createTime, LocalDateTime.now()).toDays();
            
            // 使用指数衰减，但设置一个底线
            if (daysSinceCreation <= 7) {
                // 一周内的文章有时间加成
                timeScore = 10 * Math.exp(-daysSinceCreation / 7.0);
            } else if (daysSinceCreation <= 30) {
                // 一个月内的文章保持一定分数
                timeScore = 5 * Math.exp(-(daysSinceCreation - 7) / 23.0);
            } else {
                // 超过一个月的文章，时间分数较低但不为0
                timeScore = 1;
            }
        }
        
        // 5. 互动比率加成 - 点赞率和评论率高的文章额外加分
        double engagementBonus = 0;
        if (viewCount > 0) {
            double likeRate = (double) likeCount / viewCount;
            double commentRate = (double) commentCount / viewCount;
            
            // 点赞率超过1%的文章加分
            if (likeRate > 0.01) {
                engagementBonus += Math.min(likeRate * 1000, 10); // 最多加10分
            }
            
            // 评论率超过0.5%的文章加分
            if (commentRate > 0.005) {
                engagementBonus += Math.min(commentRate * 2000, 15); // 最多加15分
            }
        }
        
        // 6. 推荐文章额外加分
        double recommendBonus = 0;
        if (Boolean.TRUE.equals(articleVO.getRecommendStatus())) {
            recommendBonus = 20; // 被推荐的文章额外加20分
        }
        
        // 计算最终热度分数
        double finalScore = viewScore + likeScore + commentScore + timeScore + engagementBonus + recommendBonus;
        
        // 调试日志
        if (log.isDebugEnabled()) {
            log.debug("文章[{}]热度计算: 浏览({})={:.2f}, 点赞({})={:.2f}, 评论({})={:.2f}, 时间={:.2f}, 互动={:.2f}, 推荐={:.2f}, 总分={:.2f}",
                    articleVO.getArticleTitle(), viewCount, viewScore, likeCount, likeScore, 
                    commentCount, commentScore, timeScore, engagementBonus, recommendBonus, finalScore);
        }
        
        return finalScore;
    }

    /**
     * 异步更新文章（快速响应版本）
     */
    @Override
    public PoetryResult<String> updateArticleAsync(ArticleVO articleVO) {
        // 调用重载方法，使用默认参数
        return updateArticleAsync(articleVO, false, null);
    }

    /**
     * 异步更新文章（快速响应版本，支持翻译参数）
     */
    @Override
    public PoetryResult<String> updateArticleAsync(ArticleVO articleVO, boolean skipAiTranslation, Map<String, String> pendingTranslation) {
        // 生成任务ID
        String taskId = "article_update_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        
        // 基础验证
        if (articleVO.getId() == null) {
            return PoetryResult.fail("文章ID不能为空！");
        }
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && !StringUtils.hasText(articleVO.getPassword())) {
            return PoetryResult.fail("请设置文章密码！");
        }
        
        Integer userId = PoetryUtil.getUserId();
        if (userId == null) {
            return PoetryResult.fail("无法确定文章作者，请重新登录后再试");
        }
        
        // 在主线程中获取用户信息，避免异步线程中无法访问RequestContext
        String currentUsername = null;
        try {
            currentUsername = PoetryUtil.getUsername();
        } catch (Exception e) {
            log.warn("【异步更新】无法获取当前用户名，使用默认值: {}", e.getMessage());
            currentUsername = "System";
        }
        final String finalUsername = currentUsername;
        
        // 初始化更新状态
        ArticleSaveStatus initialStatus = new ArticleSaveStatus(taskId, "processing", "正在更新文章...", articleVO.getId());
        ARTICLE_SAVE_STATUS.put(taskId, initialStatus);
        log.info("【异步更新】初始化更新状态，任务ID: {}, 文章ID: {}, 当前任务总数: {}", taskId, articleVO.getId(), ARTICLE_SAVE_STATUS.size());
        
        // 异步执行更新
        new Thread(() -> {
            try {
                log.info("【异步更新】开始异步更新文章，任务ID: {}, 文章ID: {}", taskId, articleVO.getId());
                
                // 更新状态：正在生成摘要
                updateSaveStatus(taskId, "processing", "正在生成AI摘要...");
                
                // 构建更新条件
                LambdaUpdateChainWrapper<Article> updateChainWrapper = lambdaUpdate()
                        .eq(Article::getId, articleVO.getId())
                        .eq(Article::getUserId, userId)
                        .set(Article::getLabelId, articleVO.getLabelId())
                        .set(Article::getSortId, articleVO.getSortId())
                        .set(Article::getArticleTitle, articleVO.getArticleTitle())
                        .set(Article::getUpdateBy, finalUsername)
                        .set(Article::getUpdateTime, LocalDateTime.now())
                        .set(Article::getVideoUrl, StringUtils.hasText(articleVO.getVideoUrl()) ? articleVO.getVideoUrl() : null)
                        .set(Article::getArticleContent, articleVO.getArticleContent());

                if (StringUtils.hasText(articleVO.getArticleCover())) {
                    updateChainWrapper.set(Article::getArticleCover, articleVO.getArticleCover());
                }
                if (articleVO.getCommentStatus() != null) {
                    updateChainWrapper.set(Article::getCommentStatus, articleVO.getCommentStatus());
                }
                if (articleVO.getRecommendStatus() != null) {
                    updateChainWrapper.set(Article::getRecommendStatus, articleVO.getRecommendStatus());
                }
                if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && StringUtils.hasText(articleVO.getPassword())) {
                    updateChainWrapper.set(Article::getPassword, articleVO.getPassword());
                    updateChainWrapper.set(StringUtils.hasText(articleVO.getTips()), Article::getTips, articleVO.getTips());
                }
                if (articleVO.getViewStatus() != null) {
                    updateChainWrapper.set(Article::getViewStatus, articleVO.getViewStatus());
                }
                if (articleVO.getSubmitToSearchEngine() != null) {
                    updateChainWrapper.set(Article::getSubmitToSearchEngine, articleVO.getSubmitToSearchEngine());
                }
                
                // 生成AI摘要（如果内容有变化）
                if (StringUtils.hasText(articleVO.getArticleContent())) {
                    try {
                        log.info("【异步更新】开始生成AI摘要，任务ID: {}, 文章内容长度: {}", taskId, articleVO.getArticleContent().length());
                        String aiSummary = summaryService.generateSummarySync(articleVO.getArticleContent());
                        updateChainWrapper.set(Article::getSummary, StringUtils.hasText(aiSummary) ? aiSummary : "");
                        log.info("【异步更新】AI摘要生成成功，任务ID: {}, 摘要长度: {}", taskId, aiSummary != null ? aiSummary.length() : 0);
                    } catch (Exception e) {
                        log.error("【异步更新】AI摘要生成失败，任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
                        log.warn("【异步更新】文章更新：AI摘要生成失败，保持原摘要，任务ID: {}", taskId);
                    }
                }
                
                // 更新状态：正在更新数据库
                updateSaveStatus(taskId, "processing", "正在更新数据库...");
                
                // 执行数据库更新
                log.info("【异步更新】开始更新数据库，任务ID: {}, 文章ID: {}", taskId, articleVO.getId());
                boolean updated = updateChainWrapper.update();
                if (!updated) {
                    log.error("【异步更新】数据库更新失败，任务ID: {}, 文章ID: {}", taskId, articleVO.getId());
                    updateSaveStatus(taskId, "failed", "数据库更新失败");
                    return;
                }
                log.info("【异步更新】数据库更新成功，任务ID: {}, 文章ID: {}", taskId, articleVO.getId());

                // 手动清除Redis中的分类文章列表缓存，确保首页能显示更新后的文章
                try {
                    cacheService.evictSortArticleList();
                    log.info("【异步更新】已清除分类文章列表缓存，确保首页显示更新后的文章，任务ID: {}", taskId);
                } catch (Exception e) {
                    log.error("【异步更新】清除分类文章列表缓存失败，任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
                }
                
                // 发布文章更新事件，触发预渲染更新（在事务提交后执行）
                try {
                    eventPublisher.publishEvent(new ArticleSavedEvent(articleVO.getId(), articleVO.getSortId(), 
                                                                    articleVO.getViewStatus(), "UPDATE"));
                    log.info("【异步更新】已发布文章更新事件，将触发首页预渲染更新，任务ID: {}", taskId);
                } catch (Exception e) {
                    log.error("【异步更新】发布文章更新事件失败，任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
                }
                
                // 更新状态：后台处理中
                updateSaveStatus(taskId, "processing", "文章已更新，正在处理翻译...");
                
                // 异步翻译（翻译完成后内部会触发预渲染）
                try {
                    log.info("【异步更新】开始文章翻译，任务ID: {}, 文章ID: {}, 跳过AI翻译: {}, 有暂存翻译: {}",
                            taskId, articleVO.getId(), skipAiTranslation, pendingTranslation != null && !pendingTranslation.isEmpty());
                    translationService.translateAndSaveArticle(articleVO.getId(), skipAiTranslation, pendingTranslation);
                    log.info("【异步更新】文章翻译完成（包含预渲染），任务ID: {}, 文章ID: {}", taskId, articleVO.getId());
                } catch (Exception e) {
                    log.error("【异步更新】文章翻译失败，任务ID: {}, 文章ID: {}, 错误: {}", taskId, articleVO.getId(), e.getMessage(), e);
                }
                
                // 更新状态：翻译完成，正在处理sitemap更新和SEO推送
                updateSaveStatus(taskId, "processing", "文章已更新，正在处理sitemap更新和SEO推送...");
                
                // 注意：sitemap更新（包括文章可见性变更）已经通过 ArticleEventListener.updateSitemapAsync() 处理
                // 无需在这里重复处理sitemap更新
                log.info("【异步更新】文章ID {} 更新完成，sitemap更新将通过事件监听器自动处理，任务ID: {}", articleVO.getId(), taskId);
                
                // 如果需要推送至搜索引擎且文章可见，异步处理
                if (Boolean.TRUE.equals(articleVO.getSubmitToSearchEngine()) && Boolean.TRUE.equals(articleVO.getViewStatus())) {
                    log.info("【异步更新】更新文章ID {} 标记为需要推送至搜索引擎，开始异步处理，任务ID: {}", articleVO.getId(), taskId);
                    
                    // 异步执行SEO推送，避免阻塞用户操作
                    new Thread(() -> {
                        try {
                            boolean seoResult = seoService.submitToSearchEngines(articleVO.getId());
                            log.info("【异步更新】更新文章ID {} 搜索引擎推送完成，结果: {}，任务ID: {}", articleVO.getId(), seoResult ? "成功" : "失败", taskId);
                        } catch (Exception e) {
                            log.error("【异步更新】搜索引擎推送失败，但不影响文章更新，文章ID: " + articleVO.getId() + ", 任务ID: " + taskId, e);
                        }
                    }).start();
                } else {
                    log.info("【异步更新】更新文章ID {} 未标记为需要推送至搜索引擎或文章不可见，任务ID: {}", articleVO.getId(), taskId);
                }
                
                // 最终成功状态
                updateSaveStatus(taskId, "success", "文章更新成功！AI摘要已生成", articleVO.getId());
                log.info("【异步更新】文章更新完成，任务ID: {}, 文章ID: {}", taskId, articleVO.getId());
                
            } catch (Exception e) {
                log.error("【异步更新】文章更新失败，任务ID: {}, 文章ID: {}, 错误: {}", taskId, articleVO.getId(), e.getMessage(), e);
                updateSaveStatus(taskId, "failed", "更新失败：" + e.getMessage());
            }
        }).start();
        
        return PoetryResult.success(taskId);
    }

    /**
     * 智能截取包含搜索关键词的内容片段
     * @param content 原始内容
     * @param keyword 搜索关键词
     * @param maxLength 最大长度
     * @return 包含关键词的内容片段
     */
    private String getContentSnippetWithKeyword(String content, String keyword, int maxLength) {
        if (content == null || keyword == null || content.length() <= maxLength) {
            // 如果内容不长，直接返回并添加省略号（如果需要）
            return content != null && content.length() > maxLength ? 
                   content.substring(0, maxLength) + "..." : content;
        }

        // 查找关键词位置（忽略大小写）
        int keywordIndex = content.toLowerCase().indexOf(keyword.toLowerCase());
        
        if (keywordIndex == -1) {
            // 如果没找到关键词，返回开头部分
            return content.substring(0, maxLength) + "...";
        }

        // 计算截取的起始位置，尽量让关键词居中
        int keywordLength = keyword.length();
        int halfLength = (maxLength - keywordLength) / 2;
        
        int startIndex = Math.max(0, keywordIndex - halfLength);
        int endIndex = Math.min(content.length(), startIndex + maxLength);
        
        // 如果从中间开始，调整起始位置确保不超过最大长度
        if (endIndex - startIndex < maxLength && startIndex > 0) {
            startIndex = Math.max(0, endIndex - maxLength);
        }

        String snippet = content.substring(startIndex, endIndex);
        
        // 添加省略号
        if (startIndex > 0) {
            snippet = "..." + snippet;
        }
        if (endIndex < content.length()) {
            snippet = snippet + "...";
        }

        return snippet;
    }

    @Override
    public ArticleVO getTranslationContent(Integer id, String searchKey, String language) {
        try {
            // 获取原文章
            Article article = this.getById(id);
            if (article == null) {
                throw new RuntimeException("文章不存在");
            }

            // 如果没有指定语言，尝试获取第一个可用的翻译语言
            if (language == null || language.trim().isEmpty()) {
                List<String> availableLanguages = translationService.getArticleAvailableLanguages(id);
                if (availableLanguages.isEmpty()) {
                    throw new RuntimeException("该文章没有可用的翻译");
                }
                language = availableLanguages.get(0);
            }
            
            // 获取翻译内容
            Map<String, String> translation = translationService.getArticleTranslation(id, language);
            if (translation == null || translation.isEmpty()) {
                throw new RuntimeException("翻译内容不存在");
            }

            String translatedTitle = translation.get("title");
            String translatedContent = translation.get("content");

            if (translatedTitle == null || translatedContent == null) {
                throw new RuntimeException("翻译内容不完整");
            }

            // 对翻译内容进行搜索高亮处理和智能截取
            if (searchKey != null && !searchKey.trim().isEmpty()) {
                // 检测是否为正则表达式搜索
                boolean isRegexSearch = searchKey.startsWith("/") && searchKey.endsWith("/") && searchKey.length() > 2;
                String actualSearchText = isRegexSearch ? searchKey.substring(1, searchKey.length() - 1) : searchKey;
                
                // 使用高亮标签进行处理
                String highlightStart = "<span class='search-highlight' style='color: var(--lightGreen); font-weight: bold;'>";
                String highlightEnd = "</span>";
                
                if (isRegexSearch) {
                    translatedTitle = StringUtil.highlightTextWithRegex(translatedTitle, actualSearchText, highlightStart, highlightEnd);
                    // 对翻译内容进行智能截取和正则高亮
                    translatedContent = getContentSnippetWithKeyword(translatedContent, actualSearchText, 80);
                    translatedContent = StringUtil.highlightTextWithRegex(translatedContent, actualSearchText, highlightStart, highlightEnd);
                } else {
                    translatedTitle = StringUtil.highlightText(translatedTitle, searchKey, highlightStart, highlightEnd);
                    // 对翻译内容进行智能截取和高亮
                    translatedContent = getContentSnippetWithKeyword(translatedContent, searchKey, 80);
                    translatedContent = StringUtil.highlightText(translatedContent, searchKey, highlightStart, highlightEnd);
                }
            } else {
                // 如果没有搜索关键词，也要进行内容截取（显示前80个字符）
                translatedContent = truncateContent(translatedContent, 80);
            }

            // 构建返回的 ArticleVO
            ArticleVO articleVO = new ArticleVO();
            articleVO.setId(article.getId());
            articleVO.setArticleTitle(translatedTitle);
            articleVO.setArticleContent(translatedContent);

            return articleVO;
        } catch (Exception e) {
            throw new RuntimeException("获取翻译内容失败: " + e.getMessage());
        }
    }

    /**
     * 截取内容到指定长度
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        
        // 移除HTML标签进行长度计算
        String plainText = content.replaceAll("<[^>]*>", "");
        if (plainText.length() <= maxLength) {
            return content;
        }
        
        // 截取到指定长度，尽量在句号、感叹号、问号处截断
        String truncated = plainText.substring(0, maxLength);
        int lastSentenceEnd = Math.max(
            Math.max(truncated.lastIndexOf('。'), truncated.lastIndexOf('！')),
            Math.max(truncated.lastIndexOf('？'), truncated.lastIndexOf('.'))
        );
        
        if (lastSentenceEnd > maxLength * 0.7) {
            truncated = plainText.substring(0, lastSentenceEnd + 1);
        }
        
        return truncated + "...";
    }

}
