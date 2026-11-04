package com.lautung.oa.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lautung.oa.model.system.SysDept;

import java.util.List;

/**
 * 部门维护（#247）：树读与 CRUD。<b>不</b>参与授权判定，也不进审批链（ADR 0056）；
 * ancestors 含自身的包裹逗号路径由本服务唯一写入，客户端只提交父子关系与资料字段。
 * 「停用部门不允许新归属」落在用户写路径（#249 的 deptId 校验），本服务不拦截；
 * 删除只做有下级/有用户两分支保护，不级联。
 */
public interface SysDeptService extends IService<SysDept> {

    /** 树列表：根节点按 sortValue、id 升序，children 逐层装配；停用部门同样出现在维护面。 */
    List<SysDept> findTree();

    /**
     * 选中部门及其全部下级（{@code ancestors} 前缀，含自身）；部门不存在/已删返回空列表。
     * 只回答部门树范围，不放宽 DataScope（#249：调用方把它与数据范围 AND 起来）。
     */
    List<Long> findSubtreeIds(Long deptId);

    /** 新增部门：校验父级并回写 ancestors（根为 {@code ,0,id,}）。 */
    void saveDept(SysDept dept);

    /**
     * 更新部门：空字段不覆盖；显式 parentId 才移动，移动后整棵子树的 ancestors 重算。
     * 父级不存在、指向自己或自己的下级都拒绝（201 命名文案）。
     */
    void updateDept(SysDept dept);

    /** 删除部门：有下级部门或有用户归属时拒绝，不级联（201 命名文案）。 */
    void removeDept(Long id);
}
