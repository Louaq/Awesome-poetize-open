"""
Google OAuth提供商实现
"""

from typing import Dict, Any
from google.oauth2 import id_token
from google.auth.transport import requests as google_requests
from ..base import OAuth2Provider
from ..exceptions import TokenError, UserInfoError


class GoogleProvider(OAuth2Provider):
    """Google OAuth 2.0提供商"""
    
    def get_provider_name(self) -> str:
        return "google"
    
    def get_additional_auth_params(self) -> Dict[str, str]:
        """Google特定的授权参数"""
        return {"access_type": "offline"}  # 获取refresh_token
    
    async def get_access_token(self, code: str) -> str:
        """获取Google访问令牌"""
        try:
            response = await self.handle_http_request(
                "POST",
                self.config["token_url"],
                data={
                    "code": code,
                    "client_id": self.config["client_id"],
                    "client_secret": self.config["client_secret"],
                    "redirect_uri": self.config["redirect_uri"],
                    "grant_type": "authorization_code"
                }
            )
            
            token_data = response.json()
            access_token = token_data.get("access_token")
            
            if not access_token:
                raise TokenError("Google未返回访问令牌", "no_token", "google")
            
            # 存储ID Token用于用户信息验证
            self._id_token = token_data.get("id_token")
            
            return access_token
            
        except Exception as e:
            if isinstance(e, TokenError):
                raise
            raise TokenError(f"获取Google访问令牌失败: {str(e)}", "token_request_failed", "google")
    
    async def get_user_info(self, access_token: str) -> Dict[str, Any]:
        """获取Google用户信息"""
        try:
            # 验证ID Token
            if not hasattr(self, '_id_token') or not self._id_token:
                raise UserInfoError("缺少Google ID Token", "missing_id_token", "google")
            
            id_info = id_token.verify_oauth2_token(
                self._id_token,
                google_requests.Request(),
                self.config["client_id"]
            )
            
            # 验证issuer
            if id_info['iss'] not in ['accounts.google.com', 'https://accounts.google.com']:
                raise UserInfoError("无效的Google ID Token issuer", "invalid_issuer", "google")
            
            # 获取详细用户信息
            headers = {"Authorization": f"Bearer {access_token}"}
            user_response = await self.handle_http_request(
                "GET",
                self.config["user_info_url"],
                params={"personFields": "names,emailAddresses,photos"},
                headers=headers
            )
            user_data = user_response.json()
            
            # 检查邮箱收集需求
            raw_email = id_info.get("email", "")
            processed_email, email_collection_needed = self.check_email_collection_needed(raw_email)
            
            # 返回标准化用户信息
            return {
                "provider": "google",
                "uid": str(id_info.get("sub", "")),
                "username": user_data.get("names", [{}])[0].get("displayName", ""),
                "email": processed_email,
                "avatar": user_data.get("photos", [{}])[0].get("url", ""),
                "email_collection_needed": email_collection_needed
            }
            
        except Exception as e:
            if isinstance(e, UserInfoError):
                raise
            raise UserInfoError(f"获取Google用户信息失败: {str(e)}", "user_info_failed", "google")
