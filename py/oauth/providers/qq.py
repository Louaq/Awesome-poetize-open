"""
QQ OAuth提供商实现
"""

from typing import Dict, Any
from ..base import OAuth2Provider
from ..exceptions import TokenError, UserInfoError


class QQProvider(OAuth2Provider):
    """QQ OAuth 2.0提供商"""
    
    def get_provider_name(self) -> str:
        return "qq"
    
    async def get_access_token(self, code: str) -> str:
        """获取QQ访问令牌"""
        try:
            response = await self.handle_http_request(
                "GET",
                self.config["token_url"],
                params={
                    "grant_type": "authorization_code",
                    "client_id": self.config["client_id"],
                    "client_secret": self.config["client_secret"],
                    "code": code,
                    "redirect_uri": self.config["redirect_uri"]
                }
            )
            
            # QQ返回的token不是标准JSON格式，而是类似 access_token=xxx&expires_in=xxx 的格式
            token_text = response.text
            token_data = {}
            
            for item in token_text.split('&'):
                if '=' in item:
                    key, value = item.split('=', 1)
                    token_data[key] = value
            
            access_token = token_data.get("access_token")
            
            if not access_token:
                raise TokenError("QQ未返回访问令牌", "no_token", "qq")
            
            return access_token
            
        except Exception as e:
            if isinstance(e, TokenError):
                raise
            raise TokenError(f"获取QQ访问令牌失败: {str(e)}", "token_request_failed", "qq")
    
    async def get_user_info(self, access_token: str) -> Dict[str, Any]:
        """获取QQ用户信息"""
        try:
            # 1. 首先通过access_token获取openid
            openid_response = await self.handle_http_request(
                "GET",
                self.config["openid_url"],
                params={"access_token": access_token}
            )
            
            # QQ返回的格式是callback( {"client_id":"xxx","openid":"xxx"} )
            openid_text = openid_response.text
            # 提取JSON部分
            openid_json = openid_text.strip()
            if openid_json.startswith('callback('):
                openid_json = openid_json[9:-1]  # 移除 callback( 和 最后的 )
            
            import json
            openid_data = json.loads(openid_json)
            openid = openid_data.get("openid")
            
            if not openid:
                raise UserInfoError("QQ未返回openid", "no_openid", "qq")
            
            # 2. 获取用户信息
            user_response = await self.handle_http_request(
                "GET",
                self.config["user_info_url"],
                params={
                    "access_token": access_token,
                    "oauth_consumer_key": self.config["client_id"],
                    "openid": openid
                }
            )
            
            user_info = user_response.json()
            
            # 检查返回状态
            if user_info.get("ret") != 0:
                error_msg = user_info.get("msg", "未知错误")
                raise UserInfoError(f"QQ返回用户信息错误: {error_msg}", "api_error", "qq")
            
            # QQ登录默认不返回邮箱，需要用户绑定
            email_collection_needed = True
            
            # 返回标准化用户信息
            return {
                "provider": "qq",
                "uid": openid,
                "username": user_info.get("nickname", ""),
                "email": "",  # QQ默认不提供邮箱
                "avatar": user_info.get("figureurl_qq_2") or user_info.get("figureurl_qq_1", ""),
                "email_collection_needed": email_collection_needed
            }
            
        except Exception as e:
            if isinstance(e, UserInfoError):
                raise
            raise UserInfoError(f"获取QQ用户信息失败: {str(e)}", "user_info_failed", "qq") 