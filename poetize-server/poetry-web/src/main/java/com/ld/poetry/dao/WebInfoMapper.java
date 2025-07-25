package com.ld.poetry.dao;

import com.ld.poetry.entity.WebInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 网站信息表 Mapper 接口
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@Mapper
public interface WebInfoMapper extends BaseMapper<WebInfo> {

    /**
     * 自定义更新网站信息方法，确保所有字段都能正确更新
     */
    @Update("UPDATE web_info SET " +
            "web_name = #{webInfo.webName}, " +
            "web_title = #{webInfo.webTitle}, " +
            "notices = #{webInfo.notices}, " +
            "footer = #{webInfo.footer}, " +
            "background_image = #{webInfo.backgroundImage}, " +
            "avatar = #{webInfo.avatar}, " +
            "random_avatar = #{webInfo.randomAvatar}, " +
            "random_name = #{webInfo.randomName}, " +
            "random_cover = #{webInfo.randomCover}, " +
            "waifu_json = #{webInfo.waifuJson}, " +
            "status = #{webInfo.status}, " +
            "enable_waifu = #{webInfo.enableWaifu}, " +
            "home_page_pull_up_height = #{webInfo.homePagePullUpHeight}, " +
            "api_enabled = #{webInfo.apiEnabled}, " +
            "api_key = #{webInfo.apiKey}, " +
            "nav_config = #{webInfo.navConfig}, " +
            "footer_background_image = #{webInfo.footerBackgroundImage}, " +
            "footer_background_config = #{webInfo.footerBackgroundConfig}, " +
            "email = #{webInfo.email}, " +
            "minimal_footer = #{webInfo.minimalFooter}, " +
            "enable_auto_night = #{webInfo.enableAutoNight}, " +
            "auto_night_start = #{webInfo.autoNightStart}, " +
            "auto_night_end = #{webInfo.autoNightEnd}, " +
            "enable_gray_mode = #{webInfo.enableGrayMode} " +
            "WHERE id = #{webInfo.id}")
    int updateWebInfoById(@Param("webInfo") WebInfo webInfo);

}
