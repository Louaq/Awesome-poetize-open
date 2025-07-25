"""
Twitter/X OAuth提供商实现
"""

from typing import Dict, Any
from urllib.parse import parse_qs
from oauthlib.oauth1 import Client
from ..base import OAuth1Provider
from ..exceptions import TokenError, UserInfoError


class TwitterProvider(OAuth1Provider):
    """Twitter/X OAuth 1.0提供商"""
    
    def get_provider_name(self) -> str:
        return "x"
    
    def get_auth_url(self, state: str) -> str:
        """
        Twitter OAuth 1.0需要先获取request token
        这个方法返回的是获取request token的信息
        实际的auth_url需要在获取request token后生成
        """
        # 注意：Twitter OAuth 1.0的流程与OAuth 2.0不同
        # 这里返回配置信息，实际URL在oauth_login中生成
        return self.config["request_token_url"]
    
    async def get_request_token(self, callback_uri: str) -> Dict[str, str]:
        """
        获取Twitter请求令牌
        
        Args:
            callback_uri: 回调URI
            
        Returns:
            Dict[str, str]: 包含oauth_token和oauth_token_secret
        """
        try:
            client = Client(self.config["client_key"], self.config["client_secret"])
            uri, headers, body = client.sign(
                self.config["request_token_url"],
                http_method="POST",
                callback_uri=callback_uri
            )
            
            response = await self.handle_http_request("POST", uri, headers=headers, data=body)
            request_token = parse_qs(response.text)
            
            oauth_token = request_token.get("oauth_token", [None])[0]
            oauth_token_secret = request_token.get("oauth_token_secret", [None])[0]
            
            if not oauth_token or not oauth_token_secret:
                raise TokenError("Twitter未返回请求令牌", "no_request_token", "x")
            
            return {
                "oauth_token": oauth_token,
                "oauth_token_secret": oauth_token_secret
            }
            
        except Exception as e:
            if isinstance(e, TokenError):
                raise
            raise TokenError(f"获取Twitter请求令牌失败: {str(e)}", "request_token_failed", "x")
    
    async def get_access_token(self, oauth_token: str, oauth_token_secret: str, oauth_verifier: str) -> Dict[str, str]:
        """
        获取Twitter访问令牌
        
        Args:
            oauth_token: OAuth token
            oauth_token_secret: OAuth token secret
            oauth_verifier: OAuth verifier
            
        Returns:
            Dict[str, str]: 包含access_token和access_token_secret
        """
        try:
            client = Client(
                self.config["client_key"],
                self.config["client_secret"],
                resource_owner_key=oauth_token,
                resource_owner_secret=oauth_token_secret,
                verifier=oauth_verifier
            )
            
            uri, headers, body = client.sign(self.config["access_token_url"], http_method="POST")
            response = await self.handle_http_request("POST", uri, headers=headers, data=body)
            
            access_data = parse_qs(response.text)
            access_token = access_data.get("oauth_token", [None])[0]
            access_token_secret = access_data.get("oauth_token_secret", [None])[0]
            
            if not access_token or not access_token_secret:
                raise TokenError("Twitter未返回访问令牌", "no_access_token", "x")
            
            return {
                "access_token": access_token,
                "access_token_secret": access_token_secret
            }
            
        except Exception as e:
            if isinstance(e, TokenError):
                raise
            raise TokenError(f"获取Twitter访问令牌失败: {str(e)}", "access_token_failed", "x")
    
    async def get_user_info(self, access_token: str, access_token_secret: str = None) -> Dict[str, Any]:
        """
        获取Twitter用户信息
        
        Args:
            access_token: 访问令牌
            access_token_secret: 访问令牌密钥（Twitter OAuth 1.0需要）
        """
        try:
            if not access_token_secret:
                raise UserInfoError("Twitter OAuth 1.0需要access_token_secret", "missing_token_secret", "x")
            
            auth_client = Client(
                self.config["client_key"],
                self.config["client_secret"],
                resource_owner_key=access_token,
                resource_owner_secret=access_token_secret
            )
            
            user_info_url = f"{self.config['user_info_url']}?include_email=true"
            uri, headers, body = auth_client.sign(user_info_url)
            
            response = await self.handle_http_request("GET", uri, headers=headers)
            user_info = response.json()
            
            # 检查邮箱收集需求
            raw_email = user_info.get("email")
            processed_email, email_collection_needed = self.check_email_collection_needed(raw_email)
            
            # 返回标准化用户信息
            return {
                "provider": "x",
                "uid": user_info.get("id_str"),
                "username": user_info.get("screen_name"),
                "email": processed_email,
                "avatar": user_info.get("profile_image_url_https", "").replace("_normal", ""),
                "email_collection_needed": email_collection_needed
            }
            
        except Exception as e:
            if isinstance(e, UserInfoError):
                raise
            raise UserInfoError(f"获取Twitter用户信息失败: {str(e)}", "user_info_failed", "x")
