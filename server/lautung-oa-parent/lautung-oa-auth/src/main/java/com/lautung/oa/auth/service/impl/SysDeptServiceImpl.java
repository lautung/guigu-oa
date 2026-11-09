package com.lautung.oa.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.auth.mapper.SysDeptMapper;
import com.lautung.oa.auth.mapper.SysUserMapper;
import com.lautung.oa.auth.service.SysDeptService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysDept;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.vo.system.SysUserOptionVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门维护（#247）。ancestors 的唯一写入点：新增按父级路径拼出 {@code ,0,id,} 形状，
 * 移动时把旧前缀整体换成新前缀，子树的相对后缀不变，所以一次前缀替换就是整棵子树重算。
 */
@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    private static final long ROOT_PARENT_ID = 0L;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public List<SysDept> findTree() {
        List<SysDept> all = list(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getSortValue)
                .orderByAsc(SysDept::getId));
        return buildTree(all);
    }

    @Override
    public List<Long> findSubtreeIds(Long deptId) {
        // 已删/不存在的部门没有可选子树：返回空集让调用方得到零行，而不是整表。
        if (deptId == null || getById(deptId) == null) {
            return List.of();
        }
        return baseMapper.selectSubtreeIds(deptId);
    }

    @Override
    @Transactional
    public void saveDept(SysDept dept) {
        Long parentId = dept.getParentId() == null ? ROOT_PARENT_ID : dept.getParentId();
        String parentPath = validateParent(parentId, null);
        dept.setParentId(parentId);
        dept.setAncestors(null);
        if (dept.getStatus() == null) {
            dept.setStatus(1);
        }
        if (!save(dept)) {
            throw new BusinessException(201, "部门保存失败");
        }
        SysDept path = new SysDept();
        path.setId(dept.getId());
        path.setAncestors(parentPath + dept.getId() + ",");
        if (!updateById(path)) {
            throw new BusinessException(201, "部门保存失败");
        }
        dept.setAncestors(path.getAncestors());
    }

    @Override
    @Transactional
    public void updateDept(SysDept dept) {
        SysDept existing = dept.getId() == null ? null : getById(dept.getId());
        if (existing == null) {
            throw new BusinessException(201, "部门不存在");
        }
        boolean moving = dept.getParentId() != null
                && !dept.getParentId().equals(existing.getParentId());
        if (moving) {
            String newPath = validateParent(dept.getParentId(), existing.getId())
                    + existing.getId() + ",";
            recomputeSubtree(existing, newPath);
            dept.setAncestors(newPath);
        }
        if (!updateById(dept)) {
            throw new BusinessException(201, "部门更新失败");
        }
        // #286：清空负责人必须显式落库为 null——updateById 的默认 NOT_NULL 字段策略会吞掉 null，
        // leader_user_id 用专用语句按提交值原样写（编辑表单总是提交该字段）。置空与解析侧
        // 「负责人停用/删除视为无负责人」同口径（ADR 0065/0069）；只此一处写路径，不波及其他字段语义。
        baseMapper.update(null, new LambdaUpdateWrapper<SysDept>()
                .eq(SysDept::getId, dept.getId())
                .set(SysDept::getLeaderUserId, dept.getLeaderUserId()));
    }

    @Override
    @Transactional
    public void removeDept(Long id) {
        SysDept dept = id == null ? null : getById(id);
        if (dept == null) {
            throw new BusinessException(201, "部门不存在");
        }
        Long children = baseMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException(201, "该部门存在下级部门，不允许删除");
        }
        Long users = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, id));
        if (users != null && users > 0) {
            throw new BusinessException(201, "该部门存在用户，不允许删除");
        }
        if (!removeById(id)) {
            throw new BusinessException(201, "部门删除失败");
        }
    }

    @Override
    public List<SysUserOptionVo> findEnabledUserOptions() {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getUsername)
                .orderByAsc(SysUser::getId));
        return users.stream()
                .map(user -> new SysUserOptionVo(user.getId(), user.getUsername(), user.getName()))
                .toList();
    }

    /**
     * 校验父级并返回其「作为前缀」的路径（不含新部门自身）：根为 {@code ,0,}。
     *
     * @param movingId 被移动的部门 id（新增为 null）：父级指向自己或是自己的下级都拒绝。
     */
    private String validateParent(Long parentId, Long movingId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return "," + ROOT_PARENT_ID + ",";
        }
        if (movingId != null && parentId.equals(movingId)) {
            throw new BusinessException(201, "上级部门不能是自己");
        }
        SysDept parent = getById(parentId);
        if (parent == null) {
            throw new BusinessException(201, "上级部门不存在");
        }
        String parentPath = pathOf(parent);
        if (movingId != null && parentPath.contains("," + movingId + ",")) {
            throw new BusinessException(201, "不能把部门移动到自己的下级部门");
        }
        return parentPath;
    }

    /** 旧前缀整体换成新前缀：dept 自身与所有后代共用同一段相对后缀。 */
    private void recomputeSubtree(SysDept existing, String newPath) {
        String oldPath = pathOf(existing);
        List<SysDept> descendants = list(new LambdaQueryWrapper<SysDept>()
                .likeRight(SysDept::getAncestors, oldPath)
                .ne(SysDept::getId, existing.getId()));
        for (SysDept descendant : descendants) {
            String ancestorPath = descendant.getAncestors();
            if (ancestorPath == null || !ancestorPath.startsWith(oldPath)) {
                continue;
            }
            SysDept update = new SysDept();
            update.setId(descendant.getId());
            update.setAncestors(newPath + ancestorPath.substring(oldPath.length()));
            baseMapper.updateById(update);
        }
    }

    /** 包裹逗号路径；历史行缺 ancestors 时按「根下的一级节点」兜底，避免拼出非法前缀。 */
    private String pathOf(SysDept dept) {
        if (StringUtils.hasText(dept.getAncestors())) {
            return dept.getAncestors();
        }
        return "," + ROOT_PARENT_ID + "," + dept.getId() + ",";
    }

    /** 按 parentId 一次分组装配，不递归、不按层多次查库；父级缺席的孤儿按根展示。 */
    private List<SysDept> buildTree(List<SysDept> all) {
        Map<Long, SysDept> byId = new LinkedHashMap<>();
        for (SysDept dept : all) {
            dept.setChildren(new ArrayList<>());
            byId.put(dept.getId(), dept);
        }
        List<SysDept> roots = new ArrayList<>();
        for (SysDept dept : all) {
            SysDept parent = dept.getParentId() == null ? null : byId.get(dept.getParentId());
            if (parent == null || parent.getId().equals(dept.getId())) {
                roots.add(dept);
            } else {
                parent.getChildren().add(dept);
            }
        }
        return roots;
    }
}
