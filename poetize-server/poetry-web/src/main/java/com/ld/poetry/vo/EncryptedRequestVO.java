package com.ld.poetry.vo;

import lombok.Data;

/**
 * 加密请求体
 */
@Data
public class EncryptedRequestVO {
    /**
     * 加密的数据
     */
    private String data;
}