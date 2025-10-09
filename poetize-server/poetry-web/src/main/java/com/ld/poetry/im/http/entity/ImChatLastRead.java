package com.ld.poetry.im.http.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;

import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 聊天最后查看时间（私聊+群聊）
 * </p>
 *
 * @author LeapYa
 * @since 2025-10-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("im_chat_last_read")
public class ImChatLastRead implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 聊天类型常量
     */
    public static final int CHAT_TYPE_FRIEND = 1; // 私聊
    public static final int CHAT_TYPE_GROUP = 2;  // 群聊

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Integer userId;

    /**
     * 聊天类型[1:私聊，2:群聊]
     */
    @TableField("chat_type")
    private Integer chatType;

    /**
     * 聊天ID（私聊为friendId，群聊为groupId）
     */
    @TableField("chat_id")
    private Integer chatId;

    /**
     * 最后查看时间
     */
    @TableField("last_read_time")
    private LocalDateTime lastReadTime;

    /**
     * 是否隐藏[0:否，1:是]
     */
    @TableField("is_hidden")
    private Integer isHidden;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}

