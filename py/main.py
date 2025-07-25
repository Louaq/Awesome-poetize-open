"""
POETIZE博客系统 - FastAPI后端服务
支持SEO优化、第三方登录、邮件发送等功能

主要功能模块：
- SEO优化和搜索引擎推送
- 第三方OAuth登录 (GitHub, Google, Twitter, Yandex, Gitee)
- 邮件配置管理和发送服务
- 智能验证码服务
- 多语言翻译
- AI聊天配置
"""

import os
from fastapi import FastAPI, Request
from fastapi.responses import RedirectResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from starlette.middleware.sessions import SessionMiddleware
from config import SECRET_KEY, JAVA_BACKEND_URL, FRONTEND_URL, JAVA_CONFIG_URL, PYTHON_SERVICE_PORT
from py_three_login import oauth_login, oauth_callback
from redis_oauth_state_manager import oauth_state_manager
from json_config_cache import get_json_config_cache

from email_api import register_email_api  # 仅处理邮箱配置和测试功能，实际邮件发送由Java后端处理
from captcha_api import register_captcha_api  # 处理滑动验证码配置功能
from seo_api import register_seo_api  # 处理SEO优化相关功能
from ai_chat_api import register_ai_chat_api  # 处理AI聊天配置功能
from translation_api import register_translation_api  # 处理翻译管理功能
from auth_decorator import admin_required  # 导入管理员权限验证装饰器
# 缓存预热服务已删除 - Java后端自动处理缓存
import logging
import asyncio

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 创建FastAPI应用实例
# 安全配置：禁用自动文档功能，避免在生产环境暴露API结构
app = FastAPI(
    title="POETIZE博客系统API",
    description="基于FastAPI的博客系统后端服务，提供SEO优化、第三方登录等功能",
    version="2.0.0",
    docs_url=None,        # 禁用Swagger UI文档 (/docs)
    redoc_url=None,       # 禁用ReDoc文档 (/redoc)
    openapi_url=None      # 禁用OpenAPI schema (/openapi.json)
)

# 添加Session中间件（必须在其他中间件之前添加）
app.add_middleware(
    SessionMiddleware,
    secret_key=SECRET_KEY,
    max_age=3600,  # 1小时过期
    same_site='lax',  # 允许跨站点请求携带cookie
    https_only=False  # 开发环境允许HTTP
)

# 配置CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
    max_age=3600
)

# 添加GZip压缩中间件，压缩响应数据以节省带宽
app.add_middleware(GZipMiddleware, minimum_size=1000)

@app.get("/")
async def index():
    return RedirectResponse(url=FRONTEND_URL)

# 高可用性检查接口
@app.get("/api/health")
async def health_check():
    """API健康检查端点，返回系统状态和版本信息"""
    return {
        'status': 'ok',
        'api_version': os.environ.get('VERSION', 'dev'),
        'app_version': os.environ.get('VERSION', 'dev'),
        'translation_model': 'integrated',  # 翻译功能已整合
    }

# 高可用性检查接口 - 同时支持旧路径
@app.get("/health")
async def health_check_old():
    """兼容旧的健康检查路径"""
    return await health_check()

# OAuth状态管理器调试端点
@app.get("/debug/oauth-states")
async def debug_oauth_states():
    """获取OAuth状态管理器的统计信息"""
    return oauth_state_manager.get_stats()



def register_all_apis(app):
    """注册所有API模块"""
    # 注册OAuth路由 - 支持多种路径格式
    app.add_api_route('/oauth/login/{provider}', oauth_login, methods=['GET'])
    app.add_api_route('/oauth/callback/{provider}', oauth_callback, methods=['GET'])

    # 兼容路由 - 支持前端直接调用的路径
    app.add_api_route('/login/{provider}', oauth_login, methods=['GET'])
    app.add_api_route('/callback/{provider}', oauth_callback, methods=['GET'])

    # 注册各个API模块
    register_email_api(app)
    register_captcha_api(app)
    register_seo_api(app)
    register_ai_chat_api(app)  # 注册AI聊天配置API
    register_translation_api(app)  # 注册翻译管理API

    logger.info("所有API模块已注册完成")

# ================================ 应用启动事件 ================================

@app.on_event("startup")
async def startup_event():
    """应用启动时的初始化操作"""
    logger.info("应用启动中，开始初始化...")
    
    # 预热JSON配置缓存
    try:
        logger.info("开始JSON配置缓存预热...")
        json_config_cache = get_json_config_cache()
        warmup_result = json_config_cache.warmup_all_caches()
        
        # 记录预热结果
        success_count = warmup_result['success_count']
        total_configs = warmup_result['total_configs']
        execution_time = warmup_result['execution_time_ms'] / 1000
        
        logger.info(f"JSON配置缓存预热完成: {success_count}/{total_configs} 成功, 耗时 {execution_time:.2f}秒")
        
        # 如果有失败的配置，记录警告
        if warmup_result['failed_count'] > 0:
            logger.warning(f"有 {warmup_result['failed_count']} 个配置预热失败")
            
            # 列出失败的配置
            failed_configs = [name for name, result in warmup_result['results'].items() 
                             if result.get('status') == 'failed' or result.get('status') == 'error']
            logger.warning(f"预热失败的配置: {', '.join(failed_configs)}")
    except Exception as e:
        # 即使预热失败也不应该中断应用启动
        logger.error(f"JSON配置缓存预热过程中发生异常: {e}")
        logger.info("应用将继续启动，但某些功能可能在首次访问时较慢")

    logger.info("应用启动完成")

@app.on_event("shutdown")
async def shutdown_event():
    """应用关闭时的清理操作"""
    logger.info("应用正在关闭...")
    logger.info("应用已关闭")

# 注册所有API
register_all_apis(app)

if __name__ == '__main__':
    import uvicorn
    
    port = int(os.environ.get('PORT', PYTHON_SERVICE_PORT))
    debug = os.environ.get('DEBUG', 'False').lower() == 'true'
    
    logger.info("==========================================")
    logger.info("POETIZE博客系统 - FastAPI后端服务启动中...")
    logger.info("==========================================")
    logger.info(f"服务端口: {port}")
    logger.info(f"调试模式: {debug}")
    logger.info(f"Java后端: {JAVA_BACKEND_URL}")
    logger.info(f"前端地址: {FRONTEND_URL}")
    logger.info("==========================================")
    
    uvicorn.run(
        "main:app", 
        host='0.0.0.0', 
        port=port, 
        reload=debug,
        log_level="info"
    ) 