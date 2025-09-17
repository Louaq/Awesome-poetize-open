package com.ld.poetry.im.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

@Data
public class ImMessage {

    private Integer messageType;

    private String content;

    private Integer fromId;

    private Integer toId;

    private Integer groupId;

    private String avatar;

    private String username;

    private Integer onlineCount;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String toJsonString() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }
}
