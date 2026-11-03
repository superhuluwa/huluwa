package com.huluwa.console.service.source;

import com.huluwa.console.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 书源注册表：启动时收集全部 {@link BookSource} 实现，按 id 查找。 */
@Service
public class BookSourceRegistry {

    private final Map<String, BookSource> sources = new LinkedHashMap<>();

    public BookSourceRegistry(List<BookSource> implementations) {
        for (BookSource source : implementations) {
            BookSource previous = sources.put(source.id(), source);
            if (previous != null) {
                throw new IllegalStateException("书源 ID 重复：" + source.id());
            }
        }
    }

    public List<BookSource> all() { return List.copyOf(sources.values()); }

    public BookSource require(String sourceId) {
        BookSource source = sources.get(sourceId);
        if (source == null) throw new BusinessException(404, "书源不存在");
        return source;
    }
}
