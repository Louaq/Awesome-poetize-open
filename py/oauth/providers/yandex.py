"""
Yandex OAuth提供商实现
"""

from typing import Dict, Any
from ..base import OAuth2Provider
from ..exceptions import TokenError, UserInfoError


class YandexProvider(OAuth2Provider):
    """Yandex OAuth 2.0提供商"""
    
    def get_provider_name(self) -> str:
        return "yandex"
    
    async def get_access_token(self, code: str) -> str:
        """获取Yandex访问令牌"""
        try:
            response = await self.handle_http_request(
                "POST",
                self.config["token_url"],
                data={
                    "grant_type": "authorization_code",
                    "code": code,
                    "client_id": self.config["client_id"],
                    "client_secret": self.config["client_secret"],
                    "redirect_uri": self.config["redirect_uri"]
                }
            )
            
            token_data = response.json()
            access_token = token_data.get("access_token")
            
            if not access_token:
                raise TokenError("Yandex未返回访问令牌", "no_token", "yandex")
            
            return access_token
            
        except Exception as e:
            if isinstance(e, TokenError):
                raise
            raise TokenError(f"获取Yandex访问令牌失败: {str(e)}", "token_request_failed", "yandex")
    
    async def get_user_info(self, access_token: str) -> Dict[str, Any]:
        """获取Yandex用户信息"""
        try:
            response = await self.handle_http_request(
                "GET",
                self.config["user_info_url"],
                params={"format": "json"},
                headers={"Authorization": f"OAuth {access_token}"}
            )
            user_info = response.json()
            
            # 检查邮箱收集需求
            raw_email = user_info.get("default_email")
            processed_email, email_collection_needed = self.check_email_collection_needed(raw_email)
            
            # 返回标准化用户信息
            return {
                "provider": "yandex",
                "uid": user_info.get("id"),
                "username": user_info.get("login"),
                "email": processed_email,
                "avatar": f"https://avatars.yandex.net/get-yapic/{user_info.get('default_avatar_id')}/islands-200",
                "email_collection_needed": email_collection_needed
            }
            
        except Exception as e:
            if isinstance(e, UserInfoError):
                raise
            raise UserInfoError(f"获取Yandex用户信息失败: {str(e)}", "user_info_failed", "yandex")
