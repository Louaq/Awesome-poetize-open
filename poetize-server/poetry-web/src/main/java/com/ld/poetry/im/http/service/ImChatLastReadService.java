package com.ld.poetry.im.http.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ld.poetry.im.http.entity.ImChatLastRead;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 聊天最后查看时间 服务类（私聊+群聊）
 * </p>
 *
 * @author LeapYa
 * @since 2025-10-09
 */
public interface ImChatLastReadService extends IService<ImChatLastRead> {

    /**
     * 获取用户所有好友的未读消息数
     * 
     * @param userId 用户ID
     * @return Map<friendId, unreadCount>
     */
    Map<Integer, Integer> getFriendUnreadCounts(Integer userId);

    /**
     * 获取用户所有群的未读消息数
     * 
     * @param userId 用户ID
     * @return Map<groupId, unreadCount>
     */
    Map<Integer, Integer> getGroupUnreadCounts(Integer userId);

    /**
     * 获取单个好友的未读消息数
     * 
     * @param userId 用户ID
     * @param friendId 好友ID
     * @return 未读消息数
     */
    Integer getFriendUnreadCount(Integer userId, Integer friendId);

    /**
     * 获取单个群的未读消息数
     * 
     * @param userId 用户ID
     * @param groupId 群ID
     * @return 未读消息数
     */
    Integer getGroupUnreadCount(Integer userId, Integer groupId);

    /**
     * 标记好友消息为已读（更新最后查看时间为当前时间）
     * 
     * @param userId 用户ID
     * @param friendId 好友ID
     */
    void markFriendAsRead(Integer userId, Integer friendId);

    /**
     * 标记群消息为已读（更新最后查看时间为当前时间）
     * 
     * @param userId 用户ID
     * @param groupId 群ID
     */
    void markGroupAsRead(Integer userId, Integer groupId);

    /**
     * 获取用户的私聊列表（按最后查看时间倒序）
     * 
     * @param userId 用户ID
     * @return 好友ID列表
     */
    List<Integer> getFriendChatList(Integer userId);

    /**
     * 获取用户的群聊列表（按最后查看时间倒序）
     * 
     * @param userId 用户ID
     * @return 群ID列表
     */
    List<Integer> getGroupChatList(Integer userId);

    /**
     * 隐藏好友聊天
     * 
     * @param userId 用户ID
     * @param friendId 好友ID
     */
    void hideFriendChat(Integer userId, Integer friendId);

    /**
     * 隐藏群聊
     * 
     * @param userId 用户ID
     * @param groupId 群ID
     */
    void hideGroupChat(Integer userId, Integer groupId);

    /**
     * 取消隐藏（收到新消息时自动调用）
     * 
     * @param userId 用户ID
     * @param chatType 聊天类型
     * @param chatId 聊天ID
     */
    void unhideChat(Integer userId, Integer chatType, Integer chatId);
}
