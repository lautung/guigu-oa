package com.lautung.oa.dict.service.impl;

import com.lautung.oa.dict.mapper.DictDataMapper;
import com.lautung.oa.dict.mapper.DictTypeMapper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysDictData;
import com.lautung.oa.model.system.SysDictType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 字典软删与标签缓存边界（PR #260 审查）：软删让出唯一键的更新失败时不得继续删行；
 * 类型改名不得留下不可达的数据行；标签缓存只对真实类型落键并带 TTL。
 */
class SysDictServiceImplTest {

    private final DictTypeMapper typeMapper = mock(DictTypeMapper.class);

    private final DictDataMapper dataMapper = mock(DictDataMapper.class);

    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final SysDictServiceImpl service = new SysDictServiceImpl(dataMapper, redisTemplate);

    @BeforeAll
    static void registerTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysDictType.class);
        TableInfoHelper.initTableInfo(assistant, SysDictData.class);
    }

    @BeforeEach
    void injectCollaborators() {
        ReflectionTestUtils.setField(service, "baseMapper", typeMapper);
        when(redisTemplate.opsForValue()).thenReturn(values);
    }

    @Test
    void removeTypeIsTransactionalAndStopsWhenReleaseUpdateAffectsNoRow() throws Exception {
        SysDictType existing = type(7L, "t251_type");
        when(typeMapper.selectById(7L)).thenReturn(existing);
        when(dataMapper.selectCount(any())).thenReturn(0L);
        when(typeMapper.updateById(any(SysDictType.class))).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class, () -> service.removeType(7L));

        assertEquals(201, error.getCode());
        assertEquals("字典类型不存在", error.getMsg());
        verify(typeMapper, never()).deleteById(any(java.io.Serializable.class));
        assertNotNull(SysDictServiceImpl.class.getMethod("removeType", Long.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void removeDataIsTransactionalAndStopsWhenReleaseUpdateAffectsNoRow() throws Exception {
        when(dataMapper.selectById(9L)).thenReturn(data(9L, "t251_type", "1"));
        when(dataMapper.updateById(any(SysDictData.class))).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class, () -> service.removeData(9L));

        assertEquals(201, error.getCode());
        assertEquals("字典数据不存在", error.getMsg());
        verify(dataMapper, never()).deleteById(any(java.io.Serializable.class));
        assertNotNull(SysDictServiceImpl.class.getMethod("removeData", Long.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void updateTypeRejectsRenamingATypeThatStillHasData() {
        SysDictType existing = type(7L, "t251_old");
        SysDictType update = new SysDictType();
        update.setId(7L);
        update.setDictType("t251_new");
        when(typeMapper.selectById(7L)).thenReturn(existing);
        when(typeMapper.selectCount(any())).thenReturn(0L);
        when(dataMapper.selectCount(any())).thenReturn(2L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.updateType(update));

        assertEquals(201, error.getCode());
        assertEquals("字典类型下仍有数据，不能修改类型编码", error.getMsg());
        verify(typeMapper, never()).updateById(any(SysDictType.class));
    }

    @Test
    void updateTypeAllowsRenamingAnEmptyType() {
        SysDictType existing = type(7L, "t251_old");
        SysDictType update = new SysDictType();
        update.setId(7L);
        update.setDictType("t251_new");
        when(typeMapper.selectById(7L)).thenReturn(existing);
        when(typeMapper.selectCount(any())).thenReturn(0L);
        when(dataMapper.selectCount(any())).thenReturn(0L);
        when(typeMapper.updateById(update)).thenReturn(1);

        service.updateType(update);

        verify(typeMapper).updateById(update);
    }

    @Test
    void findEnabledDataCachesKnownTypeWithTtl() {
        when(typeMapper.selectCount(any())).thenReturn(1L);
        when(values.get("dict:t251_type")).thenReturn(null);
        when(dataMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());

        assertTrue(service.findEnabledData("t251_type").isEmpty());

        verify(values).set(eq("dict:t251_type"), anyString(), eq(Duration.ofHours(1)));
    }

    @Test
    void findEnabledDataDoesNotCacheUnknownType() {
        when(typeMapper.selectCount(any())).thenReturn(0L);

        assertTrue(service.findEnabledData("t251_missing").isEmpty());

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verify(dataMapper, never()).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    void findEnabledDataRejectsOverlongOrMalformedTypeWithoutTouchingStorage() {
        assertTrue(service.findEnabledData("a".repeat(101)).isEmpty());
        assertTrue(service.findEnabledData("bad type").isEmpty());

        verifyNoInteractions(typeMapper);
        verifyNoInteractions(values);
    }

    @Test
    void findEnabledDataServesCacheWithoutExistenceQuery() {
        when(values.get("dict:t251_type")).thenReturn("[]");

        assertTrue(service.findEnabledData(" t251_type ").isEmpty());

        verify(typeMapper, never()).selectCount(any());
        verify(dataMapper, never()).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    void findEnabledDataBatchRejectsMoreThanFiftyTypesBeforeAnyRead() {
        List<String> types = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(index -> "t251_" + index)
                .toList();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.findEnabledData(types));

        assertEquals(201, error.getCode());
        assertEquals("字典类型一次最多查询 50 个", error.getMsg());
        verifyNoInteractions(values);
        verify(typeMapper, never()).selectCount(any());
    }

    @Test
    void findEnabledDataBatchAcceptsFiftyTypesAndTrimsDuplicates() {
        when(values.get(anyString())).thenReturn("[]");
        List<String> fifty = java.util.stream.IntStream.rangeClosed(1, 50)
                .mapToObj(index -> "t251_" + index)
                .toList();

        assertEquals(50, service.findEnabledData(fifty).size());

        var deduplicated = service.findEnabledData(List.of(" t251_type ", "t251_type", "", " "));
        assertEquals(java.util.Set.of("t251_type"), deduplicated.keySet());
        verify(values, org.mockito.Mockito.times(1)).get("dict:t251_type");
        verify(typeMapper, never()).selectCount(any());
    }

    @Test
    void saveTypeRejectsMalformedOrOverlongDictType() {
        for (String dictType : List.of("中文类型", "bad:type", "a".repeat(101))) {
            SysDictType type = type(null, dictType);
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.saveType(type));

            assertEquals(201, error.getCode());
            assertEquals("字典类型只能包含字母、数字、下划线、点和短横线，且不能超过100个字符",
                    error.getMsg());
        }
        verify(typeMapper, never()).insert(any(SysDictType.class));
    }

    @Test
    void updateTypeRejectsMalformedDictTypeBeforeWriting() {
        when(typeMapper.selectById(7L)).thenReturn(type(7L, "t251_old"));
        SysDictType update = new SysDictType();
        update.setId(7L);
        update.setDictType("bad:type");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateType(update));

        assertEquals(201, error.getCode());
        assertEquals("字典类型只能包含字母、数字、下划线、点和短横线，且不能超过100个字符",
                error.getMsg());
        verify(typeMapper, never()).updateById(any(SysDictType.class));
    }

    @Test
    void saveDataRejectsMalformedOrUnknownDictType() {
        SysDictData malformed = data(null, "bad:type", "1");
        BusinessException shape = assertThrows(BusinessException.class,
                () -> service.saveData(malformed));
        assertEquals(201, shape.getCode());
        assertEquals("字典类型只能包含字母、数字、下划线、点和短横线，且不能超过100个字符",
                shape.getMsg());

        when(typeMapper.selectCount(any())).thenReturn(0L);
        SysDictData unknown = data(null, "t251_missing", "1");
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.saveData(unknown));
        assertEquals(201, missing.getCode());
        assertEquals("字典类型不存在", missing.getMsg());
        verify(dataMapper, never()).insert(any(SysDictData.class));
    }

    @Test
    void updateDataRejectsUnknownTargetDictType() {
        when(dataMapper.selectById(9L)).thenReturn(data(9L, "t251_old", "1"));
        when(typeMapper.selectCount(any())).thenReturn(0L);
        SysDictData update = data(9L, "t251_missing", "1");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateData(update));

        assertEquals(201, error.getCode());
        assertEquals("字典类型不存在", error.getMsg());
        verify(dataMapper, never()).updateById(any(SysDictData.class));
    }

    private static SysDictType type(Long id, String dictType) {
        SysDictType type = new SysDictType();
        type.setId(id);
        type.setDictName("t251 字典");
        type.setDictType(dictType);
        type.setStatus(SysDictType.STATUS_ENABLED);
        return type;
    }

    private static SysDictData data(Long id, String dictType, String value) {
        SysDictData data = new SysDictData();
        data.setId(id);
        data.setDictType(dictType);
        data.setDictLabel("t251 标签");
        data.setDictValue(value);
        data.setStatus(SysDictData.STATUS_ENABLED);
        return data;
    }
}
