package com.lautung.oa.notice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.notice.image.NoticeImageContent;
import com.lautung.oa.notice.service.NoticeImageService;
import com.lautung.oa.notice.service.NoticeService;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.vo.notice.NoticeDetailVo;
import com.lautung.oa.vo.notice.NoticeFeedItemVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公告读侧（ADR 0055）：铃铛、未读计数、全部公告与详情。
 *
 * <p><b>内置面</b>：登录即有，不设页面授予（ADR 0056），也不吃数据范围（ADR 0053）。
 * 只返回已发布且未删除的公告；列表不返回正文。写回执是显式的
 * {@code POST /admin/notices/{id}/read}，GET 详情本身无副作用。
 *
 * <p>客户端（#219）消费的 JSON 形状：
 * <ul>
 *   <li>{@code GET /admin/notices/feed?page=1&limit=20&unreadOnly=false} →
 *       {@code {code,message,data:{current,size,total,records:[{id,title,type,publisherName,publishTime,read}]}}}</li>
 *   <li>{@code GET /admin/notices/unread} → {@code data:[{id,title,type,publisherName,publishTime,read}]}（最多 5 条）</li>
 *   <li>{@code GET /admin/notices/unread-count} → {@code data: <number>}</li>
 *   <li>{@code GET /admin/notices/{id}} → {@code data:{id,title,type,content,publisherId,publisherName,publishTime,read}}；
 *       撤回/删除/不存在时没有 {@code data}</li>
 *   <li>{@code POST /admin/notices/{id}/read} → {@code {code:200,message:"成功"}}；不存在/不可见 → 201「公告不存在」</li>
 *   <li>{@code GET /admin/notices/images/{id}} → 二进制，带存储 content-type 与
 *       {@code X-Content-Type-Options: nosniff}；无权 → 205，不存在 → 201「图片不存在」</li>
 * </ul>
 */
@Tag(name = "公告读侧")
@RestController
@RequestMapping("/admin/notices")
public class NoticeReadController {

    private final NoticeService noticeService;
    private final NoticeImageService imageService;

    public NoticeReadController(NoticeService noticeService, NoticeImageService imageService) {
        this.noticeService = noticeService;
        this.imageService = imageService;
    }

    @Operation(summary = "全部公告分页（可选只看未读）")
    @GetMapping("/feed")
    public Result<IPage<NoticeFeedItemVo>> feed(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long limit,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return Result.ok(noticeService.feed(currentUserId(), page, limit, unreadOnly));
    }

    @Operation(summary = "最近 5 条未读公告")
    @GetMapping("/unread")
    public Result<List<NoticeFeedItemVo>> unread() {
        return Result.ok(noticeService.unread(currentUserId()));
    }

    @Operation(summary = "未读公告数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(noticeService.unreadCount(currentUserId()));
    }

    @Operation(summary = "公告详情（已发布且未删除）")
    @GetMapping("/{id}")
    public Result<NoticeDetailVo> detail(@PathVariable Long id) {
        return Result.ok(noticeService.detail(currentUserId(), id));
    }

    @Operation(summary = "写已读回执（幂等）")
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        noticeService.markRead(currentUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "读取公告图片")
    @GetMapping("/images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        NoticeImageContent image = imageService.read(currentUserId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }

    private Long currentUserId() {
        return LoginUserInfoHelper.getUserId();
    }
}
