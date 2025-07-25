#!/usr/bin/env python
# -*- coding: utf-8 -*-

import smtplib
import ssl
import json
import os
import socket
import socks
import httpx
from fastapi import FastAPI, Request, HTTPException, Depends
from config import BASE_BACKEND_URL, JAVA_CONFIG_URL
from auth_decorator import admin_required  # 导入管理员权限装饰器

# 邮箱配置文件路径
EMAIL_CONFIG_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'py', 'data', 'mail_configs.json')

# 数据存储路径
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data')
if not os.path.exists(DATA_DIR):
    os.makedirs(DATA_DIR)

def init_data_files():
    """初始化数据文件，如果文件存在但内容不正确也会修复

    从 web_admin_api.py 迁移而来，确保邮件配置文件正确初始化
    """
    # 确保数据目录存在
    if not os.path.exists(DATA_DIR):
        os.makedirs(DATA_DIR)

    # 初始化邮箱配置文件
    email_config_file = os.path.join(DATA_DIR, 'mail_configs.json')
    if not os.path.exists(email_config_file):
        with open(email_config_file, 'w', encoding='utf-8') as f:
            json.dump({"configs": [], "defaultIndex": -1}, f, ensure_ascii=False)
    else:
        # 检查文件内容是否正确
        try:
            with open(email_config_file, 'r', encoding='utf-8') as f:
                data = json.load(f)
                # 如果缺少必要字段，更新文件
                if not all(key in data for key in ["configs", "defaultIndex"]):
                    data = {"configs": data.get("configs", []), "defaultIndex": data.get("defaultIndex", -1)}
                    with open(email_config_file, 'w', encoding='utf-8') as f:
                        json.dump(data, f, ensure_ascii=False)
        except:
            # 如果文件损坏，重新创建
            with open(email_config_file, 'w', encoding='utf-8') as f:
                json.dump({"configs": [], "defaultIndex": -1}, f, ensure_ascii=False)

def get_email_configs():
    """获取所有邮箱配置（统一JSON缓存）"""
    try:
        from json_config_cache import get_json_config_cache
        json_cache = get_json_config_cache()

        # 使用统一的JSON配置缓存
        email_data = json_cache.get_json_config('mail_configs', EMAIL_CONFIG_FILE)
        if email_data:
            print("从统一缓存获取邮箱配置")
            return email_data.get("configs", [])
        else:
            print("邮箱配置文件不存在")
            return []
    except Exception as e:
        print(f"获取邮箱配置出错: {e}")
        # 降级到直接文件读取
        try:
            if os.path.exists(EMAIL_CONFIG_FILE):
                with open(EMAIL_CONFIG_FILE, 'r', encoding='utf-8') as f:
                    email_data = json.load(f)
                    return email_data.get("configs", [])
        except Exception as file_e:
            print(f"直接文件读取也失败: {file_e}")
        return []

def save_email_configs(configs, default_index=None):
    """保存邮箱配置

    Args:
        configs: 邮箱配置列表
        default_index: 默认邮箱索引

    Returns:
        bool: 保存是否成功
    """
    try:
        import os
        import json

        # 确保数据目录存在
        config_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data')
        if not os.path.exists(config_dir):
            os.makedirs(config_dir)

        # 修改为与Java端一致的配置文件名
        config_file = os.path.join(config_dir, 'mail_configs.json')

        # 读取现有配置以获取当前的默认索引
        current_config = {"configs": [], "defaultIndex": -1}
        if os.path.exists(config_file):
            with open(config_file, 'r', encoding='utf-8') as f:
                current_config = json.load(f)

        # 如果未提供默认索引，则使用当前的默认索引
        if default_index is None:
            default_index = current_config.get("defaultIndex", -1)

        # 为每个配置添加或更新ID，确保高级配置参数被保存
        for i, config in enumerate(configs):
            if 'id' not in config:
                config['id'] = i + 1

            # 确保高级配置参数存在
            if 'connectionTimeout' not in config:
                config['connectionTimeout'] = 25000
            if 'timeout' not in config:
                config['timeout'] = 25000
            if 'jndiName' not in config:
                config['jndiName'] = ''
            if 'trustAllCerts' not in config:
                config['trustAllCerts'] = False
            if 'customProperties' not in config:
                config['customProperties'] = {}

        # 构建保存的数据结构
        save_data = {
            "configs": configs,
            "defaultIndex": default_index
        }

        # 保存到文件
        with open(config_file, 'w', encoding='utf-8') as f:
            json.dump(save_data, f, ensure_ascii=False, indent=2)

        print(f"邮箱配置保存成功: {len(configs)} 个配置，默认索引: {default_index}")
        return True

    except Exception as e:
        print(f"保存邮箱配置失败: {str(e)}")
        return False

def get_random_email_config():
    """获取随机邮箱配置，用于发送验证码等邮件"""
    configs = get_email_configs()

    # 过滤出已启用的邮箱配置
    enabled_configs = [config for config in configs if config.get("enabled", False)]

    if not enabled_configs:
        # 如果没有启用的配置，尝试使用默认配置
        try:
            from cache_service import get_cache_service
            cache_service = get_cache_service()
            cached_config = cache_service.get_cached_email_config()
            if cached_config:
                default_index = cached_config.get("defaultIndex", -1)
                if default_index >= 0 and default_index < len(configs):
                    print(f"没有启用的邮箱配置，使用默认配置 (索引: {default_index})")
                    return configs[default_index]
        except Exception as e:
            print(f"获取默认邮箱配置失败: {e}")
        return None

    # 随机选择一个启用的配置
    import random
    selected_config = random.choice(enabled_configs)
    print(f"随机选择邮箱配置: {selected_config.get('username', 'unknown')}")
    return selected_config

def get_mail_config(config_id=None):
    """获取指定ID的邮箱配置，如果未指定ID则返回默认配置"""
    try:
        configs = get_email_configs()
        if not configs:
            return None
        
        # 如果指定了配置ID，查找对应配置
        if config_id:
            for config in configs:
                if str(config.get('id', '')) == str(config_id):
                    return config
        
        # 未指定ID或未找到指定ID的配置，返回默认配置
        for config in configs:
            if config.get('isDefault', False):
                return config
        
        # 如果没有默认配置，返回第一个
        return configs[0] if configs else None
    except Exception as e:
        print(f"获取邮箱配置出错: {str(e)}")
        return None

def create_smtp_connection(config):
    """根据配置创建SMTP连接（仅用于测试）"""
    if not config:
        raise ValueError("邮箱配置不能为空")
    
    # 获取基本配置
    host = config.get('host', '')
    port = int(config.get('port', 25))
    username = config.get('username', '')
    password = config.get('password', '')
    
    # SSL配置处理 - 兼容前端不同格式
    ssl_enabled = False
    if 'ssl' in config:
        # 处理可能的各种布尔值表示
        ssl_val = config.get('ssl')
        if isinstance(ssl_val, bool):
            ssl_enabled = ssl_val
        elif isinstance(ssl_val, str) and ssl_val.lower() in ('true', '1', 'yes'):
            ssl_enabled = True
        elif ssl_val in (1, True):
            ssl_enabled = True
    
    # 根据端口自动确定SSL - 端口465通常要求SSL
    port_requires_ssl = False
    if port == 465:
        port_requires_ssl = True
        print(f"端口{port}通常需要SSL连接，自动启用SSL")
    
    # 如果端口要求SSL但配置中未启用，强制启用SSL
    if port_requires_ssl and not ssl_enabled:
        ssl_enabled = True
        print("由于端口要求，已强制启用SSL")
    
    # 获取高级配置
    protocol = config.get('protocol', 'SMTP').upper()
    # 增加默认超时时间，避免连接过快超时
    connection_timeout = int(config.get('connectionTimeout', 60))
    
    print(f"尝试连接邮箱服务器: {host}:{port}, 协议:{protocol}, SSL:{ssl_enabled}, 超时:{connection_timeout}秒")
    
    # 代理设置
    proxy_host = config.get('proxyHost', '')
    proxy_port = int(config.get('proxyPort', 0)) if config.get('proxyPort') else 0
    proxy_type = config.get('proxyType', '').upper()
    
    # 如果启用了代理
    if proxy_host and proxy_port > 0 and proxy_type:
        print(f"使用代理: {proxy_type}://{proxy_host}:{proxy_port}")
        if proxy_type == 'SOCKS5':
            socks.set_default_proxy(socks.SOCKS5, proxy_host, proxy_port)
        elif proxy_type == 'SOCKS4':
            socks.set_default_proxy(socks.SOCKS4, proxy_host, proxy_port)
        elif proxy_type == 'HTTP':
            socks.set_default_proxy(socks.HTTP, proxy_host, proxy_port)
        socket.socket = socks.socksocket
    
    # 创建连接
    try:
        # 根据端口和SSL设置选择连接方式
        if ssl_enabled:
            print(f"创建SSL连接(端口:{port})...")
            context = ssl.create_default_context()
            server = smtplib.SMTP_SSL(host, port, context=context, timeout=connection_timeout)
        else:
            # 端口587通常使用STARTTLS
            if port == 587:
                print(f"创建普通连接(端口:{port})，将尝试STARTTLS...")
                server = smtplib.SMTP(host, port, timeout=connection_timeout)
                try:
                    server.ehlo()
                    server.starttls()
                    server.ehlo()
                    print("STARTTLS连接成功建立")
                except Exception as e:
                    print(f"STARTTLS失败: {str(e)}，继续使用普通连接")
            else:
                print(f"创建普通连接(端口:{port})...")
                server = smtplib.SMTP(host, port, timeout=connection_timeout)
        
        # 登录邮箱
        if username and password:
            print(f"尝试登录: {username}")
            server.login(username, password)
            print("登录成功!")
        
        return server
    except socket.timeout:
        print(f"连接超时，请检查服务器地址和端口是否正确，超时时间({connection_timeout}秒)是否足够")
        raise
    except ssl.SSLError as e:
        print(f"SSL错误: {str(e)}，请检查SSL设置是否正确")
        raise
    except smtplib.SMTPAuthenticationError:
        print("认证失败，用户名或密码错误")
        raise
    except smtplib.SMTPException as e:
        print(f"SMTP错误: {str(e)}")
        raise

def test_email_config(config):
    """测试邮箱配置是否有效（仅测试连接）
    
    Args:
        config: 邮箱配置对象
    
    Returns:
        成功返回(True, "连接成功")，失败返回(False, "错误信息")
    """
    try:
        if not config:
            return False, "邮箱配置不能为空"
        
        # 检查必填字段
        if not config.get('host'):
            return False, "邮箱服务器地址不能为空"
        
        if not config.get('port'):
            return False, "邮箱服务器端口不能为空"
            
        if not config.get('username') or not config.get('password'):
            return False, "用户名和密码不能为空"
        
        # 创建连接并测试
        print("开始测试邮箱连接...")
        server = create_smtp_connection(config)
        server.quit()
        print("邮箱连接测试成功!")
        
        # 连接成功，提供测试邮件发送失败的可能原因和解决方案
        port = config.get('port')
        host = config.get('host').lower()
        
        # 构建邮箱服务器特定建议
        email_provider_tips = ""
        if "163.com" in host or "126.com" in host:
            email_provider_tips = "网易邮箱提示：请确认是否已开启POP3/SMTP服务，并使用授权码而非登录密码。"
        elif "qq.com" in host:
            email_provider_tips = "QQ邮箱提示：请确认是否已开启POP3/SMTP服务，并使用授权码而非QQ密码。"
        elif "gmail.com" in host:
            email_provider_tips = "Gmail提示：请确认是否已开启两步验证并创建应用专用密码。"
        elif "outlook.com" in host or "hotmail.com" in host:
            email_provider_tips = "Outlook提示：请确认是否已在Outlook安全设置中允许不太安全的应用访问。"
        elif "aliyun.com" in host:
            email_provider_tips = "阿里云邮箱提示：请确认是否已开启POP3/SMTP服务，并使用授权码。"
        
        # 构建端口特定建议
        port_tips = ""
        if port == 465:
            port_tips = "465端口要求使用SSL加密，请确认已启用SSL选项。"
        elif port == 587:
            port_tips = "587端口通常使用STARTTLS，请尝试启用STARTTLS选项。"
        elif port == 25:
            port_tips = "25端口是非加密端口，许多ISP和企业网络可能阻止此端口，建议使用465(SSL)或587(STARTTLS)端口。"
        
        # 构建安全提示
        security_tips = "如果发送测试邮件失败，可能原因：\n" + \
                      "1. 邮箱密码可能输入错误或需要使用应用专用密码/授权码\n" + \
                      "2. 邮箱安全设置可能禁止第三方应用程序访问\n" + \
                      "3. 企业防火墙可能阻止了邮件发送\n" + \
                      "4. SSL/TLS配置可能不正确"
        
        # 组合提示信息
        success_msg = f"邮箱服务器连接成功!\n\n{email_provider_tips}\n{port_tips}\n\n{security_tips}"
        
        return True, success_msg
    except socket.timeout:
        err_msg = "连接邮箱服务器超时，请检查服务器地址和端口设置是否正确，网络连接是否正常"
        print(err_msg)
        return False, err_msg
    except ssl.SSLError as e:
        err_msg = f"SSL/TLS连接错误: {str(e)}，请检查SSL设置是否正确"
        print(err_msg)
        return False, err_msg
    except smtplib.SMTPAuthenticationError:
        err_msg = "认证失败，用户名或密码错误。请检查：\n" + \
                 "1. 密码是否正确\n" + \
                 "2. 是否需要使用应用专用密码/授权码而非登录密码\n" + \
                 "3. 邮箱是否已开启SMTP服务"
        print(err_msg)
        return False, err_msg
    except smtplib.SMTPConnectError as e:
        err_msg = f"无法连接到邮箱服务器: {str(e)}"
        print(err_msg)
        return False, err_msg
    except smtplib.SMTPException as e:
        err_msg = f"SMTP错误: {str(e)}"
        print(err_msg)
        return False, err_msg
    except Exception as e:
        err_msg = f"测试邮箱配置失败: {str(e)}"
        print(err_msg)
        return False, err_msg

# 注册邮件相关API路由
def register_email_api(app: FastAPI):
    # 确保数据文件存在
    init_data_files()

    # ============================================================================
    # 邮件配置管理API端点
    # ============================================================================

    @app.get('/webInfo/getEmailConfigs')
    async def get_email_configs_api():
        """获取邮箱配置（带缓存优化）"""
        print("收到获取邮箱配置请求")
        try:
            # 使用缓存实现
            email_configs = get_email_configs()
            print(f"返回邮箱配置数据（已缓存优化）: {len(email_configs) if email_configs else 0} 个配置")
            return {
                "code": 200,
                "message": "获取成功",
                "data": email_configs
            }
        except Exception as e:
            print(f"获取邮箱配置失败: {e}")
            return {
                "code": 500,
                "message": f"获取邮箱配置失败: {str(e)}",
                "data": []
            }

    @app.get('/webInfo/getDefaultMailConfig')
    async def get_default_mail_config():
        """获取默认邮箱配置索引（带缓存优化）"""
        print("收到获取默认邮箱配置索引请求")
        try:
            # 使用统一JSON缓存获取邮件配置
            from json_config_cache import get_json_config_cache
            json_cache = get_json_config_cache()

            email_data = json_cache.get_json_config('mail_configs', EMAIL_CONFIG_FILE)
            if email_data:
                default_index = email_data.get("defaultIndex", -1)
                print(f"从统一缓存返回默认邮箱配置索引: {default_index}")
                return {
                    "code": 200,
                    "message": "获取成功",
                    "data": default_index
                }
        except Exception as e:
            print(f"从统一缓存获取默认邮箱索引失败: {e}")

        # 降级到文件读取
        try:
            if os.path.exists(EMAIL_CONFIG_FILE):
                with open(EMAIL_CONFIG_FILE, 'r', encoding='utf-8') as f:
                    email_data = json.load(f)
                    default_index = email_data.get('defaultIndex', -1)
                    print(f"从文件返回默认邮箱配置索引: {default_index}")
                    return {
                        "code": 200,
                        "message": "获取成功",
                        "data": default_index
                    }
        except Exception as e:
            print(f"从文件获取默认邮箱索引失败: {e}")

        return {
            "code": 200,
            "message": "获取成功",
            "data": -1
        }

    @app.post('/webInfo/saveEmailConfigs')
    async def save_email_configs_api(request: Request, _: bool = Depends(admin_required)):
        """保存邮箱配置API"""
        try:
            # 获取URL中的默认索引参数
            default_index_str = request.query_params.get('defaultIndex', '-1')
            try:
                default_index = int(default_index_str)
            except ValueError:
                default_index = -1

            # 获取请求体中的配置数据
            configs = await request.json()
            print(f"收到保存邮箱配置请求，配置数量: {len(configs)}, 默认索引: {default_index}")

            # 验证配置数据
            if not isinstance(configs, list):
                raise HTTPException(status_code=400, detail={
                    "code": 400,
                    "message": "配置数据格式错误，应为数组",
                    "data": None
                })

            # 为每个配置添加必要的字段和默认值
            for config in configs:
                # 确保必要字段存在
                if 'enabled' not in config:
                    config['enabled'] = True
                if 'customProperties' not in config:
                    config['customProperties'] = config.get('customProperties', {})

            save_result = save_email_configs(configs, default_index)

            if save_result:
                # 使用统一JSON缓存管理器刷新缓存
                try:
                    from json_config_cache import get_json_config_cache
                    json_cache = get_json_config_cache()
                    json_cache.invalidate_json_cache('mail_configs')
                    print("邮件配置缓存已刷新")
                except Exception as cache_e:
                    print(f"刷新邮件配置缓存失败: {cache_e}")
                    # 缓存刷新失败不影响保存成功的状态

                print(f"邮箱配置保存成功，共{len(configs)}个配置，默认索引: {default_index}")
                return {
                    "code": 200,
                    "message": "保存成功",
                    "data": None
                }
            else:
                print(f"邮箱配置保存失败")
                raise HTTPException(status_code=500, detail={
                    "code": 500,
                    "message": "保存失败",
                    "data": None
                })
        except HTTPException:
            raise
        except Exception as e:
            print(f"保存邮箱配置出错: {str(e)}")
            import traceback
            traceback.print_exc()
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"保存失败: {str(e)}",
                "data": None
            })

    # ============================================================================
    # 邮件测试API端点
    # ============================================================================
    @app.post('/webInfo/testEmailConfig')
    async def test_email_config_api(request: Request, _: bool = Depends(admin_required)):
        """测试邮箱配置API - 前端使用，优先调用Java API，提供降级机制"""
        print("接收到测试邮箱配置请求")
        try:
            config = await request.json()
            if not config:
                print("配置信息为空")
                raise HTTPException(status_code=400, detail={"code": 400, "message": "配置信息不能为空"})
            
            # 首先尝试调用Java后端的邮件测试接口
            try:
                # 获取测试邮箱地址
                # 优先使用URL参数中的testEmail，其次使用form中的testTo，最后使用自己的邮箱
                test_email = None
                if 'testEmail' in request.query_params and request.query_params.get('testEmail'):
                    test_email = request.query_params.get('testEmail')
                elif 'testTo' in config:
                    test_email = config.get('testTo')
                
                # 如果没有指定测试邮箱，默认使用配置中的用户名
                if not test_email:
                    test_email = config.get('username', '')
                
                # 修复配置中的布尔值，确保不为null
                # 端口465强制设置SSL=true
                if config.get('port') == 465:
                    config['ssl'] = True
                # 端口587强制设置starttls=true（仅当未明确设置时）
                elif config.get('port') == 587 and 'starttls' not in config:
                    config['starttls'] = True
                    
                # 确保必要的布尔属性不为null，但不覆盖已有值
                for key in ['ssl', 'starttls', 'auth', 'enabled', 'trustAllCerts']:
                    # 只在属性未设置或为null时应用默认值，保留false值
                    if key not in config or config[key] is None:
                        # 根据前端值添加默认值（保留前端的false值）
                        if key == 'ssl':
                            config[key] = False  # 默认SSL关闭
                        elif key == 'starttls':
                            config[key] = False  # 默认STARTTLS关闭
                        elif key == 'auth':
                            config[key] = True   # 默认认证开启
                        elif key == 'enabled':
                            config[key] = True   # 默认启用
                        elif key == 'trustAllCerts':
                            config[key] = False  # 默认不信任所有证书
                
                print(f"修正后的配置: {config}")
                
                # 构建测试数据
                test_data = {
                    "testEmail": test_email,
                    "config": config
                }
                
                # 使用完整的URL格式
                if BASE_BACKEND_URL.startswith("http"):
                    java_test_url = f"{BASE_BACKEND_URL}/api/mail/test"
                else:
                    java_test_url = f"http://{BASE_BACKEND_URL}/api/mail/test"
                    
                print(f"转发测试请求到Java后端: {java_test_url}")
                print(f"测试邮箱: {test_email}")
                
                # 发送请求到Java后端，使用更长的超时时间
                async with httpx.AsyncClient() as client:
                    response = await client.post(
                        java_test_url, 
                        json=test_data,
                        headers={"Content-Type": "application/json"},
                        timeout=60  # 延长超时时间到60秒
                    )
                
                print(f"Java后端响应状态码: {response.status_code}")
                
                # 尝试解析响应
                try:
                    java_result = response.json()
                    print(f"Java后端测试结果: {java_result}")
                    
                    # 检查Java返回的结果
                    if java_result.get("code") == 200:
                        return {"code": 200, "message": "邮箱配置测试成功，测试邮件已发送"}
                    else:
                        error_msg = java_result.get('message', '未知错误')
                        print(f"Java返回错误: {error_msg}")
                        
                        # 构建更详细的错误消息
                        detailed_error = "测试邮件发送失败: " + error_msg + "\n\n"
                        
                        # 根据常见错误提供建议
                        if "Authentication failed" in error_msg or "认证失败" in error_msg:
                            detailed_error += "● 可能原因：\n" + \
                                            "1. 邮箱密码错误\n" + \
                                            "2. 需要使用应用专用密码/授权码而非登录密码\n" + \
                                            "3. 邮箱未开启SMTP服务\n\n" + \
                                            "● 解决方案：\n" + \
                                            "- 企业邮箱请联系IT管理员获取正确的SMTP设置\n" + \
                                            "- 个人邮箱请登录邮箱网页版，在设置中开启SMTP服务并获取授权码"
                        elif "连接超时" in error_msg or "timeout" in error_msg.lower():
                            detailed_error += "● 可能原因：\n" + \
                                            "1. 服务器地址或端口错误\n" + \
                                            "2. 网络环境阻止了SMTP连接\n" + \
                                            "3. 邮箱服务器暂时不可用\n\n" + \
                                            "● 解决方案：\n" + \
                                            "- 确认服务器地址和端口是否正确\n" + \
                                            "- 检查防火墙是否阻止了SMTP连接\n" + \
                                            "- 稍后再试"
                        elif "ssl" in error_msg.lower() or "tls" in error_msg.lower():
                            detailed_error += "● 可能原因：\n" + \
                                            "1. SSL/TLS设置错误\n" + \
                                            "2. 端口与加密方式不匹配\n\n" + \
                                            "● 解决方案：\n" + \
                                            "- 端口465应使用SSL加密\n" + \
                                            "- 端口587应使用STARTTLS\n" + \
                                            "- 端口25通常不使用加密（不推荐）"
                        
                        return {
                            "code": 500, 
                            "message": detailed_error
                        }
                except ValueError as e:
                    print(f"解析Java响应JSON失败: {str(e)}, 响应内容: {response.text[:200]}")
                    return {
                        "code": 500,
                        "message": f"无法理解Java响应: {response.text[:100]}..."
                    }
                    
            except httpx.ConnectError as e:
                print(f"连接Java后端失败: {str(e)}")
                print(f"Java后端URL: {BASE_BACKEND_URL}")
                # 如果Java端连接失败，使用本地测试连接
                print("使用本地SMTP连接测试作为备选方案...")
            except httpx.TimeoutException as e:
                print(f"调用Java后端超时: {str(e)}")
                print("使用本地SMTP连接测试作为备选方案...")
            except Exception as e:
                print(f"调用Java后端测试接口失败: {str(e)}")
                print("使用本地SMTP连接测试作为备选方案...")
            
            # 备选方案：本地测试SMTP连接
            success, message = test_email_config(config)
            code = 200 if success else 500
            
            # 对于超时错误，使用特定错误码以便前端可以提供更细致的错误信息
            if not success and "超时" in message:
                code = 504  # Gateway Timeout
            
            if success:
                message = "邮箱服务器连接成功，但未发送测试邮件。若要验证完整功能，请在Java端检查日志获取详细错误信息。连接成功不代表邮件一定能发送成功，可能仍需要正确配置授权码或应用专用密码。"
            
            print(f"本地测试结果: {'成功' if success else '失败'}, 消息: {message}")
            return {"code": code, "message": message}
        except HTTPException:
            raise
        except Exception as e:
            error_message = f"测试邮箱配置失败: {str(e)}"
            print(error_message)
            raise HTTPException(status_code=500, detail={"code": 500, "message": error_message})

    @app.get('/api/debug/config')
    async def debug_config():
        """调试配置信息API"""
        config_info = {
            "java_backend_url": BASE_BACKEND_URL,
            "java_config_url": JAVA_CONFIG_URL,
            "mail_test_url": f"{BASE_BACKEND_URL}/api/mail/test"
        }
        
        print(f"调试配置信息: {config_info}")
        
        # 尝试测试连接Java后端
        try:
            test_url = f"{BASE_BACKEND_URL}/api/mail/test"
            async with httpx.AsyncClient() as client:
                response = await client.get(test_url, timeout=2)
            config_info["java_connection_test"] = {
                "status_code": response.status_code,
                "reachable": True,
                "response": response.text[:100] if response.text else ""
            }
        except Exception as e:
            config_info["java_connection_test"] = {
                "error": str(e),
                "reachable": False
            }
        
        return {"code": 200, "message": "调试配置信息", "data": config_info}

    @app.post('/api/cache/refreshEmailCache')
    async def refresh_email_cache(request: Request, _: bool = Depends(admin_required)):
        """手动刷新邮件配置缓存"""
        try:
            from cache_refresh_service import get_cache_refresh_service
            refresh_service = get_cache_refresh_service()
            refresh_result = refresh_service.refresh_email_caches()

            return {
                "code": 200,
                "message": "邮件缓存刷新完成",
                "data": refresh_result
            }
        except Exception as e:
            return {
                "code": 500,
                "message": f"刷新邮件缓存失败: {str(e)}",
                "data": None
            }

    @app.post('/api/debug/setBackendUrl')
    async def set_backend_url(request: Request):
        """手动设置Java后端URL（仅用于调试）"""
        try:
            data = await request.json()
            if not data or 'url' not in data:
                raise HTTPException(status_code=400, detail={"code": 400, "message": "请提供url参数", "data": None})
            
            new_url = data['url']
            if not new_url.startswith('http'):
                new_url = f"http://{new_url}"
            
            # 临时修改全局变量
            global BASE_BACKEND_URL
            BASE_BACKEND_URL = new_url
            
            # 验证连接
            test_result = {}
            try:
                test_url = f"{BASE_BACKEND_URL}/api/mail/test"
                async with httpx.AsyncClient() as client:
                    response = await client.get(test_url, timeout=2)
                test_result = {
                    "status_code": response.status_code,
                    "reachable": True
                }
                message = f"成功设置Java后端URL为: {new_url}，连接测试成功"
            except Exception as e:
                test_result = {
                    "error": str(e),
                    "reachable": False
                }
                message = f"已设置Java后端URL为: {new_url}，但连接测试失败: {str(e)}"
            
            print(message)
            return {
                "code": 200, 
                "message": message,
                "data": {
                    "url": new_url,
                    "test": test_result
                }
            }
        except HTTPException:
            raise
        except Exception as e:
            raise HTTPException(status_code=500, detail={
                "code": 500,
                "message": f"设置失败: {str(e)}",
                "data": None
            })

