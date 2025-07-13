package com.ld.poetry.utils.storage;

import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.vo.FileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * EasyImage 2.0简单图床存储服务
 */
@Slf4j
@Component
public class EasyImageUtil implements StoreService {
    
    @Value("${easyimage.url:}")
    private String easyImageUrl;
    
    @Value("${easyimage.token:}")
    private String easyImageToken;
    
    @Value("${easyimage.enable:false}")
    private Boolean easyImageEnable;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Override
    public FileVO saveFile(FileVO fileVO) {
        // 参数校验
        if (fileVO == null || fileVO.getFile() == null) {
            throw new PoetryRuntimeException("文件参数不能为空！");
        }

        // 检查是否启用EasyImage
        if (easyImageEnable == null || !easyImageEnable || !StringUtils.hasText(easyImageUrl) || !StringUtils.hasText(easyImageToken)) {
            throw new PoetryRuntimeException("简单图床未正确配置！");
        }
        
        try {
            MultipartFile file = fileVO.getFile();
            
            // 构建请求头和表单数据
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("image", file.getResource());
            form.add("token", easyImageToken);
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(form, headers);
            
            // 发送上传请求
            log.info("正在上传文件到简单图床: {}", file.getOriginalFilename());
            EasyImageResponse response = restTemplate.postForObject(easyImageUrl, requestEntity, EasyImageResponse.class);
            
            // 处理响应
            if (response == null) {
                throw new PoetryRuntimeException("简单图床返回空响应");
            }
            
            if (!"success".equals(response.getResult()) || response.getCode() != 200) {
                log.error("EasyImage上传失败: {}", response);
                throw new PoetryRuntimeException("简单图床上传失败: " + response.getResult());
            }
            
            // 设置访问路径
            String imageUrl = response.getUrl().replace("\\", "");
            fileVO.setVisitPath(imageUrl);
            log.info("简单图床上传成功: {}", imageUrl);
            
            return fileVO;
        } catch (Exception e) {
            log.error("简单图床上传出错", e);
            throw new PoetryRuntimeException("简单图床上传出错: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(List<String> paths) {
        log.warn("简单图床不支持服务端批量删除文件操作");
    }

    @Override
    public String getStoreName() {
        return StoreEnum.EASYIMAGE.getCode();
    }

    /**
     * 简单图床响应结构
     */
    private static class EasyImageResponse {
        private String result;
        private int code;
        private String url;
        private String srcName;
        private String thumb;
        private String del;

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSrcName() {
            return srcName;
        }

        public void setSrcName(String srcName) {
            this.srcName = srcName;
        }

        public String getThumb() {
            return thumb;
        }

        public void setThumb(String thumb) {
            this.thumb = thumb;
        }

        public String getDel() {
            return del;
        }

        public void setDel(String del) {
            this.del = del;
        }

        @Override
        public String toString() {
            return "EasyImageResponse{" +
                    "result='" + result + '\'' +
                    ", code=" + code +
                    ", url='" + url + '\'' +
                    ", srcName='" + srcName + '\'' +
                    '}';
        }
    }
} 