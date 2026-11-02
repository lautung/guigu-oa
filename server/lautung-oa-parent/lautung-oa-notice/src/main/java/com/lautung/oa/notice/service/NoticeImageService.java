package com.lautung.oa.notice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.notice.Notice;
import com.lautung.oa.model.notice.NoticeImage;
import com.lautung.oa.notice.image.NoticeImageContent;
import com.lautung.oa.notice.image.NoticeImageStore;
import com.lautung.oa.notice.image.NoticeImageTypes;
import com.lautung.oa.notice.mapper.NoticeImageMapper;
import com.lautung.oa.notice.mapper.NoticeMapper;
import com.lautung.oa.vo.notice.NoticeImageVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 公告图片的生命周期（ADR 0055）：服务端转存、元数据行、绑定重算、删公告清对象、孤儿清理。
 *
 * <p>绑定判据：只有「上传人是当前编辑者」或「已经绑定在本公告」的图片可以被正文引用；
 * 别人上传的未绑定图片不能被绑走，避免借公告把他人图片变成可读。
 *
 * <p>对象删除都放在事务 {@code afterCommit}：行回滚时对象还在，重试仍可复用；提交后删对象
 * 失败只留 WARN，孤儿对象不阻塞业务。
 */
@Service
public class NoticeImageService {

    private static final Logger log = LoggerFactory.getLogger(NoticeImageService.class);
    private static final String OBJECT_PREFIX = "notices/";
    private static final long ORPHAN_TTL_HOURS = 24L;

    private final NoticeImageMapper imageMapper;
    private final NoticeMapper noticeMapper;
    private final NoticeImageStore imageStore;

    public NoticeImageService(
            NoticeImageMapper imageMapper, NoticeMapper noticeMapper, NoticeImageStore imageStore) {
        this.imageMapper = imageMapper;
        this.noticeMapper = noticeMapper;
        this.imageStore = imageStore;
    }

    /**
     * 上传一张图片：5 MB 上限、只收 jpeg/png/gif/webp、Content-Type 与魔数必须一致。
     * 对象进现有私有桶的 {@code notices/} 前缀，元数据先不绑定公告。
     */
    @Transactional
    public NoticeImageVo upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(201, "图片不能为空");
        }
        if (file.getSize() > NoticeImageTypes.MAX_BYTES) {
            throw new BusinessException(201, "图片超过 5 MB 限制");
        }
        String declared = NoticeImageTypes.normalize(file.getContentType());
        if (!NoticeImageTypes.isAllowed(declared)) {
            throw new BusinessException(201, "图片类型不支持");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(201, "图片读取失败");
        }
        if (content.length == 0) {
            throw new BusinessException(201, "图片不能为空");
        }
        if (content.length > NoticeImageTypes.MAX_BYTES) {
            throw new BusinessException(201, "图片超过 5 MB 限制");
        }
        String detected = NoticeImageTypes.detect(content);
        if (detected == null) {
            // 含 SVG：声明什么类型都不放行，魔数识别不出来就是拒绝。
            throw new BusinessException(201, "图片类型不支持");
        }
        if (!detected.equals(declared)) {
            throw new BusinessException(201, "图片内容与类型不一致");
        }

        String objectKey = OBJECT_PREFIX + UUID.randomUUID();
        imageStore.put(objectKey, content, detected);
        NoticeImage image = new NoticeImage();
        image.setUploaderId(userId);
        image.setObjectKey(objectKey);
        image.setContentType(detected);
        image.setSize((long) content.length);
        try {
            imageMapper.insert(image);
        } catch (RuntimeException exception) {
            deleteObjectQuietly(objectKey);
            throw exception;
        }
        return new NoticeImageVo(image.getId(), imageUrl(image.getId()));
    }

    /**
     * 读图片：登录即可，但只允许「已绑定已发布且未删除公告」或「未绑定且请求者就是上传人」。
     * 其余情况按无权处理（205）；行不存在按不存在处理（201）。
     */
    public NoticeImageContent read(Long userId, Long imageId) {
        NoticeImage image = imageId == null ? null : imageMapper.selectById(imageId);
        if (image == null) {
            throw new BusinessException(201, "图片不存在");
        }
        if (image.getNoticeId() != null) {
            Notice notice = noticeMapper.selectById(image.getNoticeId());
            if (notice == null || !Integer.valueOf(Notice.STATUS_PUBLISHED).equals(notice.getStatus())) {
                throw new BusinessException(205, "没有操作权限");
            }
        } else if (!Objects.equals(image.getUploaderId(), userId)) {
            throw new BusinessException(205, "没有操作权限");
        }
        if (!NoticeImageTypes.isAllowed(image.getContentType())) {
            throw new BusinessException(201, "图片类型不支持");
        }
        byte[] content;
        try {
            content = imageStore.get(image.getObjectKey());
        } catch (RuntimeException exception) {
            log.warn("Unable to read notice image object id={}", image.getId());
            content = null;
        }
        if (content == null || content.length == 0) {
            throw new BusinessException(201, "图片不存在");
        }
        return new NoticeImageContent(content, image.getContentType());
    }

    /**
     * 按正文里出现的图片 URL 重算绑定：新出现的绑到本公告，已移除的解除绑定。
     * 解除绑定后由孤儿清理在 24 小时后删除对象与行。
     */
    @Transactional
    public void rebind(Long userId, Long noticeId, Set<Long> desiredImageIds) {
        Set<Long> desired = desiredImageIds == null ? Set.of() : new HashSet<>(desiredImageIds);
        desired.remove(null);

        List<NoticeImage> bound = imageMapper.selectList(new LambdaQueryWrapper<NoticeImage>()
                .eq(NoticeImage::getNoticeId, noticeId));
        for (NoticeImage image : bound) {
            if (!desired.contains(image.getId())) {
                unbind(image.getId());
            }
        }
        for (Long imageId : desired) {
            NoticeImage image = imageMapper.selectById(imageId);
            if (image == null) {
                throw new BusinessException(201, "公告图片不存在");
            }
            if (Objects.equals(image.getNoticeId(), noticeId)) {
                continue;
            }
            if (image.getNoticeId() != null) {
                throw new BusinessException(201, "公告图片已被使用");
            }
            if (!Objects.equals(image.getUploaderId(), userId)) {
                throw new BusinessException(201, "公告图片不存在");
            }
            bind(image.getId(), noticeId);
        }
    }

    /** 删公告：删除其绑定图片的行，对象在提交后删除（回滚时对象保留）。 */
    @Transactional
    public void deleteForNotice(Long noticeId) {
        List<NoticeImage> images = imageMapper.selectList(new LambdaQueryWrapper<NoticeImage>()
                .eq(NoticeImage::getNoticeId, noticeId));
        for (NoticeImage image : images) {
            imageMapper.deleteById(image.getId());
            deleteObjectAfterCommit(image.getObjectKey());
        }
    }

    /**
     * 孤儿清理：未绑定且最后更新超过 24 小时的图片，先删对象再删行；对象删除失败留行待下一轮。
     * 与附件的 pending 清理同一形状（每小时一次），测试直接调本方法。
     */
    @Scheduled(
            fixedDelayString = "${lautung.oa.notices.cleanup-interval:PT1H}",
            initialDelayString = "${lautung.oa.notices.cleanup-interval:PT1H}")
    @Transactional
    public void cleanupOrphans() {
        Date cutoff = Date.from(Instant.now().minusSeconds(ORPHAN_TTL_HOURS * 3600));
        List<NoticeImage> orphans = imageMapper.selectList(new LambdaQueryWrapper<NoticeImage>()
                .isNull(NoticeImage::getNoticeId)
                .le(NoticeImage::getUpdateTime, cutoff));
        for (NoticeImage orphan : orphans) {
            try {
                imageStore.delete(orphan.getObjectKey());
                imageMapper.deleteById(orphan.getId());
            } catch (RuntimeException exception) {
                log.warn("Unable to collect orphan notice image id={}", orphan.getId());
            }
        }
    }

    private void bind(Long imageId, Long noticeId) {
        LambdaUpdateWrapper<NoticeImage> update = new LambdaUpdateWrapper<>();
        update.eq(NoticeImage::getId, imageId)
                .set(NoticeImage::getNoticeId, noticeId)
                .set(NoticeImage::getUpdateTime, new Date());
        imageMapper.update(null, update);
    }

    private void unbind(Long imageId) {
        // updateById 会忽略 null 字段，解除绑定必须走 wrapper 显式 set null；
        // update_time 同时刷新，孤儿清理以「最后更新」起算 24 小时。
        LambdaUpdateWrapper<NoticeImage> update = new LambdaUpdateWrapper<>();
        update.eq(NoticeImage::getId, imageId)
                .set(NoticeImage::getNoticeId, null)
                .set(NoticeImage::getUpdateTime, new Date());
        imageMapper.update(null, update);
    }

    private void deleteObjectAfterCommit(String objectKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteObjectQuietly(objectKey);
                }
            });
            return;
        }
        deleteObjectQuietly(objectKey);
    }

    private void deleteObjectQuietly(String objectKey) {
        try {
            imageStore.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("Unable to delete notice image object after commit");
        }
    }

    private String imageUrl(Long imageId) {
        return "/admin/notices/images/" + imageId;
    }
}
