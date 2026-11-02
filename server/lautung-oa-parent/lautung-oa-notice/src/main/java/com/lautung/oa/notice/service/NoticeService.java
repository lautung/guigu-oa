package com.lautung.oa.notice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.notice.Notice;
import com.lautung.oa.model.notice.NoticeRead;
import com.lautung.oa.notice.mapper.NoticeMapper;
import com.lautung.oa.notice.mapper.NoticeReadMapper;
import com.lautung.oa.notice.mapper.NoticeUserMapper;
import com.lautung.oa.notice.support.NoticeContentSanitizer;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.vo.notice.NoticeAdminDetailVo;
import com.lautung.oa.vo.notice.NoticeAdminListVo;
import com.lautung.oa.vo.notice.NoticeDetailVo;
import com.lautung.oa.vo.notice.NoticeFeedItemVo;
import com.lautung.oa.vo.notice.NoticeRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 公告的读侧与管理侧（ADR 0055）。
 *
 * <p>读侧是内置面：登录即有，不套页面授予与数据范围；只暴露已发布且未删除的公告，
 * 列表永不返回正文。管理侧由 controller 的 {@code backofficeGate} 判定页面/按钮，
 * 本类只管状态机与落库形状。
 *
 * <p>写侧消毒只发生在这里：创建/更新正文先过 {@link NoticeContentSanitizer}，
 * 库里的 HTML 就是干净产物；读侧不二次消毒。
 */
@Service
public class NoticeService {

    /** 铃铛弹层只列最近 5 条未读（ADR 0055）。 */
    private static final int UNREAD_POPOVER_SIZE = 5;

    private final NoticeMapper noticeMapper;
    private final NoticeReadMapper noticeReadMapper;
    private final NoticeUserMapper noticeUserMapper;
    private final NoticeImageService imageService;
    private final NoticeContentSanitizer sanitizer;

    public NoticeService(
            NoticeMapper noticeMapper,
            NoticeReadMapper noticeReadMapper,
            NoticeUserMapper noticeUserMapper,
            NoticeImageService imageService,
            NoticeContentSanitizer sanitizer) {
        this.noticeMapper = noticeMapper;
        this.noticeReadMapper = noticeReadMapper;
        this.noticeUserMapper = noticeUserMapper;
        this.imageService = imageService;
        this.sanitizer = sanitizer;
    }

    // ------------------------------------------------------------------
    // 读侧（登录即有）
    // ------------------------------------------------------------------

    /**
     * 「全部公告」分页：{@code publish_time DESC, id DESC}，可选只看未读。
     * 返回 MyBatis-Plus 的 paged 形状（current/size/total/records），记录不含正文。
     */
    public IPage<NoticeFeedItemVo> feed(Long userId, Long page, Long limit, boolean unreadOnly) {
        Page<Notice> window = PageWindow.of(page, limit);
        LambdaQueryWrapper<Notice> query = publishedQuery();
        if (unreadOnly) {
            query.apply(unreadSubquery(), userId);
        }
        query.orderByDesc(Notice::getPublishTime).orderByDesc(Notice::getId);
        IPage<Notice> result = noticeMapper.selectPage(window, query);
        Page<NoticeFeedItemVo> output = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        output.setRecords(feedItems(userId, result.getRecords()));
        return output;
    }

    /** 顶栏铃铛：最近 5 条未读，同一排序。 */
    public List<NoticeFeedItemVo> unread(Long userId) {
        LambdaQueryWrapper<Notice> query = publishedQuery();
        query.apply(unreadSubquery(), userId)
                .orderByDesc(Notice::getPublishTime)
                .orderByDesc(Notice::getId)
                .last("LIMIT " + UNREAD_POPOVER_SIZE);
        return feedItems(userId, noticeMapper.selectList(query));
    }

    /** 未读角标：只数已发布且未删除、且当前用户没有回执的公告。 */
    public long unreadCount(Long userId) {
        LambdaQueryWrapper<Notice> query = publishedQuery();
        query.apply(unreadSubquery(), userId);
        Long count = noticeMapper.selectCount(query);
        return count == null ? 0L : count;
    }

    /**
     * 读侧详情：已发布且未删除才有数据；撤回/删除按不存在处理（返回 {@code null}，信封仍是 200）。
     * 本方法只读，不写回执——回执由显式的 {@code POST /admin/notices/{id}/read} 负责。
     */
    public NoticeDetailVo detail(Long userId, Long noticeId) {
        Notice notice = noticeMapper.selectById(noticeId);
        if (notice == null || !Integer.valueOf(Notice.STATUS_PUBLISHED).equals(notice.getStatus())) {
            return null;
        }
        NoticeDetailVo vo = new NoticeDetailVo();
        vo.setId(notice.getId());
        vo.setTitle(notice.getTitle());
        vo.setType(notice.getType());
        vo.setContent(notice.getContent());
        vo.setPublisherId(notice.getPublisherId());
        vo.setPublisherName(notice.getPublisherName());
        vo.setPublishTime(notice.getPublishTime());
        vo.setRead(hasReceipt(userId, notice.getId()));
        return vo;
    }

    /**
     * 已读回执：打开详情后调用，幂等。唯一键 {@code (notice_id, user_id)} 兜住并发重复，
     * 重复插入按成功处理。撤回/删除的公告按不存在拒绝。
     */
    @Transactional
    public void markRead(Long userId, Long noticeId) {
        Notice notice = noticeMapper.selectById(noticeId);
        if (notice == null || !Integer.valueOf(Notice.STATUS_PUBLISHED).equals(notice.getStatus())) {
            throw new BusinessException(201, "公告不存在");
        }
        if (hasReceipt(userId, noticeId)) {
            return;
        }
        NoticeRead receipt = new NoticeRead();
        receipt.setNoticeId(noticeId);
        receipt.setUserId(userId);
        try {
            noticeReadMapper.insert(receipt);
        } catch (DuplicateKeyException ignored) {
            // 并发重复打开：另一请求已写入同一唯一键，本次按成功处理。
        }
    }

    // ------------------------------------------------------------------
    // 管理侧（controller 已过 page + button gate）
    // ------------------------------------------------------------------

    /** 管理列表：标题/类型/状态过滤，排除软删；稳定排序 {@code create_time DESC, id DESC}。 */
    public IPage<NoticeAdminListVo> adminPage(
            Long page, Long limit, String title, Integer type, Integer status) {
        Page<Notice> window = PageWindow.of(page, limit);
        LambdaQueryWrapper<Notice> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(title)) {
            query.like(Notice::getTitle, title.trim());
        }
        if (type != null) {
            query.eq(Notice::getType, type);
        }
        if (status != null) {
            query.eq(Notice::getStatus, status);
        }
        query.orderByDesc(Notice::getCreateTime).orderByDesc(Notice::getId);
        IPage<Notice> result = noticeMapper.selectPage(window, query);
        Page<NoticeAdminListVo> output = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        output.setRecords(result.getRecords().stream().map(this::adminListVo).toList());
        return output;
    }

    /** 管理详情：编辑页需要草稿正文（#220）。软删行按不存在处理。 */
    public NoticeAdminDetailVo adminDetail(Long noticeId) {
        Notice notice = noticeId == null ? null : noticeMapper.selectById(noticeId);
        return notice == null ? null : adminDetailVo(notice);
    }

    /** 新建草稿：正文写入时消毒；正文里引用的图片按当前用户可绑定的范围重算绑定。 */
    @Transactional
    public NoticeAdminDetailVo create(Long userId, NoticeRequest request) {
        String content = sanitizer.sanitize(request.getContent());
        Notice notice = new Notice();
        notice.setTitle(request.getTitle().trim());
        notice.setType(request.getType());
        notice.setContent(content);
        notice.setStatus(Notice.STATUS_DRAFT);
        noticeMapper.insert(notice);
        imageService.rebind(userId, notice.getId(), sanitizer.imageIds(content));
        return adminDetail(notice.getId());
    }

    /**
     * 编辑：草稿与已发布可改（保留同一 id，不回滚回执）；已撤回不可编辑（ADR 0055 只把
     * 「发布」作为撤回后的出口）。发布人快照与 {@code publish_time} 不因编辑改变。
     */
    @Transactional
    public NoticeAdminDetailVo update(Long userId, Long noticeId, NoticeRequest request) {
        Notice notice = requireNotice(noticeId);
        if (!Integer.valueOf(Notice.STATUS_DRAFT).equals(notice.getStatus())
                && !Integer.valueOf(Notice.STATUS_PUBLISHED).equals(notice.getStatus())) {
            throw new BusinessException(201, "公告当前状态不可编辑");
        }
        String content = sanitizer.sanitize(request.getContent());
        notice.setTitle(request.getTitle().trim());
        notice.setType(request.getType());
        notice.setContent(content);
        noticeMapper.updateById(notice);
        imageService.rebind(userId, noticeId, sanitizer.imageIds(content));
        return adminDetail(noticeId);
    }

    /** 软删任意状态：等价于隐式撤回，读侧立即不可见；回执保留，绑定图片的对象与行删除。 */
    @Transactional
    public void delete(Long noticeId) {
        requireNotice(noticeId);
        noticeMapper.deleteById(noticeId);
        imageService.deleteForNotice(noticeId);
    }

    /**
     * 发布：仅草稿/已撤回可发布；刷新 {@code publish_time} 并重写发布人快照。
     * 重复发布已发布公告按状态冲突拒绝（状态机只允许显式两跳）。
     */
    @Transactional
    public NoticeAdminDetailVo publish(Long userId, Long noticeId) {
        Notice notice = requireNotice(noticeId);
        if (!Integer.valueOf(Notice.STATUS_DRAFT).equals(notice.getStatus())
                && !Integer.valueOf(Notice.STATUS_WITHDRAWN).equals(notice.getStatus())) {
            throw new BusinessException(201, "公告当前状态不可发布");
        }
        notice.setStatus(Notice.STATUS_PUBLISHED);
        notice.setPublisherId(userId);
        notice.setPublisherName(publisherName(userId));
        notice.setPublishTime(new Date());
        noticeMapper.updateById(notice);
        return adminDetail(noticeId);
    }

    /** 撤回：仅已发布可撤回；对读侧立即不可见，回执保留。 */
    @Transactional
    public NoticeAdminDetailVo withdraw(Long noticeId) {
        Notice notice = requireNotice(noticeId);
        if (!Integer.valueOf(Notice.STATUS_PUBLISHED).equals(notice.getStatus())) {
            throw new BusinessException(201, "公告当前状态不可撤回");
        }
        notice.setStatus(Notice.STATUS_WITHDRAWN);
        noticeMapper.updateById(notice);
        return adminDetail(noticeId);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private LambdaQueryWrapper<Notice> publishedQuery() {
        return new LambdaQueryWrapper<Notice>().eq(Notice::getStatus, Notice.STATUS_PUBLISHED);
    }

    /** 未读 = 没有本用户未删除的回执；子查询按参数绑定，不拼 SQL 片段。 */
    private String unreadSubquery() {
        return "NOT EXISTS (SELECT 1 FROM oa_notice_read r WHERE r.notice_id = oa_notice.id "
                + "AND r.user_id = {0} AND r.is_deleted = 0)";
    }

    private boolean hasReceipt(Long userId, Long noticeId) {
        if (userId == null || noticeId == null) {
            return false;
        }
        Long count = noticeReadMapper.selectCount(new LambdaQueryWrapper<NoticeRead>()
                .eq(NoticeRead::getNoticeId, noticeId)
                .eq(NoticeRead::getUserId, userId));
        return count != null && count > 0;
    }

    private List<NoticeFeedItemVo> feedItems(Long userId, List<Notice> notices) {
        if (notices == null || notices.isEmpty()) {
            return List.of();
        }
        Set<Long> readNoticeIds = new HashSet<>();
        List<Long> noticeIds = new ArrayList<>();
        for (Notice notice : notices) {
            noticeIds.add(notice.getId());
        }
        List<NoticeRead> receipts = noticeReadMapper.selectList(new LambdaQueryWrapper<NoticeRead>()
                .eq(NoticeRead::getUserId, userId)
                .in(NoticeRead::getNoticeId, noticeIds));
        for (NoticeRead receipt : receipts) {
            readNoticeIds.add(receipt.getNoticeId());
        }
        List<NoticeFeedItemVo> items = new ArrayList<>(notices.size());
        for (Notice notice : notices) {
            NoticeFeedItemVo item = new NoticeFeedItemVo();
            item.setId(notice.getId());
            item.setTitle(notice.getTitle());
            item.setType(notice.getType());
            item.setPublisherName(notice.getPublisherName());
            item.setPublishTime(notice.getPublishTime());
            item.setRead(readNoticeIds.contains(notice.getId()));
            items.add(item);
        }
        return items;
    }

    private NoticeAdminListVo adminListVo(Notice notice) {
        NoticeAdminListVo vo = new NoticeAdminListVo();
        vo.setId(notice.getId());
        vo.setTitle(notice.getTitle());
        vo.setType(notice.getType());
        vo.setStatus(notice.getStatus());
        vo.setPublisherId(notice.getPublisherId());
        vo.setPublisherName(notice.getPublisherName());
        vo.setPublishTime(notice.getPublishTime());
        vo.setCreateTime(notice.getCreateTime());
        vo.setUpdateTime(notice.getUpdateTime());
        return vo;
    }

    private NoticeAdminDetailVo adminDetailVo(Notice notice) {
        NoticeAdminDetailVo vo = new NoticeAdminDetailVo();
        vo.setId(notice.getId());
        vo.setTitle(notice.getTitle());
        vo.setType(notice.getType());
        vo.setContent(notice.getContent());
        vo.setStatus(notice.getStatus());
        vo.setPublisherId(notice.getPublisherId());
        vo.setPublisherName(notice.getPublisherName());
        vo.setPublishTime(notice.getPublishTime());
        vo.setCreateTime(notice.getCreateTime());
        vo.setUpdateTime(notice.getUpdateTime());
        return vo;
    }

    private Notice requireNotice(Long noticeId) {
        Notice notice = noticeId == null ? null : noticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new BusinessException(201, "公告不存在");
        }
        return notice;
    }

    /**
     * 发布人姓名快照。notice 边不允许访问 auth，所以只读 {@code sys_user.name} 一列；
     * 查不到时退回登录名，列宽 50。
     */
    private String publisherName(Long userId) {
        String name = noticeUserMapper.selectUserName(userId);
        if (!StringUtils.hasText(name)) {
            name = LoginUserInfoHelper.getUsername();
        }
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.length() <= 50 ? name : name.substring(0, 50);
    }
}
