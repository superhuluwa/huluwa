package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.security.AuthInterceptor;
import com.huluwa.console.service.StarwarService;
import com.huluwa.console.ws.StarwarWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 星球大战（是男人就撑20秒 网络对战版）接口。
 *
 * <p>对齐 2009 年冒泡社区 starwar.mrp 的服务端行为：大厅、四专区房间、等待聊天室、
 * 实时对战、结算、商店与机库、好友与资料、排行榜。实时帧走 {@code /ws/starwar}。</p>
 */
@RestController
@RequestMapping("/api/starwar")
public class StarwarController {

    private final StarwarService service;
    private final StarwarWebSocketHandler push;

    public StarwarController(StarwarService service, StarwarWebSocketHandler push) {
        this.service = service;
        this.push = push;
    }

    private String user(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(AuthInterceptor.ATTR_USERNAME));
    }

    // ---------------------------------------------------------------- 大厅

    /** 大厅首页：玩家档案、四专区、九星球、段位、全部原文文案 */
    @GetMapping("/lobby")
    public ApiResult<Map<String, Object>> lobby(HttpServletRequest request) {
        return ApiResult.ok(service.lobby(user(request)));
    }

    /** 只取文案（客户端首次进入时缓存，避免重复拉全量） */
    @GetMapping("/texts")
    public ApiResult<Map<String, Object>> texts() {
        return ApiResult.ok(service.texts());
    }

    @GetMapping("/help")
    public ApiResult<Map<String, Object>> help() {
        return ApiResult.ok(service.help());
    }

    // ---------------------------------------------------------------- 档案

    @GetMapping("/profile")
    public ApiResult<Map<String, Object>> profile(HttpServletRequest request) {
        return ApiResult.ok(service.profile(user(request)));
    }

    @PostMapping("/profile/nickname")
    public ApiResult<Map<String, Object>> nickname(HttpServletRequest request, @RequestBody Map<String, String> body) {
        return ApiResult.ok(service.updateNickname(user(request), body == null ? null : body.get("nickname")));
    }

    /** 我的状态：等级 / 胜率 / 飞机 */
    @GetMapping("/status")
    public ApiResult<Map<String, Object>> status(HttpServletRequest request) {
        return ApiResult.ok(service.myStatus(user(request)));
    }

    // ---------------------------------------------------------------- 房间

    /** 专区房间列表（分页） */
    @GetMapping("/rooms")
    public ApiResult<Map<String, Object>> rooms(@RequestParam String zone,
                                                @RequestParam(defaultValue = "0") int page) {
        return ApiResult.ok(service.rooms(zone, page));
    }

    @PostMapping("/rooms")
    public ApiResult<Map<String, Object>> create(HttpServletRequest request,
                                                 @RequestBody CreateRoomRequest body) {
        if (body == null) {
            throw new com.huluwa.console.common.BusinessException(400, "缺少房间参数");
        }
        Map<String, Object> room = service.createRoom(user(request), body.zone(), body.name(),
                body.mapId() == 0 ? 2 : body.mapId(), body.items(), body.costMode(),
                body.password());
        push.broadcastRoom(String.valueOf(room.get("id")), room);
        return ApiResult.ok(room);
    }

    @GetMapping("/rooms/{id}")
    public ApiResult<Map<String, Object>> detail(HttpServletRequest request, @PathVariable String id) {
        return ApiResult.ok(service.roomDetail(id, user(request)));
    }

    @PostMapping("/rooms/{id}/enter")
    public ApiResult<Map<String, Object>> enter(HttpServletRequest request, @PathVariable String id,
                                                @RequestBody(required = false) Map<String, String> body) {
        Map<String, Object> room = service.enterRoom(user(request), id, body == null ? null : body.get("password"));
        push.broadcastRoom(id, room);
        return ApiResult.ok(room);
    }

    @PostMapping("/rooms/{id}/leave")
    public ApiResult<Map<String, Object>> leave(HttpServletRequest request, @PathVariable String id) {
        Map<String, Object> result = service.leaveRoom(user(request), id);
        return ApiResult.ok(result);
    }

    @PostMapping("/rooms/{id}/kick")
    public ApiResult<Map<String, Object>> kick(HttpServletRequest request, @PathVariable String id,
                                               @RequestBody Map<String, String> body) {
        Map<String, Object> room = service.kick(user(request), id, body == null ? null : body.get("target"));
        push.broadcastRoom(id, room);
        return ApiResult.ok(room);
    }

    @PostMapping("/rooms/{id}/ready")
    public ApiResult<Map<String, Object>> ready(HttpServletRequest request, @PathVariable String id,
                                                @RequestBody(required = false) Map<String, Object> body) {
        boolean ready = body != null && Boolean.TRUE.equals(body.get("ready"));
        Map<String, Object> room = service.ready(user(request), id, ready);
        push.broadcastRoom(id, room);
        return ApiResult.ok(room);
    }

    /** 房主修改房间设置：地图 / 道具 / 费用 / 密码（对应原文地%s图、选%s项、费%s用、密%s码） */
    @PostMapping("/rooms/{id}/settings")
    public ApiResult<Map<String, Object>> updateSettings(HttpServletRequest request, @PathVariable String id,
                                                         @RequestBody Map<String, Object> body) {
        Integer mapId = body.get("mapId") instanceof Number n ? n.intValue() : null;
        Boolean items = body.get("items") instanceof Boolean b ? b : null;
        Integer costMode = body.get("costMode") instanceof Number n ? n.intValue() : null;
        String password = body.get("password") instanceof String s ? s : null;
        Map<String, Object> room = service.updateSettings(user(request), id, mapId, items, costMode, password);
        push.broadcastRoom(id, room);
        return ApiResult.ok(room);
    }

    /** 快速开始（仅房主）：3 秒倒计时 */
    @PostMapping("/rooms/{id}/quick-start")
    public ApiResult<Map<String, Object>> quickStart(HttpServletRequest request, @PathVariable String id) {
        Map<String, Object> room = service.quickStart(user(request), id);
        push.broadcastRoom(id, room);
        return ApiResult.ok(room);
    }

    // ---------------------------------------------------------------- 等待聊天室

    @GetMapping("/rooms/{id}/chat")
    public ApiResult<Map<String, Object>> chat(HttpServletRequest request, @PathVariable String id,
                                               @RequestParam(defaultValue = "0") long afterId) {
        return ApiResult.ok(service.chatHistory(user(request), id, afterId));
    }

    @PostMapping("/rooms/{id}/chat")
    public ApiResult<Map<String, Object>> say(HttpServletRequest request, @PathVariable String id,
                                              @RequestBody Map<String, String> body) {
        Map<String, Object> result = service.say(user(request), id,
                body == null ? null : body.get("content"),
                body == null ? null : body.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) result.get("message");
        if (message != null) {
            push.broadcastChat(id, message);
        }
        return ApiResult.ok(result);
    }

    @GetMapping("/phrases")
    public ApiResult<List<String>> phrases(HttpServletRequest request) {
        return ApiResult.ok(service.phrases(user(request)));
    }

    @PostMapping("/phrases")
    public ApiResult<List<String>> savePhrases(HttpServletRequest request, @RequestBody List<String> body) {
        return ApiResult.ok(service.savePhrases(user(request), body));
    }

    // ---------------------------------------------------------------- 好友与资料

    @GetMapping("/friends")
    public ApiResult<List<Map<String, Object>>> friends(HttpServletRequest request) {
        return ApiResult.ok(service.friends(user(request)));
    }

    @PostMapping("/friends")
    public ApiResult<Map<String, Object>> addFriend(HttpServletRequest request, @RequestBody Map<String, String> body) {
        return ApiResult.ok(service.addFriend(user(request), body == null ? null : body.get("target")));
    }

    /** 对手资料 */
    @GetMapping("/players/{name}")
    public ApiResult<Map<String, Object>> opponent(HttpServletRequest request, @PathVariable String name) {
        return ApiResult.ok(service.opponent(user(request), name));
    }

    // ---------------------------------------------------------------- 商店 / 机库 / 市场

    @GetMapping("/shop")
    public ApiResult<Map<String, Object>> shop(HttpServletRequest request) {
        return ApiResult.ok(service.shop(user(request)));
    }

    @PostMapping("/shop/buy")
    public ApiResult<Map<String, Object>> buy(HttpServletRequest request, @RequestBody Map<String, String> body) {
        return ApiResult.ok(service.buy(user(request), body.get("type"), body.get("id")));
    }

    @GetMapping("/hangar")
    public ApiResult<List<Map<String, Object>>> hangar(HttpServletRequest request) {
        return ApiResult.ok(service.hangar(user(request)));
    }

    @PostMapping("/hangar/equip")
    public ApiResult<Map<String, Object>> equip(HttpServletRequest request, @RequestBody Map<String, String> body) {
        return ApiResult.ok(service.equip(user(request), body.get("type"), body.get("id")));
    }

    @GetMapping("/market")
    public ApiResult<Map<String, Object>> market(HttpServletRequest request) {
        return ApiResult.ok(service.market(user(request)));
    }

    // ---------------------------------------------------------------- 排行榜

    @GetMapping("/ranking")
    public ApiResult<Map<String, Object>> ranking(@RequestParam(defaultValue = "score") String scope,
                                                  @RequestParam(defaultValue = "0") int page) {
        return ApiResult.ok(service.ranking(scope, page));
    }

    // ---------------------------------------------------------------- 对战

    /** 开局（倒计时结束后调用；幂等） */
    @PostMapping("/rooms/{id}/start")
    public ApiResult<Map<String, Object>> start(HttpServletRequest request, @PathVariable String id) {
        var world = service.startMatch(id);
        return ApiResult.ok(world.snapshot());
    }

    /** 世界快照（WebSocket 不可用时的轮询兜底） */
    @GetMapping("/rooms/{id}/world")
    public ApiResult<Map<String, Object>> world(@PathVariable String id) {
        return ApiResult.ok(service.snapshot(id));
    }

    /** 方向键输入（轮询模式下使用；正常走 WebSocket） */
    @PostMapping("/rooms/{id}/input")
    public ApiResult<Map<String, Object>> input(HttpServletRequest request, @PathVariable String id,
                                                @RequestBody Map<String, Object> body) {
        int dx = body.get("dx") instanceof Number n ? n.intValue() : 0;
        int dy = body.get("dy") instanceof Number n ? n.intValue() : 0;
        boolean item = Boolean.TRUE.equals(body.get("item"));
        return ApiResult.ok(service.input(user(request), id, dx, dy, item));
    }

    @GetMapping("/matches/{matchId}")
    public ApiResult<Map<String, Object>> matchResult(HttpServletRequest request, @PathVariable String matchId) {
        return ApiResult.ok(service.matchResult(user(request), matchId));
    }

    public record CreateRoomRequest(String zone, String name, int mapId, boolean items,
                                    int costMode, String password) {
    }
}
