package com.lautung.oa.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.config.cache.ConfigCacheKey;
import com.lautung.oa.config.mapper.ConfigMapper;
import com.lautung.oa.config.service.ConfigReader;
import com.lautung.oa.config.service.SysConfigService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysConfig;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 参数服务实现（#252，ADR 0062）：库是真相，{@code config:} 键只是读缓存。
 *
 * <p>读端口先命中缓存、未命中查库并回填；写动作按 key 失效，手动刷新用 {@code SCAN}
 * 清掉全部 {@code config:} 键（不用 {@code KEYS}）。内置项按库里的行判定保护。
 */
@Service
public class SysConfigServiceImpl extends ServiceImpl<ConfigMapper, SysConfig>
        implements SysConfigService, ConfigReader {

    private static final Set<String> VALUE_TYPES = Set.of(
            SysConfig.TYPE_STRING, SysConfig.TYPE_NUMBER, SysConfig.TYPE_BOOLEAN);

    /** 扫描提示，与监控缓存列表同档；实际结果不依赖这个数。 */
    private static final int SCAN_HINT = 200;

    /** 软删前让出唯一键：删除行不再占用原 config_key，同一键可以重新创建。 */
    private static final String DELETED_KEY_PREFIX = "__deleted__";

    private final RedisTemplate<String, String> redisTemplate;

    public SysConfigServiceImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getValue(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return null;
        }
        String cacheKey = ConfigCacheKey.of(configKey);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        SysConfig config = findByKey(configKey);
        if (config == null || config.getConfigValue() == null) {
            return null;
        }
        redisTemplate.opsForValue().set(cacheKey, config.getConfigValue());
        return config.getConfigValue();
    }

    @Override
    public boolean getBoolean(String configKey, boolean defaultValue) {
        String raw = getValue(configKey);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        String normalized = raw.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        return defaultValue;
    }

    @Override
    public void saveConfig(SysConfig config) {
        String valueType = normalizeType(config.getValueType(), true);
        config.setValueType(valueType);
        validateValue(valueType, config.getConfigValue());
        assertKeyAvailable(config.getConfigKey(), null);
        config.setBuiltin(SysConfig.BUILTIN_NO);
        try {
            save(config);
        } catch (DuplicateKeyException duplicate) {
            throw duplicateKey();
        }
    }

    @Override
    public void updateConfig(SysConfig config) {
        if (config.getId() == null) {
            throw notFound();
        }
        SysConfig existing = getById(config.getId());
        if (existing == null) {
            throw notFound();
        }

        String requestedKey = normalizeText(config.getConfigKey());
        String requestedName = normalizeText(config.getConfigName());
        String requestedType = normalizeType(config.getValueType(), false);
        String requestedRemark = normalizeText(config.getRemark());

        if (isBuiltin(existing)) {
            if (requestedKey != null && !requestedKey.equals(existing.getConfigKey())) {
                throw new BusinessException(201, "内置参数的键不能修改");
            }
            boolean metadataChanged = (requestedName != null
                    && !requestedName.equals(existing.getConfigName()))
                    || (requestedType != null && !requestedType.equals(existing.getValueType()))
                    || (requestedRemark != null && !requestedRemark.equals(existing.getRemark()));
            if (metadataChanged) {
                throw new BusinessException(201, "内置参数只允许修改参数值");
            }
            validateValue(existing.getValueType(), config.getConfigValue());
            // 内置项只落 value：元数据即使原样提交也不写回，保护不依赖调用方克制。
            config.setConfigKey(null);
            config.setConfigName(null);
            config.setValueType(null);
            config.setRemark(null);
        } else {
            String effectiveType = requestedType != null ? requestedType : existing.getValueType();
            validateValue(effectiveType, config.getConfigValue());
            if (requestedKey != null && !requestedKey.equals(existing.getConfigKey())) {
                assertKeyAvailable(requestedKey, existing.getId());
            }
            config.setConfigKey(requestedKey);
            config.setConfigName(requestedName);
            config.setValueType(requestedType);
            config.setRemark(requestedRemark);
        }

        config.setBuiltin(null);
        try {
            if (!updateById(config)) {
                throw notFound();
            }
        } catch (DuplicateKeyException duplicate) {
            throw duplicateKey();
        }
        invalidate(existing.getConfigKey());
        if (config.getConfigKey() != null) {
            invalidate(config.getConfigKey());
        }
    }

    @Override
    public void removeConfig(Long id) {
        SysConfig existing = id == null ? null : getById(id);
        if (existing == null) {
            throw notFound();
        }
        if (isBuiltin(existing)) {
            throw new BusinessException(201, "内置参数不能删除");
        }
        SysConfig releaseKey = new SysConfig();
        releaseKey.setId(id);
        releaseKey.setConfigKey(DELETED_KEY_PREFIX + id);
        updateById(releaseKey);
        if (!removeById(id)) {
            throw notFound();
        }
        invalidate(existing.getConfigKey());
    }

    @Override
    public long refreshCache() {
        List<String> targets = new ArrayList<>();
        try (Cursor<String> scanned = redisTemplate.scan(
                ScanOptions.scanOptions().match(ConfigCacheKey.PREFIX + "*").count(SCAN_HINT).build())) {
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

    private SysConfig findByKey(String configKey) {
        return getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey), false);
    }

    private void assertKeyAvailable(String configKey, Long excludeId) {
        if (!StringUtils.hasText(configKey)) {
            throw new BusinessException(201, "参数键不能为空");
        }
        LambdaQueryWrapper<SysConfig> query = new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey);
        if (excludeId != null) {
            query.ne(SysConfig::getId, excludeId);
        }
        if (baseMapper.selectCount(query) > 0) {
            throw duplicateKey();
        }
    }

    private void invalidate(String configKey) {
        if (StringUtils.hasText(configKey)) {
            redisTemplate.delete(ConfigCacheKey.of(configKey));
        }
    }

    private static boolean isBuiltin(SysConfig config) {
        return Objects.equals(config.getBuiltin(), SysConfig.BUILTIN_YES);
    }

    private static String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeType(String valueType, boolean defaultWhenBlank) {
        if (!StringUtils.hasText(valueType)) {
            return defaultWhenBlank ? SysConfig.TYPE_STRING : null;
        }
        String normalized = valueType.trim().toLowerCase(Locale.ROOT);
        if (!VALUE_TYPES.contains(normalized)) {
            throw new BusinessException(201, "参数类型不合法");
        }
        return normalized;
    }

    private static void validateValue(String valueType, String value) {
        if (!StringUtils.hasText(value) || valueType == null) {
            return;
        }
        String normalized = value.trim();
        if (SysConfig.TYPE_BOOLEAN.equals(valueType)
                && !"true".equalsIgnoreCase(normalized)
                && !"false".equalsIgnoreCase(normalized)) {
            throw new BusinessException(201, "布尔类型的参数值只能是 true 或 false");
        }
        if (SysConfig.TYPE_NUMBER.equals(valueType)) {
            try {
                new BigDecimal(normalized);
            } catch (NumberFormatException invalidNumber) {
                throw new BusinessException(201, "数字类型的参数值必须是数字");
            }
        }
    }

    private static BusinessException duplicateKey() {
        return new BusinessException(201, "参数键已存在");
    }

    private static BusinessException notFound() {
        return new BusinessException(201, "参数不存在");
    }
}
