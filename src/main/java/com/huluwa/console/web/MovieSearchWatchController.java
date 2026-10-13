package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.MovieSearchWatchService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 电影港关键字监听的状态、启动与取消接口。 */
@RestController
@RequestMapping("/api/chat/movie-search-watches")
public class MovieSearchWatchController {

    private final MovieSearchWatchService watches;

    public MovieSearchWatchController(MovieSearchWatchService watches) {
        this.watches = watches;
    }

    @GetMapping
    public ApiResult<List<MovieSearchWatchService.SearchWatch>> list() {
        return ApiResult.ok(watches.list());
    }

    @PostMapping
    public ApiResult<MovieSearchWatchService.SearchWatch> start(@RequestBody StartRequest body) {
        if (body == null) {
            throw new BusinessException(400, "请输入监听关键字");
        }
        return ApiResult.ok(watches.start(body.keyword()));
    }

    @DeleteMapping("/{id}")
    public ApiResult<MovieSearchWatchService.SearchWatch> cancel(@PathVariable long id) {
        return ApiResult.ok(watches.cancel(id));
    }

    public record StartRequest(String keyword) {
    }
}
