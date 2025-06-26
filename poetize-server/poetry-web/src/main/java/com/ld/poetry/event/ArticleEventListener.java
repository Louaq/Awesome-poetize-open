package com.ld.poetry.event;

import com.ld.poetry.utils.PrerenderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文章事件监听器
 * 在数据库事务提交后执行预渲染操作
 */
@Component
@Slf4j
public class ArticleEventListener {
    
    @Autowired
    private PrerenderClient prerenderClient;
    
    /**
     * 监听文章保存事件，在事务提交后执行预渲染
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleArticleSavedEvent(ArticleSavedEvent event) {
        log.info("收到文章事件: ID={}, 操作={}, 可见={}", 
                 event.getArticleId(), event.getOperationType(), event.getViewStatus());
        
        try {
            switch (event.getOperationType()) {
                case "CREATE":
                case "UPDATE":
                    if (Boolean.TRUE.equals(event.getViewStatus())) {
                        // 文章可见，执行预渲染
                        prerenderClient.renderArticle(event.getArticleId());
                        prerenderClient.renderHomePage();
                        if (event.getSortId() != null) {
                            prerenderClient.renderCategoryPage(event.getSortId());
                        }
                        log.info("文章预渲染完成: ID={}", event.getArticleId());
                    } else {
                        // 文章不可见，删除预渲染文件
                        prerenderClient.deleteArticle(event.getArticleId());
                        prerenderClient.renderHomePage();
                        if (event.getSortId() != null) {
                            prerenderClient.renderCategoryPage(event.getSortId());
                        }
                        log.info("文章预渲染文件删除完成: ID={}", event.getArticleId());
                    }
                    break;
                    
                case "DELETE":
                    // 删除预渲染文件并重新渲染相关页面
                    prerenderClient.deleteArticle(event.getArticleId());
                    prerenderClient.renderHomePage();
                    if (event.getSortId() != null) {
                        prerenderClient.renderCategoryPage(event.getSortId());
                    }
                    log.info("文章删除后预渲染处理完成: ID={}", event.getArticleId());
                    break;
                    
                default:
                    log.warn("未知的文章操作类型: {}", event.getOperationType());
            }
        } catch (Exception e) {
            log.warn("文章预渲染处理失败: ID={}, 操作={}, 错误={}", 
                     event.getArticleId(), event.getOperationType(), e.getMessage());
        }
    }
} 