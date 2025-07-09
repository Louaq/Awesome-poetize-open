package com.ld.poetry.utils.cache;

import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.User;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.service.UserService;
import com.ld.poetry.utils.RetryUtil;
import com.ld.poetry.utils.cache.PoetryCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 用户信息缓存管理器
 * 提供用户信息的多级缓存策略，减少数据库查询
 */
@Component
@Slf4j
public class UserCacheManager {

    @Autowired
    private UserService userService;

    /**
     * 用户信息本地缓存（L1缓存）
     * 用于减少对PoetryCache的访问
     */
    private final ConcurrentHashMap<String, CacheEntry> localUserCache = new ConcurrentHashMap<>();

    /**
     * 用户ID到Token的映射缓存
     */
    private final ConcurrentHashMap<Integer, String> userIdToTokenCache = new ConcurrentHashMap<>();

    /**
     * 读写锁，用于保护缓存操作
     */
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();

    /**
     * 本地缓存过期时间（毫秒）
     */
    private static final long LOCAL_CACHE_EXPIRE_TIME = 5 * 60 * 1000; // 5分钟

    /**
     * 最大本地缓存大小
     */
    private static final int MAX_LOCAL_CACHE_SIZE = 1000;

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        private final User user;
        private final long timestamp;

        public CacheEntry(User user) {
            this.user = user;
            this.timestamp = System.currentTimeMillis();
        }

        public User getUser() {
            return user;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > LOCAL_CACHE_EXPIRE_TIME;
        }
    }

    /**
     * 根据Token获取用户信息（带缓存）
     *
     * @param token 用户Token
     * @return 用户信息，如果不存在返回null
     */
    public User getUserByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }

        return RetryUtil.executeWithRetry(() -> {
            // 1. 先从本地缓存获取
            User cachedUser = getUserFromLocalCache(token);
            if (cachedUser != null) {
                return cachedUser;
            }

            // 2. 从PoetryCache获取
            User user = (User) PoetryCache.get(token);
            if (user != null) {
                // 更新本地缓存
                putUserToLocalCache(token, user);
                // 更新用户ID到Token的映射
                updateUserIdToTokenMapping(user.getId(), token);
                return user;
            }

            // 3. 如果缓存中都没有，尝试从数据库查询（这种情况很少见）
            log.debug("Token {} 在缓存中不存在，可能已过期", token);
            return null;
        }, 2, 50, "获取用户信息");
    }

    /**
     * 根据用户ID获取用户信息（带缓存）
     *
     * @param userId 用户ID
     * @return 用户信息，如果不存在返回null
     */
    public User getUserById(Integer userId) {
        if (userId == null) {
            return null;
        }

        return RetryUtil.executeWithRetry(() -> {
            // 1. 先尝试通过用户ID找到对应的Token
            String token = userIdToTokenCache.get(userId);
            if (StringUtils.hasText(token)) {
                User user = getUserFromLocalCache(token);
                if (user != null && user.getId().equals(userId)) {
                    return user;
                }
            }

            // 2. 从数据库查询
            User user = userService.getById(userId);
            if (user != null) {
                // 如果有对应的Token，更新本地缓存
                if (StringUtils.hasText(token)) {
                    putUserToLocalCache(token, user);
                }
                log.debug("从数据库查询用户信息: userId={}", userId);
            }
            return user;
        }, 2, 50, "根据ID获取用户信息");
    }

    /**
     * 缓存用户信息
     *
     * @param token 用户Token
     * @param user  用户信息
     */
    public void cacheUser(String token, User user) {
        if (!StringUtils.hasText(token) || user == null) {
            return;
        }

        try {
            // 更新PoetryCache
            PoetryCache.put(token, user);
            
            // 更新本地缓存
            putUserToLocalCache(token, user);
            
            // 更新用户ID到Token的映射
            updateUserIdToTokenMapping(user.getId(), token);
            
            log.debug("缓存用户信息: userId={}, token={}", user.getId(), token);
        } catch (Exception e) {
            log.error("缓存用户信息失败: userId={}, token={}", user.getId(), token, e);
        }
    }

    /**
     * 移除用户缓存
     *
     * @param token 用户Token
     */
    public void removeUserCache(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }

        try {
            // 从本地缓存中获取用户信息，以便清理用户ID映射
            User user = getUserFromLocalCache(token);
            
            // 移除PoetryCache
            PoetryCache.remove(token);
            
            // 移除本地缓存
            removeUserFromLocalCache(token);
            
            // 移除用户ID到Token的映射
            if (user != null) {
                userIdToTokenCache.remove(user.getId());
            }
            
            log.debug("移除用户缓存: token={}", token);
        } catch (Exception e) {
            log.error("移除用户缓存失败: token={}", token, e);
        }
    }

    /**
     * 移除用户缓存（根据用户ID）
     *
     * @param userId 用户ID
     */
    public void removeUserCacheById(Integer userId) {
        if (userId == null) {
            return;
        }

        try {
            String token = userIdToTokenCache.remove(userId);
            if (StringUtils.hasText(token)) {
                removeUserCache(token);
            }
            log.debug("移除用户缓存: userId={}", userId);
        } catch (Exception e) {
            log.error("移除用户缓存失败: userId={}", userId, e);
        }
    }

    /**
     * 从本地缓存获取用户信息
     */
    private User getUserFromLocalCache(String token) {
        cacheLock.readLock().lock();
        try {
            CacheEntry entry = localUserCache.get(token);
            if (entry != null && !entry.isExpired()) {
                return entry.getUser();
            } else if (entry != null && entry.isExpired()) {
                // 异步清理过期条目
                localUserCache.remove(token);
            }
            return null;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * 将用户信息放入本地缓存
     */
    private void putUserToLocalCache(String token, User user) {
        cacheLock.writeLock().lock();
        try {
            // 检查缓存大小，如果超过限制则清理
            if (localUserCache.size() >= MAX_LOCAL_CACHE_SIZE) {
                cleanupExpiredEntries();
                
                // 如果清理后仍然超过限制，移除一些旧条目
                if (localUserCache.size() >= MAX_LOCAL_CACHE_SIZE) {
                    int toRemove = localUserCache.size() - MAX_LOCAL_CACHE_SIZE + 100;
                    localUserCache.entrySet().stream()
                            .limit(toRemove)
                            .forEach(entry -> localUserCache.remove(entry.getKey()));
                }
            }
            
            localUserCache.put(token, new CacheEntry(user));
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * 从本地缓存移除用户信息
     */
    private void removeUserFromLocalCache(String token) {
        cacheLock.writeLock().lock();
        try {
            localUserCache.remove(token);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * 更新用户ID到Token的映射
     */
    private void updateUserIdToTokenMapping(Integer userId, String token) {
        if (userId != null && StringUtils.hasText(token)) {
            userIdToTokenCache.put(userId, token);
        }
    }

    /**
     * 清理过期的缓存条目
     */
    private void cleanupExpiredEntries() {
        localUserCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计信息
     */
    public String getCacheStats() {
        cacheLock.readLock().lock();
        try {
            long expiredCount = localUserCache.values().stream()
                    .mapToLong(entry -> entry.isExpired() ? 1 : 0)
                    .sum();
            
            return String.format("本地缓存大小: %d, 过期条目: %d, 用户ID映射: %d", 
                    localUserCache.size(), expiredCount, userIdToTokenCache.size());
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * 清空所有缓存
     */
    public void clearAllCache() {
        cacheLock.writeLock().lock();
        try {
            localUserCache.clear();
            userIdToTokenCache.clear();
            log.info("已清空所有用户缓存");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * 根据Token移除用户缓存
     * @param token 用户Token
     */
    public void removeUserByToken(String token) {
        if (StringUtils.hasText(token)) {
            removeUserCache(token);
            log.debug("移除用户缓存 - Token: {}", token);
        }
    }
    
    /**
     * 根据用户ID移除用户缓存
     * @param userId 用户ID
     */
    public void removeUserById(Integer userId) {
        if (userId != null) {
            removeUserCacheById(userId);
            log.debug("移除用户缓存 - 用户ID: {}", userId);
        }
    }
    
    /**
     * 缓存用户信息（根据Token）
     * @param token 用户Token
     * @param user 用户对象
     */
    public void cacheUserByToken(String token, User user) {
        if (StringUtils.hasText(token) && user != null) {
            cacheUser(token, user);
            log.debug("缓存用户信息 - Token: {}, 用户: {}", token, user.getUsername());
        }
    }
    
    /**
     * 缓存用户信息（根据用户ID）
     * @param userId 用户ID
     * @param user 用户对象
     */
    public void cacheUserById(Integer userId, User user) {
        if (userId != null && user != null) {
            // 如果有对应的Token，使用Token缓存
            String token = userIdToTokenCache.get(userId);
            if (StringUtils.hasText(token)) {
                cacheUser(token, user);
            }
            log.debug("缓存用户信息 - 用户ID: {}, 用户: {}", userId, user.getUsername());
        }
    }
}