"""
OAuth模块 - 第三方登录重构版本
提供统一的OAuth提供商管理和处理机制
"""

from .factory import OAuthProviderFactory
from .base import BaseOAuthProvider
from .exceptions import OAuthError, ConfigurationError, ValidationError
from .config import OAuthConfigManager

__all__ = [
    'OAuthProviderFactory',
    'BaseOAuthProvider', 
    'OAuthError',
    'ConfigurationError',
    'ValidationError',
    'OAuthConfigManager'
]

__version__ = '2.0.0'
