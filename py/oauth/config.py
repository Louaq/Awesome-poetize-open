"""
OAuth配置管理
统一的配置获取和验证机制
"""

from typing import Dict, Any, Optional
from .exceptions import ConfigurationError


class OAuthConfigManager:
    """OAuth配置管理器"""
    
    # 提供商配置模板
    PROVIDER_TEMPLATES = {
        "github": {
            "auth_url": "https://github.com/login/oauth/authorize",
            "token_url": "https://github.com/login/oauth/access_token",
            "user_info_url": "https://api.github.com/user",
            "emails_url": "https://api.github.com/user/emails",
            "scope": "user:email"
        },
        "google": {
            "auth_url": "https://accounts.google.com/o/oauth2/v2/auth",
            "token_url": "https://oauth2.googleapis.com/token",
            "user_info_url": "https://people.googleapis.com/v1/people/me",
            "scope": "openid email profile"
        },
        "x": {
            "request_token_url": "https://api.twitter.com/oauth/request_token",
            "auth_url": "https://api.twitter.com/oauth/authenticate",
            "access_token_url": "https://api.twitter.com/oauth/access_token",
            "user_info_url": "https://api.twitter.com/1.1/account/verify_credentials.json",
            "include_email": "true"
        },
        "yandex": {
            "auth_url": "https://oauth.yandex.com/authorize",
            "token_url": "https://oauth.yandex.com/token",
            "user_info_url": "https://login.yandex.ru/info",
            "scope": "login:email login:info"
        },
        "gitee": {
            "auth_url": "https://gitee.com/oauth/authorize",
            "token_url": "https://gitee.com/oauth/token",
            "user_info_url": "https://gitee.com/api/v5/user",
            "emails_url": "https://gitee.com/api/v5/emails",
            "scope": "user_info emails"
        }
    }
    
    def __init__(self, config_source_func=None):
        """
        初始化配置管理器
        
        Args:
            config_source_func: 配置源函数，用于获取动态配置
        """
        self.config_source_func = config_source_func
    
    def get_provider_config(self, provider: str) -> Dict[str, Any]:
        """
        获取提供商配置
        
        Args:
            provider: 提供商名称
            
        Returns:
            Dict[str, Any]: 提供商配置
            
        Raises:
            ConfigurationError: 配置获取失败
        """
        try:
            # 获取基础模板
            template = self.PROVIDER_TEMPLATES.get(provider)
            if not template:
                raise ConfigurationError(f"不支持的OAuth提供商: {provider}", "unsupported_provider", provider)
            
            # 获取动态配置
            dynamic_config = self._get_dynamic_config(provider)
            
            # 合并配置
            config = template.copy()
            if dynamic_config:
                config.update(dynamic_config)
            
            # 验证配置完整性
            self._validate_provider_config(provider, config)
            
            return config
            
        except Exception as e:
            if isinstance(e, ConfigurationError):
                raise
            raise ConfigurationError(f"获取{provider}配置时出错: {str(e)}", "config_error", provider)
    
    def _get_dynamic_config(self, provider: str) -> Optional[Dict[str, Any]]:
        """
        获取动态配置
        
        Args:
            provider: 提供商名称
            
        Returns:
            Optional[Dict[str, Any]]: 动态配置或None
        """
        if not self.config_source_func:
            return None
        
        try:
            # 从配置源获取第三方登录配置
            config = self.config_source_func()
            if not config or not config.get('enable', False):
                return None
            
            # 获取特定提供商的配置
            provider_config = config.get(provider)
            if not provider_config or not provider_config.get('enabled', True):
                return None
            
            return provider_config
            
        except Exception as e:
            print(f"获取{provider}动态配置时出错: {str(e)}")
            return None
    
    def _validate_provider_config(self, provider: str, config: Dict[str, Any]) -> None:
        """
        验证提供商配置
        
        Args:
            provider: 提供商名称
            config: 配置字典
            
        Raises:
            ConfigurationError: 配置验证失败
        """
        # 检查OAuth 1.0 vs 2.0的必需字段
        if provider == 'x':  # Twitter使用OAuth 1.0
            required_fields = ['client_key', 'client_secret']
        else:  # 其他使用OAuth 2.0
            required_fields = ['client_id', 'client_secret']
        
        missing_fields = [field for field in required_fields if not config.get(field)]
        if missing_fields:
            raise ConfigurationError(
                f"{provider}配置不完整，缺少字段: {', '.join(missing_fields)}", 
                "incomplete_config", 
                provider
            )
    
    def is_provider_enabled(self, provider: str) -> bool:
        """
        检查提供商是否启用
        
        Args:
            provider: 提供商名称
            
        Returns:
            bool: 是否启用
        """
        try:
            self.get_provider_config(provider)
            return True
        except ConfigurationError:
            return False
    
    def get_supported_providers(self) -> list:
        """
        获取支持的提供商列表
        
        Returns:
            list: 支持的提供商名称列表
        """
        return list(self.PROVIDER_TEMPLATES.keys())
    
    def add_provider_template(self, provider: str, template: Dict[str, Any]) -> None:
        """
        添加新的提供商模板
        
        Args:
            provider: 提供商名称
            template: 配置模板
        """
        self.PROVIDER_TEMPLATES[provider] = template
