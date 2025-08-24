package com.ld.poetry.service;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IP2Region数据库文件测试
 * 验证IP2Region 2.7.0版本和数据库文件是否正常工作
 */
public class Ip2RegionTest {

    @Test
    public void testIp2RegionDatabase() {
        try {
            // 测试加载IP2Region数据库文件
            Searcher searcher = Searcher.newWithBuffer(
                IOUtils.toByteArray(new ClassPathResource("ip2region.xdb").getInputStream())
            );
            
            assertNotNull(searcher, "IP2Region搜索器应该能够正常初始化");
            
            // 测试一些已知的IP地址
            String result1 = searcher.search("8.8.8.8");
            assertNotNull(result1, "应该能够解析公网IP");
            System.out.println("8.8.8.8 -> " + result1);
            
            String result2 = searcher.search("114.114.114.114");
            assertNotNull(result2, "应该能够解析114DNS的IP");
            System.out.println("114.114.114.114 -> " + result2);
            
            String result3 = searcher.search("61.135.185.32");
            assertNotNull(result3, "应该能够解析百度的IP");
            System.out.println("61.135.185.32 -> " + result3);
            
            System.out.println("IP2Region数据库文件工作正常！");
            
        } catch (Exception e) {
            fail("IP2Region数据库测试失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testParseResult() {
        try {
            Searcher searcher = Searcher.newWithBuffer(
                IOUtils.toByteArray(new ClassPathResource("ip2region.xdb").getInputStream())
            );
            
            String searchResult = searcher.search("61.135.185.32");
            
            // IP2Region格式: 国家|区域|省份|城市|ISP
            String[] regions = searchResult.split("\\|");
            assertTrue(regions.length >= 4, "解析结果应该包含至少4个部分");
            
            System.out.println("解析结果详情:");
            for (int i = 0; i < regions.length; i++) {
                String[] labels = {"国家", "区域", "省份", "城市", "ISP"};
                String label = i < labels.length ? labels[i] : "字段" + i;
                System.out.println(label + ": " + regions[i]);
            }
            
        } catch (Exception e) {
            fail("解析结果测试失败: " + e.getMessage());
        }
    }
}