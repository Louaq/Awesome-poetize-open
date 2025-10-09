package com.ld.poetry.im.http.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.im.http.entity.ImChatLastRead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 聊天最后查看时间 Mapper 接口（私聊+群聊）
 * </p>
 *
 * @author LeapYa
 * @since 2025-10-09
 */
@Mapper
public interface ImChatLastReadMapper extends BaseMapper<ImChatLastRead> {

    /**
     * 获取用户所有好友的未读消息数
     * 
     * @param userId 用户ID
     * @return Map<friendId, unreadCount>
     */
    @Select("SELECT " +
            "  f.friend_id, " +
            "  COUNT(m.id) as unread_count " +
            "FROM im_chat_user_friend f " +
            "LEFT JOIN im_chat_last_read lr ON lr.user_id = #{userId} AND lr.chat_type = 1 AND lr.chat_id = f.friend_id " +
            "LEFT JOIN im_chat_user_message m ON (m.from_id = f.friend_id AND m.to_id = #{userId}) " +
            "  AND m.create_time > COALESCE(lr.last_read_time, '1970-01-01') " +
            "WHERE f.user_id = #{userId} " +
            "  AND f.friend_status = 1 " +
            "GROUP BY f.friend_id")
    List<Map<String, Object>> getFriendUnreadCountsByUserId(@Param("userId") Integer userId);

    /**
     * 获取用户所有群的未读消息数
     * 
     * @param userId 用户ID
     * @return Map<groupId, unreadCount>
     */
    @Select("SELECT " +
            "  gu.group_id, " +
            "  COUNT(gm.id) as unread_count " +
            "FROM im_chat_group_user gu " +
            "LEFT JOIN im_chat_last_read lr ON lr.user_id = #{userId} AND lr.chat_type = 2 AND lr.chat_id = gu.group_id " +
            "LEFT JOIN im_chat_user_group_message gm ON gm.group_id = gu.group_id " +
            "  AND gm.create_time > COALESCE(lr.last_read_time, '1970-01-01') " +
            "  AND gm.from_id != #{userId} " +
            "WHERE gu.user_id = #{userId} " +
            "  AND gu.user_status IN (1, 2) " +
            "GROUP BY gu.group_id")
    List<Map<String, Object>> getGroupUnreadCountsByUserId(@Param("userId") Integer userId);

    /**
     * 获取单个好友的未读消息数
     * 
     * @param userId 用户ID
     * @param friendId 好友ID
     * @return 未读消息数
     */
    @Select("SELECT COUNT(*) " +
            "FROM im_chat_user_message m " +
            "LEFT JOIN im_chat_last_read lr ON lr.user_id = #{userId} AND lr.chat_type = 1 AND lr.chat_id = #{friendId} " +
            "WHERE m.from_id = #{friendId} AND m.to_id = #{userId} " +
            "  AND m.create_time > COALESCE(lr.last_read_time, '1970-01-01')")
    Integer getFriendUnreadCount(@Param("userId") Integer userId, @Param("friendId") Integer friendId);

    /**
     * 获取单个群的未读消息数
     * 
     * @param userId 用户ID
     * @param groupId 群ID
     * @return 未读消息数
     */
    @Select("SELECT COUNT(*) " +
            "FROM im_chat_user_group_message gm " +
            "LEFT JOIN im_chat_last_read lr ON lr.user_id = #{userId} AND lr.chat_type = 2 AND lr.chat_id = #{groupId} " +
            "WHERE gm.group_id = #{groupId} " +
            "  AND gm.create_time > COALESCE(lr.last_read_time, '1970-01-01') " +
            "  AND gm.from_id != #{userId}")
    Integer getGroupUnreadCount(@Param("userId") Integer userId, @Param("groupId") Integer groupId);

    /**
     * 获取用户的私聊列表（按最后查看时间倒序，过滤隐藏的聊天）
     * 
     * @param userId 用户ID
     * @return 好友ID列表
     */
    @Select("SELECT f.friend_id " +
            "FROM im_chat_user_friend f " +
            "LEFT JOIN im_chat_last_read lr " +
            "  ON lr.user_id = #{userId} AND lr.chat_type = 1 AND lr.chat_id = f.friend_id " +
            "WHERE f.user_id = #{userId} AND f.friend_status = 1 " +
            "  AND COALESCE(lr.is_hidden, 0) = 0 " +
            "ORDER BY COALESCE(lr.last_read_time, '1970-01-01') DESC")
    List<Integer> getFriendChatList(@Param("userId") Integer userId);

    /**
     * 获取用户的群聊列表（按最后查看时间倒序，过滤隐藏的聊天）
     * 
     * @param userId 用户ID
     * @return 群ID列表
     */
    @Select("SELECT gu.group_id " +
            "FROM im_chat_group_user gu " +
            "LEFT JOIN im_chat_last_read lr " +
            "  ON lr.user_id = #{userId} AND lr.chat_type = 2 AND lr.chat_id = gu.group_id " +
            "WHERE gu.user_id = #{userId} AND gu.user_status IN (1, 2) " +
            "  AND COALESCE(lr.is_hidden, 0) = 0 " +
            "ORDER BY COALESCE(lr.last_read_time, '1970-01-01') DESC")
    List<Integer> getGroupChatList(@Param("userId") Integer userId);
}
