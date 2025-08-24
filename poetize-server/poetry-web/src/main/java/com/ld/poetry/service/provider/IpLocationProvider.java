package com.ld.poetry.service.provider;

/**
 * IP地理位置解析提供者接口
 * 用于统一管理不同的IP地理位置解析服务
 * 
 * @author LeapYa
 */
public interface IpLocationProvider {
    
    /**
     * 提供者类型枚举
     */
    enum ProviderType {
        TENCENT_LBS("腾讯位置服务", 1),
        TAOBAO_IP("淘宝IP服务", 2), 
        IP2_REGION("IP2Region离线库", 3);
        
        private final String name;
        private final int priority;
        
        ProviderType(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
        
        public String getName() {
            return name;
        }
        
        public int getPriority() {
            return priority;
        }
    }
    
    /**
     * 获取提供者类型
     * @return 提供者类型
     */
    ProviderType getProviderType();
    
    /**
     * 解析IP地址的地理位置
     * @param ipAddress IP地址（支持IPv4和IPv6）
     * @return 地理位置字符串，解析失败返回"未知"
     */
    String resolveLocation(String ipAddress);
    
    /**
     * 检查提供者是否可用
     * @return 是否可用
     */
    boolean isAvailable();
    
    /**
     * 检查是否支持指定的IP地址类型
     * @param ipAddress IP地址
     * @return 是否支持
     */
    boolean supportsIpType(String ipAddress);
    
    /**
     * 获取提供者名称
     * @return 提供者名称
     */
    default String getProviderName() {
        return getProviderType().getName();
    }
    
    /**
     * 获取提供者优先级（数字越小优先级越高）
     * @return 优先级
     */
    default int getPriority() {
        return getProviderType().getPriority();
    }
}