package com.lautung.oa.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.dict.cache.DictCacheKey;
import com.lautung.oa.dict.mapper.DictDataMapper;
import com.lautung.oa.dict.mapper.DictTypeMapper;
import com.lautung.oa.dict.service.SysDictService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysDictData;
import com.lautung.oa.model.system.SysDictType;
import com.lautung.oa.vo.system.DictDataVo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 字典服务实现（#251，ADR 0062）：库是真相，{@code dict:<dictType>} 键只是读缓存。
 *
 * <p>读路径先命中缓存、未命中查库并回填；写动作按类型失效，手动刷新用 {@code SCAN}
 * 清掉全部 {@code dict:} 键（不用 {@code KEYS}）。类型/数据行软删前让出唯一键，
 * 允许同一编码或同名值重新创建。
 */
@Service
public class SysDictServiceImpl extends ServiceImpl<DictTypeMapper, SysDictType>
        implements SysDictService {

    private static final TypeReference<List<DictDataVo>> DICT_LIST_TYPE =
            new TypeReference<>() {
            };

    /** 扫描提示，与监控缓存列表同档；实际结果不依赖这个数。 */
    private static final int SCAN_HINT = 200;

    /** 软删前让出唯一键：删除行不再占用原编码/值，同一编码可以重新创建。 */
    private static final String DELETED_PREFIX = "__deleted__";

    /** 标签缓存 TTL：字典读多写少，脏键最多滞留一小时；写动作仍即时失效。 */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    /** 与 {@code sys_dict_type.dict_type VARCHAR(100)} 对齐，挡住任意长度/形状的缓存键。 */
    private static final int DICT_TYPE_MAX_LENGTH = 100;

    private static final Pattern DICT_TYPE_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");

    private final DictDataMapper dictDataMapper;

    private final RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SysDictServiceImpl(DictDataMapper dictDataMapper,
                              RedisTemplate<String, String> redisTemplate) {
        this.dictDataMapper = dictDataMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveType(SysDictType type) {
        String dictName = requireText(type.getDictName(), "字典名称不能为空");
        String dictType = requireText(type.getDictType(), "字典类型不能为空");
        type.setDictName(dictName);
        type.setDictType(dictType);
        type.setStatus(normalizeStatus(type.getStatus()));
        type.setRemark(normalizeText(type.getRemark()));
        assertTypeNameAvailable(dictName, null);
        assertDictTypeAvailable(dictType, null);
        try {
            save(type);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(201, "字典类型或名称已存在");
        }
    }

    @Override
    public void updateType(SysDictType type) {
        if (type.getId() == null) {
            throw typeNotFound();
        }
        SysDictType existing = getById(type.getId());
        if (existing == null) {
            throw typeNotFound();
        }
        String dictName = normalizeText(type.getDictName());
        String dictType = normalizeText(type.getDictType());
        String effectiveName = dictName != null ? dictName : existing.getDictName();
        String effectiveType = dictType != null ? dictType : existing.getDictType();
        assertTypeNameAvailable(effectiveName, existing.getId());
        assertDictTypeAvailable(effectiveType, existing.getId());
        boolean typeChanged = dictType != null && !dictType.equals(existing.getDictType());
        if (typeChanged && dataCount(existing.getDictType()) > 0) {
            // 改名会让旧类型下的数据行不可达，也绕过 removeType 的「有数据拒删」。
            throw new BusinessException(201, "字典类型下仍有数据，不能修改类型编码");
        }

        type.setDictName(dictName);
        type.setDictType(dictType);
        type.setStatus(type.getStatus() == null
                ? null : normalizeStatus(type.getStatus()));
        type.setRemark(normalizeText(type.getRemark()));
        try {
            if (!updateById(type)) {
                throw typeNotFound();
            }
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(201, "字典类型或名称已存在");
        }
        invalidate(existing.getDictType());
        if (dictType != null) {
            invalidate(dictType);
        }
    }

    @Override
    @Transactional
    public void removeType(Long id) {
        SysDictType existing = id == null ? null : getById(id);
        if (existing == null) {
            throw typeNotFound();
        }
        if (dataCount(existing.getDictType()) > 0) {
            throw new BusinessException(201, "字典类型下仍有数据，不能删除");
        }
        SysDictType releaseKeys = new SysDictType();
        releaseKeys.setId(id);
        releaseKeys.setDictName(DELETED_PREFIX + id);
        releaseKeys.setDictType(DELETED_PREFIX + id);
        if (!updateById(releaseKeys)) {
            throw typeNotFound();
        }
        if (!removeById(id)) {
            throw typeNotFound();
        }
        invalidate(existing.getDictType());
    }

    @Override
    public IPage<SysDictData> pageData(IPage<SysDictData> page, Wrapper<SysDictData> queryWrapper) {
        return dictDataMapper.selectPage(page, queryWrapper);
    }

    @Override
    public void saveData(SysDictData data) {
        String dictType = requireText(data.getDictType(), "字典类型不能为空");
        String dictLabel = requireText(data.getDictLabel(), "字典标签不能为空");
        String dictValue = requireText(data.getDictValue(), "字典值不能为空");
        data.setDictType(dictType);
        data.setDictLabel(dictLabel);
        data.setDictValue(dictValue);
        data.setSortValue(data.getSortValue() == null ? 0 : data.getSortValue());
        data.setStatus(normalizeStatus(data.getStatus()));
        data.setRemark(normalizeText(data.getRemark()));
        assertDictValueAvailable(dictType, dictValue, null);
        try {
            dictDataMapper.insert(data);
        } catch (DuplicateKeyException duplicate) {
            throw duplicateValue();
        }
        invalidate(dictType);
    }

    @Override
    public void updateData(SysDictData data) {
        if (data.getId() == null) {
            throw dataNotFound();
        }
        SysDictData existing = dictDataMapper.selectById(data.getId());
        if (existing == null) {
            throw dataNotFound();
        }
        String dictLabel = normalizeText(data.getDictLabel());
        String dictValue = normalizeText(data.getDictValue());
        String dictType = normalizeText(data.getDictType());
        String effectiveType = dictType != null ? dictType : existing.getDictType();
        String effectiveValue = dictValue != null ? dictValue : existing.getDictValue();
        assertDictValueAvailable(effectiveType, effectiveValue, existing.getId());

        data.setDictType(dictType);
        data.setDictLabel(dictLabel);
        data.setDictValue(dictValue);
        data.setStatus(data.getStatus() == null ? null : normalizeStatus(data.getStatus()));
        if (data.getSortValue() == null) {
            data.setSortValue(null);
        }
        data.setRemark(normalizeText(data.getRemark()));
        try {
            if (dictDataMapper.updateById(data) == 0) {
                throw dataNotFound();
            }
        } catch (DuplicateKeyException duplicate) {
            throw duplicateValue();
        }
        invalidate(existing.getDictType());
        if (dictType != null) {
            invalidate(dictType);
        }
    }

    @Override
    @Transactional
    public void removeData(Long id) {
        SysDictData existing = id == null ? null : dictDataMapper.selectById(id);
        if (existing == null) {
            throw dataNotFound();
        }
        SysDictData releaseValue = new SysDictData();
        releaseValue.setId(id);
        releaseValue.setDictValue(DELETED_PREFIX + id);
        if (dictDataMapper.updateById(releaseValue) == 0) {
            throw dataNotFound();
        }
        if (dictDataMapper.deleteById(id) == 0) {
            throw dataNotFound();
        }
        invalidate(existing.getDictType());
    }

    @Override
    public List<DictDataVo> findEnabledData(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            return List.of();
        }
        String normalizedType = dictType.trim();
        if (normalizedType.length() > DICT_TYPE_MAX_LENGTH
                || !DICT_TYPE_PATTERN.matcher(normalizedType).matches()) {
            return List.of();
        }
        if (baseMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, normalizedType)) == 0) {
            // 未知类型不落键：读面的 PathVariable 不能成为任意 dict:* 键的来源。
            return List.of();
        }
        String cacheKey = DictCacheKey.of(normalizedType);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                List<DictDataVo> hit = objectMapper.readValue(cached, DICT_LIST_TYPE);
                return hit == null ? List.of() : hit;
            } catch (RuntimeException stale) {
                // 缓存形状脏了（旧版本/手工写入）：回库重建，不让读路径 500。
                redisTemplate.delete(cacheKey);
            }
        }
        List<DictDataVo> loaded = queryEnabledData(normalizedType);
        redisTemplate.opsForValue().set(
                cacheKey, objectMapper.writeValueAsString(loaded), CACHE_TTL);
        return loaded;
    }

    @Override
    public Map<String, List<DictDataVo>> findEnabledData(Collection<String> dictTypes) {
        Map<String, List<DictDataVo>> result = new LinkedHashMap<>();
        if (dictTypes == null) {
            return result;
        }
        for (String dictType : dictTypes) {
            if (StringUtils.hasText(dictType)) {
                result.put(dictType.trim(), findEnabledData(dictType.trim()));
            }
        }
        return result;
    }

    @Override
    public long refreshCache() {
        List<String> targets = new ArrayList<>();
        try (Cursor<String> scanned = redisTemplate.scan(
                ScanOptions.scanOptions().match(DictCacheKey.PREFIX + "*").count(SCAN_HINT).build())) {
            while (scanned.hasNext()) {
                targets.add(scanned.next());
            }
        }
        if (targets.isEmpty()) {
            return 0L;
        }
        Long deleted = redisTemplate.delete(targets);
        return deleted == null ? 0L : deleted;
    }

    private List<DictDataVo> queryEnabledData(String dictType) {
        return dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getDictType, dictType)
                        .eq(SysDictData::getStatus, SysDictData.STATUS_ENABLED)
                        .orderByAsc(SysDictData::getSortValue)
                        .orderByAsc(SysDictData::getId))
                .stream()
                .map(DictDataVo::of)
                .toList();
    }

    private long dataCount(String dictType) {
        return dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType));
    }

    private void assertTypeNameAvailable(String dictName, Long excludeId) {
        LambdaQueryWrapper<SysDictType> query = new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictName, dictName);
        if (excludeId != null) {
            query.ne(SysDictType::getId, excludeId);
        }
        if (baseMapper.selectCount(query) > 0) {
            throw new BusinessException(201, "字典名称已存在");
        }
    }

    private void assertDictTypeAvailable(String dictType, Long excludeId) {
        LambdaQueryWrapper<SysDictType> query = new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType);
        if (excludeId != null) {
            query.ne(SysDictType::getId, excludeId);
        }
        if (baseMapper.selectCount(query) > 0) {
            throw new BusinessException(201, "字典类型已存在");
        }
    }

    private void assertDictValueAvailable(String dictType, String dictValue, Long excludeId) {
        LambdaQueryWrapper<SysDictData> query = new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getDictValue, dictValue);
        if (excludeId != null) {
            query.ne(SysDictData::getId, excludeId);
        }
        if (dictDataMapper.selectCount(query) > 0) {
            throw duplicateValue();
        }
    }

    private void invalidate(String dictType) {
        if (StringUtils.hasText(dictType)) {
            redisTemplate.delete(DictCacheKey.of(dictType));
        }
    }

    private static int normalizeStatus(Integer status) {
        if (status == null) {
            return SysDictType.STATUS_ENABLED;
        }
        if (!Objects.equals(status, SysDictType.STATUS_ENABLED)
                && !Objects.equals(status, SysDictType.STATUS_DISABLED)) {
            throw new BusinessException(201, "字典状态不合法");
        }
        return status;
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(201, message);
        }
        return value.trim();
    }

    private static String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static BusinessException duplicateValue() {
        return new BusinessException(201, "字典值已存在");
    }

    private static BusinessException typeNotFound() {
        return new BusinessException(201, "字典类型不存在");
    }

    private static BusinessException dataNotFound() {
        return new BusinessException(201, "字典数据不存在");
    }
}
