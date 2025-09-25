
-- 获取响应体片段和结束标志
local chunk = ngx.arg[1]
local eof = ngx.arg[2]

-- 如果chunk为空或nil，直接返回（这是nginx body_filter的正常行为）
if not chunk or chunk == "" then
    return
end

-- 移除默认的错误图标标签（IM页面也可能有默认图标）
if not ngx.ctx.default_icons_removed then
    chunk = string.gsub(chunk, '<link rel=icon href=%.?/?poetize%.jpg[^>]*>', "")
    chunk = string.gsub(chunk, '<link[^>]*href=["\']%.?/?poetize%.jpg["\'][^>]*>', "")
    chunk = string.gsub(chunk, '<link[^>]*id=["\']default%-favicon["\'][^>]*>', "")
    ngx.ctx.default_icons_removed = true
end

-- 简单的IM title替换
if ngx.ctx.title and ngx.ctx.title ~= "" and not ngx.ctx.title_injected then
    -- 简单替换title标签内容
    local title_replaced = false
    
    chunk = string.gsub(chunk, "<title[^>]*>(.-)</title>", function()
        title_replaced = true
        return "<title>" .. ngx.ctx.title .. "</title>"
    end, 1)
    
    -- 如果没有找到title标签，在head标签后添加
    if not title_replaced then
        chunk = string.gsub(chunk, "<head>", "<head><title>" .. ngx.ctx.title .. "</title>", 1)
    end
    
    ngx.ctx.title_injected = true
end

ngx.arg[1] = chunk
