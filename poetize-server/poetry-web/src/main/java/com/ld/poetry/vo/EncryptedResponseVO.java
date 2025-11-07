package com.ld.poetry.vo;

import lombok.Data;

/**
 * 加密响应VO
 */
@Data
public class EncryptedResponseVO {
    
    /**
     * 加密后的数据
     */
    private String data;
    
    public EncryptedResponseVO() {}
    
    public EncryptedResponseVO(String data) {
        this.data = data;
    }
}