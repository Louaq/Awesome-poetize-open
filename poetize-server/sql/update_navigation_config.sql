-- 导航栏配置迁移脚本
-- 将"百宝箱"导航项替换为三个独立的导航项：友人帐、曲乐、收藏夹
-- 
-- 执行时间：请在部署新版本后执行此脚本
-- 影响范围：web_info 表的 nav_config 字段

-- 1. 显示当前导航配置状态
SELECT 
    COUNT(*) as total_records,
    SUM(CASE WHEN nav_config LIKE '%百宝箱%' THEN 1 ELSE 0 END) as has_treasure_box,
    SUM(CASE WHEN nav_config IS NULL OR nav_config = '' OR nav_config = '[]' THEN 1 ELSE 0 END) as empty_config
FROM web_info;

-- 更新导航配置
UPDATE web_info 
SET nav_config = '[
  {"name":"首页","icon":"🏡","link":"/","type":"internal","order":1,"enabled":true},
  {"name":"记录","icon":"📒","link":"#","type":"dropdown","order":2,"enabled":true},
  {"name":"家","icon":"❤️‍🔥","link":"/love","type":"internal","order":3,"enabled":true},
  {"name":"友人帐","icon":"🤝","link":"/friends","type":"internal","order":4,"enabled":true},
  {"name":"曲乐","icon":"🎵","link":"/music","type":"internal","order":5,"enabled":true},
  {"name":"收藏夹","icon":"📁","link":"/favorites","type":"internal","order":6,"enabled":true},
  {"name":"留言","icon":"📪","link":"/message","type":"internal","order":7,"enabled":true},
  {"name":"联系我","icon":"💬","link":"#chat","type":"special","order":8,"enabled":true}
]'
WHERE nav_config IS NOT NULL 
  AND nav_config != '' 
  AND nav_config != '[]'
  AND nav_config LIKE '%百宝箱%';

-- 对于空的导航配置，也设置为新的默认配置
UPDATE web_info 
SET nav_config = '[
  {"name":"首页","icon":"🏡","link":"/","type":"internal","order":1,"enabled":true},
  {"name":"记录","icon":"📒","link":"#","type":"dropdown","order":2,"enabled":true},
  {"name":"家","icon":"❤️‍🔥","link":"/love","type":"internal","order":3,"enabled":true},
  {"name":"友人帐","icon":"🤝","link":"/friends","type":"internal","order":4,"enabled":true},
  {"name":"曲乐","icon":"🎵","link":"/music","type":"internal","order":5,"enabled":true},
  {"name":"收藏夹","icon":"📁","link":"/favorites","type":"internal","order":6,"enabled":true},
  {"name":"留言","icon":"📪","link":"/message","type":"internal","order":7,"enabled":true},
  {"name":"联系我","icon":"💬","link":"#chat","type":"special","order":8,"enabled":true}
]'
WHERE nav_config IS NULL 
  OR nav_config = '' 
  OR nav_config = '[]';

-- 2. 验证更新结果
SELECT 
    id,
    CASE 
        WHEN nav_config LIKE '%友人帐%' AND nav_config LIKE '%曲乐%' AND nav_config LIKE '%收藏夹%' THEN '✅ 迁移成功'
        WHEN nav_config LIKE '%百宝箱%' THEN '❌ 迁移失败'
        ELSE '❓ 其他配置'
    END as migration_status,
    SUBSTRING(nav_config, 1, 200) as nav_config_preview
FROM web_info 
WHERE nav_config IS NOT NULL AND nav_config != ''
ORDER BY id;

-- 3. 显示迁移统计
SELECT 
    '迁移完成' as status,
    COUNT(*) as total_updated,
    SUM(CASE WHEN nav_config LIKE '%友人帐%' THEN 1 ELSE 0 END) as has_friends_nav,
    SUM(CASE WHEN nav_config LIKE '%百宝箱%' THEN 1 ELSE 0 END) as still_has_treasure_box
FROM web_info 
WHERE nav_config IS NOT NULL AND nav_config != '';
