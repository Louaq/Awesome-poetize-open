package com.ld.poetry.utils.storage;

import com.ld.poetry.entity.User;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.utils.cache.PoetryCache;
import com.ld.poetry.utils.PoetryUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FileFilter {

    private final AntPathMatcher matcher = new AntPathMatcher();

    public boolean doFilterFile(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        if (matcher.match("/resource/upload", httpServletRequest.getRequestURI())) {
            String token = PoetryUtil.getTokenWithoutBearer();
            if (StringUtils.hasText(token)) {
                User user = (User) PoetryCache.get(token);

                if (user != null) {
                    // 管理员不受上传次数限制
                    if (user.getId().intValue() == PoetryUtil.getAdminUser().getId().intValue()) {
                        return false;
                    }

                    // 检查用户上传次数限制
                    AtomicInteger atomicInteger = (AtomicInteger) PoetryCache.get(CommonConst.SAVE_COUNT_USER_ID + user.getId().toString());
                    if (atomicInteger == null) {
                        atomicInteger = new AtomicInteger();
                        PoetryCache.put(CommonConst.SAVE_COUNT_USER_ID + user.getId().toString(), atomicInteger, CommonConst.SAVE_EXPIRE);
                    }
                    int userIdCount = atomicInteger.getAndIncrement();

                    // 检查IP上传次数限制
                    String ip = PoetryUtil.getIpAddr(PoetryUtil.getRequest());
                    AtomicInteger atomic = (AtomicInteger) PoetryCache.get(CommonConst.SAVE_COUNT_IP + ip);
                    if (atomic == null) {
                        atomic = new AtomicInteger();
                        PoetryCache.put(CommonConst.SAVE_COUNT_IP + ip, atomic, CommonConst.SAVE_EXPIRE);
                    }
                    int ipCount = atomic.getAndIncrement();

                    // 如果用户或IP超过限制，则拦截
                    return userIdCount >= CommonConst.SAVE_MAX_COUNT || ipCount >= CommonConst.SAVE_MAX_COUNT;
                }
            }
            return true;
        } else {
            return false;
        }
    }
}
