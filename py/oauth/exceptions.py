"""
OAuth异常定义
统一的异常处理机制
"""

class OAuthError(Exception):
    """OAuth基础异常类"""
    def __init__(self, message: str, error_code: str = None, provider: str = None):
        super().__init__(message)
        self.message = message
        self.error_code = error_code
        self.provider = provider

class ConfigurationError(OAuthError):
    """配置错误异常"""
    pass

class ValidationError(OAuthError):
    """验证错误异常"""
    pass

class TokenError(OAuthError):
    """Token相关错误异常"""
    pass

class UserInfoError(OAuthError):
    """用户信息获取错误异常"""
    pass

class StateValidationError(ValidationError):
    """State验证错误异常"""
    pass

class ProviderNotSupportedError(OAuthError):
    """不支持的提供商异常"""
    pass
