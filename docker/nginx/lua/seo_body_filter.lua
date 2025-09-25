
-- 简化的body filter - 仅处理title标签
-- prerender已处理大部分SEO，这里只作为fallback

local chunk = ngx.arg[1]
if not chunk then return end

-- 确保只处理HTML内容
local content_type = ngx.header.content_type
if not content_type or not string.find(content_type, "text/html", 1, true) then
    return
end

-- 处理HTML lang属性（保留这个功能，因为可能有多语言需求）
if not ngx.ctx.lang_injected then
    local lang = ngx.var.arg_lang or ""
    if lang ~= "" then
        chunk = string.gsub(chunk, "<html([^>]*)>", function(attrs)
            if string.find(attrs, "lang=", 1, true) then
                attrs = string.gsub(attrs, 'lang="[^"]*"', 'lang="' .. lang .. '"')
            else
                attrs = ' lang="' .. lang .. '"' .. attrs
            end
            return "<html" .. attrs .. ">"
        end)
        ngx.ctx.lang_injected = true
    end
end

-- 移除默认的错误图标标签（因为可能不是正确的网站图标）
if not ngx.ctx.default_icons_removed then
    -- 移除默认图标标签
    chunk = string.gsub(chunk, '<link rel=icon href=%.?/?poetize%.jpg[^>]*>', "")
    chunk = string.gsub(chunk, '<link[^>]*href=["\']%.?/?poetize%.jpg["\'][^>]*>', "")
    chunk = string.gsub(chunk, '<link[^>]*id=["\']default%-favicon["\'][^>]*>', "")
    ngx.ctx.default_icons_removed = true
end

-- 简单的title替换（仅在没有prerender文件时使用）
if ngx.ctx.title and ngx.ctx.title ~= "" and not ngx.ctx.title_injected then
    -- 简单替换title标签内容
    local title_replaced = false
    
    -- 尝试替换现有title标签
    chunk = string.gsub(chunk, "<title[^>]*>(.-)</title>", function(existing_content)
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