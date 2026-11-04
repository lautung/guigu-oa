package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.SysPost;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * 岗位服务（#248）：CRUD、删除保护与用户写路径的岗位校验。
 * 岗位只是职务标签，不产生授权、不参与审批分配、不套数据范围。
 */
public interface SysPostService extends IService<SysPost> {

    /**
     * 更新岗位；行不存在是明确拒绝（200 + 201），不是静默无操作（ADR 0034 的空字段语义）。
     */
    void updatePost(SysPost post);

    /** 删除岗位；仍有未删除用户挂载时拒绝，不级联清关联行。 */
    void removePost(Long id);

    /**
     * 校验并规范化用户提交的岗位集合：去重、去 null，要求每个 id 都存在且未停用。
     * 空列表合法（表示不挂岗位 / 清空）；null 由调用方按「未传」处理。
     */
    List<Long> validateAssignablePostIds(List<Long> postIds);
}
