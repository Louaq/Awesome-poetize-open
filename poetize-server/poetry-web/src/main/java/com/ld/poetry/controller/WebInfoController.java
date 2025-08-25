package com.ld.poetry.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.*;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.WebInfoService;
import com.ld.poetry.service.ThirdPartyOauthConfigService;
import com.ld.poetry.dao.WebInfoMapper;
import com.ld.poetry.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 网站信息表 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@Slf4j
@SuppressWarnings("unchecked")
@RestController
@RequestMapping("/webInfo")
public class WebInfoController {

    @Autowired
    private WebInfoService webInfoService;

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private WebInfoMapper webInfoMapper;

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private PrerenderClient prerenderClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ThirdPartyOauthConfigService thirdPartyOauthConfigService;

    @Autowired
    private CacheService cacheService;

    /**
     * 更新网站信息
     */
    @LoginCheck(0)
    @PostMapping("/updateWebInfo")
    public PoetryResult<WebInfo> updateWebInfo(@RequestBody WebInfo webInfo) {
        try {
            // 记录更新前的详细信息
            log.info("开始更新网站信息 - ID: {}, webName: {}, webTitle: {}",
                    webInfo.getId(), webInfo.getWebName(), webInfo.getWebTitle());
            log.debug("更新数据详情: {}", webInfo);

            // 使用自定义更新方法确保所有字段都能正确更新
            int updateResult = webInfoMapper.updateWebInfoById(webInfo);
            log.info("网站信息数据库更新结果: {} 行受影响, ID: {}", updateResult, webInfo.getId());

            if (updateResult == 0) {
                log.error("数据库更新失败：没有行受影响，可能ID不存在或数据未变化");
                return PoetryResult.fail("更新失败：网站信息不存在或数据未变化");
            }

            // 验证更新是否成功：重新查询最新数据
            log.info("重新查询数据库验证更新结果...");
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();

            if (!CollectionUtils.isEmpty(list)) {
                WebInfo latestWebInfo = list.get(0);

                // 验证数据是否真正更新
                log.info("数据库查询结果 - webName: {}, webTitle: {}",
                        latestWebInfo.getWebName(), latestWebInfo.getWebTitle());

                // 检查关键字段是否更新成功
                if (webInfo.getWebName() != null && !webInfo.getWebName().equals(latestWebInfo.getWebName())) {
                    log.warn("webName更新失败 - 期望: {}, 实际: {}", webInfo.getWebName(), latestWebInfo.getWebName());
                } else if (webInfo.getWebTitle() != null && !webInfo.getWebTitle().equals(latestWebInfo.getWebTitle())) {
                    log.warn("webTitle更新失败 - 期望: {}, 实际: {}", webInfo.getWebTitle(), latestWebInfo.getWebTitle());
                } else {
                    log.info("数据库更新验证成功");
                }

                // 原子性缓存更新：先设置新缓存，再删除旧缓存
                // 这样可以避免缓存空窗期
                // cacheService.evictWebInfo(); // 不再需要删除缓存，直接覆盖即可
                cacheService.cacheWebInfo(latestWebInfo);

                log.info("网站信息缓存更新成功(永久缓存) - webName: {}, webTitle: {}",
                        latestWebInfo.getWebName(), latestWebInfo.getWebTitle());

                // 网站信息更新时，重新渲染首页和百宝箱页面
                try {
                    prerenderClient.renderMainPages();
                    log.debug("网站信息更新后成功触发页面预渲染");
                } catch (Exception e) {
                    // 预渲染失败不影响主流程，只记录日志
                    log.warn("网站信息更新后页面预渲染失败", e);
                }
            } else {
                log.warn("更新后未找到网站信息数据");
            }

            return PoetryResult.success();
        } catch (Exception e) {
            log.error("更新网站信息失败", e);
            return PoetryResult.fail("更新网站信息失败: " + e.getMessage());
        }
    }


    /**
     * 获取网站信息
     */
    @GetMapping("/getWebInfo")
    public PoetryResult<WebInfo> getWebInfo() {
        try {
            // 直接从Redis缓存获取网站信息
            WebInfo webInfo = cacheService.getCachedWebInfo();
            if (webInfo != null) {
                WebInfo result = new WebInfo();
                BeanUtils.copyProperties(webInfo, result);

                // 清理敏感信息，不对外暴露
                result.setRandomAvatar(null);
                result.setRandomName(null);
                result.setWaifuJson(null);

                // 添加访问统计数据
                addHistoryStatsToWebInfo(result);

                log.debug("成功从Redis缓存获取网站信息");
                return PoetryResult.success(result);
            }

            log.warn("Redis缓存中未找到网站信息");
            return PoetryResult.success();

        } catch (Exception e) {
            log.error("获取网站信息时发生错误", e);
            return PoetryResult.success();
        }
    }

    /**
     * 为WebInfo添加访问统计数据
     */
    private void addHistoryStatsToWebInfo(WebInfo result) {
        try {
            Map<String, Object> historyStats = (Map<String, Object>) cacheService.getCachedIpHistoryStatistics();
            if (historyStats != null) {
                // 获取总访问量
                Long historyCount = (Long) historyStats.get(CommonConst.IP_HISTORY_COUNT);
                if (historyCount != null) {
                    result.setHistoryAllCount(historyCount.toString());
                }

                // 获取24小时内访问统计
                List<Map<String, Object>> hourStats = (List<Map<String, Object>>) historyStats.get(CommonConst.IP_HISTORY_HOUR);
                if (hourStats != null) {
                    result.setHistoryDayCount(Integer.toString(hourStats.size()));
                }

                log.debug("成功添加访问统计数据到网站信息");
            } else {
                log.debug("访问统计缓存为空，使用默认值");
                result.setHistoryAllCount("0");
                result.setHistoryDayCount("0");
            }
        } catch (Exception e) {
            // 访问统计获取失败不影响主要功能，使用默认值
            log.warn("获取访问统计时出错，使用默认值", e);
            result.setHistoryAllCount("0");
            result.setHistoryDayCount("0");
        }
    }

    /**
     * 获取用户IP地址 - 用于403页面显示
     */
    @GetMapping("/getUserIP")
    public PoetryResult<Map<String, Object>> getUserIP() {
        Map<String, Object> result = new HashMap<>();
        String clientIP = PoetryUtil.getIpAddr(PoetryUtil.getRequest());
        result.put("ip", clientIP);
        result.put("timestamp", System.currentTimeMillis());
        return PoetryResult.success(result);
    }

    @LoginCheck(0)
    @PostMapping("/updateThirdLoginConfig")
    public PoetryResult<Object> updateThirdLoginConfig(@RequestBody Map<String, Object> config) {
        try {
            log.info("更新第三方登录配置: {}", config);

            // 直接使用数据库服务更新配置
            PoetryResult<Boolean> result = thirdPartyOauthConfigService.updateThirdLoginConfig(config);

            if (result.isSuccess()) {
                log.info("第三方登录配置更新成功");
                return PoetryResult.success("配置更新成功");
            } else {
                log.warn("第三方登录配置更新失败: {}", result.getMessage());
                return PoetryResult.fail(result.getMessage());
            }
        } catch (Exception e) {
            log.error("第三方登录配置更新失败", e);
            return PoetryResult.fail("第三方登录配置更新失败: " + e.getMessage());
        }
    }

    /**
     * 获取网站统计信息
     */
    @LoginCheck(0)
    @GetMapping("/getHistoryInfo")
    public PoetryResult<Map<String, Object>> getHistoryInfo() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 使用安全的缓存获取方法，内置了默认值处理
            Map<String, Object> history = cacheService.getCachedIpHistoryStatisticsSafely();
            
            // 检查是否需要刷新缓存
            if (Boolean.TRUE.equals(history.get("_cache_refresh_needed"))) {
                log.info("检测到缓存需要刷新，主动刷新统计数据");
                try {
                    // 主动刷新缓存
                    Map<String, Object> refreshedHistory = new HashMap<>();
                    refreshedHistory.put(CommonConst.IP_HISTORY_PROVINCE, historyInfoMapper.getHistoryByProvince());
                    refreshedHistory.put(CommonConst.IP_HISTORY_IP, historyInfoMapper.getHistoryByIp());
                    refreshedHistory.put(CommonConst.IP_HISTORY_HOUR, historyInfoMapper.getHistoryBy24Hour());
                    refreshedHistory.put(CommonConst.IP_HISTORY_COUNT, historyInfoMapper.getHistoryCount());
                    
                    // 缓存新数据
                    cacheService.cacheIpHistoryStatistics(refreshedHistory);
                    history = refreshedHistory;
                    log.info("缓存刷新成功，总访问量: {}", history.get(CommonConst.IP_HISTORY_COUNT));
                } catch (Exception refreshException) {
                    log.error("主动刷新缓存失败", refreshException);
                    // 刷新失败时删除标记，避免频繁刷新
                    history.remove("_cache_refresh_needed");
                }
            }

            // 获取今日访问信息
            List<HistoryInfo> infoList = new LambdaQueryChainWrapper<>(historyInfoMapper)
                    .select(HistoryInfo::getIp, HistoryInfo::getUserId, HistoryInfo::getNation, HistoryInfo::getProvince, HistoryInfo::getCity)
                    .ge(HistoryInfo::getCreateTime, LocalDateTime.now().with(LocalTime.MIN))
                    .list();

            // 从缓存中获取数据（getCachedIpHistoryStatisticsSafely已确保非null）
            result.put(CommonConst.IP_HISTORY_PROVINCE, history.get(CommonConst.IP_HISTORY_PROVINCE));
            result.put(CommonConst.IP_HISTORY_IP, history.get(CommonConst.IP_HISTORY_IP));
            result.put(CommonConst.IP_HISTORY_COUNT, history.get(CommonConst.IP_HISTORY_COUNT));

            // 处理24小时数据
            List<Map<String, Object>> ipHistoryCount = (List<Map<String, Object>>) history.get(CommonConst.IP_HISTORY_HOUR);

            if (ipHistoryCount != null && !ipHistoryCount.isEmpty()) {
                result.put("ip_count_yest", ipHistoryCount.stream()
                    .map(m -> m != null ? m.get("ip") : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count());
            } else {
                result.put("ip_count_yest", 0L);
            }
            // 安全地处理昨日用户信息
            if (ipHistoryCount != null && !ipHistoryCount.isEmpty()) {
                result.put("username_yest", ipHistoryCount.stream()
                    .filter(Objects::nonNull)
                    .map(m -> {
                        try {
                            Object userId = m.get("user_id");
                            if (userId != null) {
                                User user = commonQuery.getUser(Integer.valueOf(userId.toString()));
                                if (user != null) {
                                    Map<String, String> userInfo = new HashMap<>();
                                    userInfo.put("avatar", user.getAvatar());
                                    userInfo.put("username", user.getUsername());
                                    return userInfo;
                                }
                            }
                        } catch (Exception e) {
                            log.warn("处理昨日用户信息时出错: {}", e.getMessage());
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
            } else {
                result.put("username_yest", new ArrayList<>());
            }

            // 处理今日访问统计
            if (infoList != null) {
                result.put("ip_count_today", infoList.stream()
                    .map(HistoryInfo::getIp)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count());

                result.put("username_today", infoList.stream()
                    .filter(Objects::nonNull)
                    .map(m -> {
                        try {
                            Integer userId = m.getUserId();
                            if (userId != null) {
                                User user = commonQuery.getUser(userId);
                                if (user != null) {
                                    Map<String, String> userInfo = new HashMap<>();
                                    userInfo.put("avatar", user.getAvatar());
                                    userInfo.put("username", user.getUsername());
                                    return userInfo;
                                }
                            }
                        } catch (Exception e) {
                            log.warn("处理今日用户信息时出错: {}", e.getMessage());
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));

                // 处理今日省份统计
                List<Map<String, Object>> list = infoList.stream()
                        .map(HistoryInfo::getProvince)
                        .filter(Objects::nonNull)
                        .collect(Collectors.groupingBy(m -> m, Collectors.counting()))
                        .entrySet().stream()
                        .map(entry -> {
                            HashMap<String, Object> map = new HashMap<>();
                            map.put("province", entry.getKey());
                            map.put("num", entry.getValue());
                            return map;
                        })
                        .sorted((o1, o2) -> Long.valueOf(o2.get("num").toString())
                            .compareTo(Long.valueOf(o1.get("num").toString())))
                        .collect(Collectors.toList());

                result.put("province_today", list);
            } else {
                result.put("ip_count_today", 0L);
                result.put("username_today", new ArrayList<>());
                result.put("province_today", new ArrayList<>());
            }

            return PoetryResult.success(result);

        } catch (Exception e) {
            log.error("获取历史统计信息时发生错误", e);
            // 返回默认的空数据，避免前端报错
            Map<String, Object> defaultResult = createDefaultHistoryResult();
            return PoetryResult.success(defaultResult);
        }
    }



    /**
     * 创建默认的历史结果数据
     */
    private Map<String, Object> createDefaultHistoryResult() {
        Map<String, Object> defaultResult = new HashMap<>();
        defaultResult.put(CommonConst.IP_HISTORY_PROVINCE, new ArrayList<>());
        defaultResult.put(CommonConst.IP_HISTORY_IP, new ArrayList<>());
        defaultResult.put(CommonConst.IP_HISTORY_COUNT, 0L);
        defaultResult.put("ip_count_yest", 0L);
        defaultResult.put("username_yest", new ArrayList<>());
        defaultResult.put("ip_count_today", 0L);
        defaultResult.put("username_today", new ArrayList<>());
        defaultResult.put("province_today", new ArrayList<>());

        log.info("返回默认历史统计结果");
        return defaultResult;
    }



    /**
     * 获取赞赏
     */
    @GetMapping("/getAdmire")
    public PoetryResult<List<User>> getAdmire() {
        return PoetryResult.success(commonQuery.getAdmire());
    }

    /**
     * 获取看板娘状态
     * 替代Python端的getWaifuStatus端点，统一架构设计
     */
    @GetMapping("/getWaifuStatus")
    public PoetryResult<Map<String, Object>> getWaifuStatus() {
        try {
            log.debug("收到获取看板娘状态请求");

            // 从缓存获取网站信息以保持性能
            WebInfo webInfo = cacheService.getCachedWebInfo();

            if (webInfo != null) {
                Boolean enableWaifu = webInfo.getEnableWaifu();
                if (enableWaifu == null) {
                    enableWaifu = false;
                }

                Map<String, Object> data = new HashMap<>();
                data.put("enableWaifu", enableWaifu);
                data.put("id", webInfo.getId());

                log.debug("返回看板娘状态: enableWaifu={}, id={}", enableWaifu, webInfo.getId());
                return PoetryResult.success(data);
            } else {
                log.warn("网站信息不存在");
                return PoetryResult.fail("网站信息不存在");
            }
        } catch (Exception e) {
            log.error("获取看板娘状态失败", e);
            return PoetryResult.fail("获取看板娘状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取看板娘消息
     */
    @GetMapping("/getWaifuJson")
    public String getWaifuJson() {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        if (webInfo != null && StringUtils.hasText(webInfo.getWaifuJson())) {
            return webInfo.getWaifuJson();
        }
        return "{}";
    }

    /**
     * 清除分类信息缓存
     */
    @GetMapping("/clearSortCache")
    public PoetryResult<String> clearSortCache() {
        cacheService.evictSortList();
        return PoetryResult.success();
    }

    /**
     * 获取API配置
     */
    @LoginCheck(0)
    @GetMapping("/getApiConfig")
    public PoetryResult<Map<String, Object>> getApiConfig() {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
                cacheService.cacheWebInfo(webInfo);
            } else {
                webInfo = new WebInfo();
            }
        }
        
        Map<String, Object> apiConfig = new HashMap<>();
        apiConfig.put("enabled", webInfo.getApiEnabled() != null ? webInfo.getApiEnabled() : false);
        apiConfig.put("apiKey", webInfo.getApiKey() != null ? webInfo.getApiKey() : generateApiKey());
        
        return PoetryResult.success(apiConfig);
    }

    /**
     * 保存API配置
     */
    @LoginCheck(0)
    @PostMapping("/saveApiConfig")
    public PoetryResult<String> saveApiConfig(@RequestBody Map<String, Object> apiConfig) {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
            } else {
                return PoetryResult.fail("网站信息不存在");
            }
        }
        
        Boolean enabled = (Boolean) apiConfig.get("enabled");
        String apiKey = (String) apiConfig.get("apiKey");
        
        // 如果提交的配置不包含apiKey，生成一个新的
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = generateApiKey();
        }
        
        // 更新数据库
        WebInfo updateInfo = new WebInfo();
        updateInfo.setId(webInfo.getId());
        updateInfo.setApiEnabled(enabled);
        updateInfo.setApiKey(apiKey);
        webInfoService.updateById(updateInfo);

        // 清理Redis缓存并重新缓存最新数据
        cacheService.evictWebInfo();
        webInfo.setApiEnabled(enabled);
        webInfo.setApiKey(apiKey);
        cacheService.cacheWebInfo(webInfo);
        log.info("API配置更新成功，已刷新Redis缓存");

        return PoetryResult.success();
    }

    /**
     * 重新生成API密钥
     */
    @LoginCheck(0)
    @PostMapping("/regenerateApiKey")
    public PoetryResult<String> regenerateApiKey() {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
            } else {
                return PoetryResult.fail("网站信息不存在");
            }
        }
        
        String newApiKey = generateApiKey();
        
        // 更新数据库
        WebInfo updateInfo = new WebInfo();
        updateInfo.setId(webInfo.getId());
        updateInfo.setApiKey(newApiKey);
        webInfoService.updateById(updateInfo);
        
        // 更新缓存
        webInfo.setApiKey(newApiKey);
        cacheService.cacheWebInfo(webInfo);
        
        return PoetryResult.success(newApiKey);
    }

    /**
     * 获取分类信息 - 用于预渲染服务
     * 此接口专门为prerender-worker提供分类列表数据
     */
    @GetMapping("/listSortForPrerender")
    public PoetryResult<List<Sort>> listSortForPrerender() {
        try {
            // 获取所有分类信息，包含标签
            List<Sort> sortList = new LambdaQueryChainWrapper<>(sortMapper)
                    .orderByAsc(Sort::getSortType)
                    .orderByAsc(Sort::getPriority)
                    .list();
            
            log.debug("预渲染服务获取分类列表，共{}个分类", sortList.size());
            return PoetryResult.success(sortList);
        } catch (Exception e) {
            log.error("获取预渲染分类列表失败", e);
            return PoetryResult.fail("获取分类列表失败");
        }
    }

    /**
     * 获取分类详细信息 - 用于预渲染服务
     * @param sortId 分类ID
     */
    @GetMapping("/getSortDetailForPrerender")
    public PoetryResult<Sort> getSortDetailForPrerender(@RequestParam Integer sortId) {
        if (sortId == null) {
            return PoetryResult.fail("分类ID不能为空");
        }
        
        try {
            // 获取分类基本信息
            Sort sort = sortMapper.selectById(sortId);
            if (sort == null) {
                return PoetryResult.fail("分类不存在");
            }
            
            // 获取该分类下的标签信息
            LambdaQueryChainWrapper<Label> labelWrapper = new LambdaQueryChainWrapper<>(labelMapper);
            List<Label> labels = labelWrapper.eq(Label::getSortId, sortId).list();
            sort.setLabels(labels);
            
            log.debug("预渲染服务获取分类详情，分类ID: {}, 标签数: {}", sortId, labels != null ? labels.size() : 0);
            return PoetryResult.success(sort);
        } catch (Exception e) {
            log.error("获取预渲染分类详情失败，分类ID: {}", sortId, e);
            return PoetryResult.fail("获取分类详情失败");
        }
    }
    
    /**
     * 生成API密钥
     */
    private String generateApiKey() {
        return UUID.randomUUID().toString().replaceAll("-", "") + 
               UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
    }

    /**
     * 获取每日访问量统计
     * @param days 查询天数(1-365)，默认7
     */
    @LoginCheck(0)
    @GetMapping("/getDailyVisitStats")
    public PoetryResult<List<Map<String, Object>>> getDailyVisitStats(@RequestParam(value = "days", defaultValue = "7") Integer days) {
        if (days == null || days <= 0) {
            days = 7;
        } else if (days > 365) {
            days = 365;
        }

        List<Map<String, Object>> stats = historyInfoMapper.getDailyVisitStats(days);

        if (stats == null) {
            return PoetryResult.success(Collections.emptyList());
        }

        // 计算平均 unique_visits
        double avg = stats.stream()
                .map(m -> (Number) m.get("unique_visits"))
                .filter(Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .average()
                .orElse(0);
        avg = Math.round(avg * 100.0) / 100.0;

        for (Map<String, Object> m : stats) {
            m.put("avg_unique_visits", avg);
        }

        return PoetryResult.success(stats);
    }

    /**
     * 手动刷新访问统计缓存（管理员专用）
     */
    @LoginCheck(1)
    @PostMapping("/refreshHistoryCache")
    public PoetryResult<Map<String, Object>> refreshHistoryCache() {
        try {
            log.info("管理员手动刷新访问统计缓存");
            
            // 清理旧缓存
            cacheService.evictIpHistoryStatistics();
            
            // 重新构建统计数据
            Map<String, Object> history = new HashMap<>();
            
            // 获取省份统计
            List<Map<String, Object>> provinceStats = historyInfoMapper.getHistoryByProvince();
            history.put(CommonConst.IP_HISTORY_PROVINCE, provinceStats != null ? provinceStats : new ArrayList<>());
            log.info("省份访问统计更新成功，数据条数: {}", provinceStats != null ? provinceStats.size() : 0);
            
            // 获取IP统计
            List<Map<String, Object>> ipStats = historyInfoMapper.getHistoryByIp();
            history.put(CommonConst.IP_HISTORY_IP, ipStats != null ? ipStats : new ArrayList<>());
            log.info("IP访问统计更新成功，数据条数: {}", ipStats != null ? ipStats.size() : 0);
            
            // 获取24小时统计
            List<Map<String, Object>> hourStats = historyInfoMapper.getHistoryBy24Hour();
            history.put(CommonConst.IP_HISTORY_HOUR, hourStats != null ? hourStats : new ArrayList<>());
            log.info("24小时访问统计更新成功，数据条数: {}", hourStats != null ? hourStats.size() : 0);
            
            // 获取总访问量
            Long totalCount = historyInfoMapper.getHistoryCount();
            history.put(CommonConst.IP_HISTORY_COUNT, totalCount != null ? totalCount : 0L);
            log.info("总访问量统计更新成功: {}", totalCount);
            
            // 缓存新数据
            cacheService.cacheIpHistoryStatistics(history);
            
            // 返回统计结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("totalCount", totalCount);
            result.put("provinceCount", provinceStats != null ? provinceStats.size() : 0);
            result.put("ipCount", ipStats != null ? ipStats.size() : 0);
            result.put("hourCount", hourStats != null ? hourStats.size() : 0);
            result.put("refreshTime", System.currentTimeMillis());
            
            log.info("访问统计缓存刷新完成");
            return PoetryResult.success(result);
            
        } catch (Exception e) {
            log.error("手动刷新访问统计缓存失败", e);
            return PoetryResult.fail("刷新失败: " + e.getMessage());
        }
    }

    @LoginCheck(0)
    @GetMapping("/getThirdLoginConfig")
    public PoetryResult<Object> getThirdLoginConfig() {
        try {
            log.info("获取第三方登录配置");

            // 直接从数据库获取配置
            PoetryResult<Map<String, Object>> result = thirdPartyOauthConfigService.getThirdLoginConfig();

            if (result.isSuccess()) {
                log.info("第三方登录配置获取成功");
                return PoetryResult.success(result.getData());
            } else {
                log.warn("第三方登录配置获取失败: {}", result.getMessage());
                return PoetryResult.fail(result.getMessage());
            }
        } catch (Exception e) {
            log.error("获取第三方登录配置失败", e);
            return PoetryResult.fail("获取第三方登录配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取第三方登录状态（轻量级接口，用于前端状态检查）
     */
    @GetMapping("/getThirdLoginStatus")
    public PoetryResult<Object> getThirdLoginStatus(@RequestParam(required = false) String provider) {
        try {
            log.debug("获取第三方登录状态，平台: {}", provider);

            // 获取所有配置
            List<ThirdPartyOauthConfig> allConfigs = thirdPartyOauthConfigService.getAllConfigs();

            // 获取激活的配置（全局启用且平台启用）
            List<ThirdPartyOauthConfig> activeConfigs = thirdPartyOauthConfigService.getActiveConfigs();

            // 构建状态响应
            Map<String, Object> status = new HashMap<>();

            // 检查是否有任何平台全局启用且平台启用
            boolean globalEnabled = !activeConfigs.isEmpty();
            status.put("enable", globalEnabled);

            // 如果指定了平台，检查该平台状态
            if (provider != null && !provider.trim().isEmpty()) {
                boolean platformEnabled = activeConfigs.stream()
                    .anyMatch(config -> provider.equals(config.getPlatformType()));
                status.put(provider, Map.of("enabled", platformEnabled));
            } else {
                // 返回所有平台状态（包括未启用的）
                for (ThirdPartyOauthConfig config : allConfigs) {
                    Map<String, Object> platformStatus = new HashMap<>();
                    platformStatus.put("enabled", config.getEnabled() && config.getGlobalEnabled());

                    // 添加平台基本信息
                    platformStatus.put("platformName", config.getPlatformName());
                    platformStatus.put("sortOrder", config.getSortOrder());

                    status.put(config.getPlatformType(), platformStatus);
                }
            }

            log.debug("第三方登录状态响应: {}", status);
            return PoetryResult.success(status);
        } catch (Exception e) {
            log.error("获取第三方登录状态失败", e);
            return PoetryResult.fail("获取第三方登录状态失败: " + e.getMessage());
        }
    }

}

