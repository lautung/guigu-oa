package com.atguigu.process.cc;

import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.model.process.Process;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.mapper.OaProcessMapper;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CarbonCopyServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final OaProcessMapper processMapper = mock(OaProcessMapper.class);
    private final CarbonCopyService service = new CarbonCopyService(jdbcTemplate, processMapper);

    @Test
    void copyAtStartIgnoresEmptyRecipientLists() {
        service.copyAtStart(user(1L), 9L, null);
        service.copyAtStart(user(1L), 9L, List.of());

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void copyAtStartRequiresIdentityAndProcess() {
        assertEquals(205, code(() -> service.copyAtStart(null, 9L, List.of(2L))));
        assertEquals(205, code(() -> service.copyAtStart(user(null), 9L, List.of(2L))));
        assertEquals(205, code(() -> service.copyAtStart(user(1L), null, List.of(2L))));
    }

    @Test
    void copyAtStartRejectsNullAndInactiveRecipients() {
        assertEquals(201, code(() -> service.copyAtStart(user(1L), 9L, java.util.Arrays.asList(2L, null))));

        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(2L));
        assertEquals(201, code(() -> service.copyAtStart(user(1L), 9L, List.of(2L, 3L))));
    }

    @Test
    void copyAtStartSkipsTheStarterAndInsertsEachRecipient() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(2L));

        service.copyAtStart(user(1L), 9L, List.of(1L, 2L));

        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    void copyAtStartReturnsWhenOnlyTheStarterWasNamed() {
        service.copyAtStart(user(1L), 9L, List.of(1L));

        verify(jdbcTemplate, never()).queryForList(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void duplicateGrantsAreSwallowed() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(2L));
        doThrow(new DuplicateKeyException("duplicate"))
                .when(jdbcTemplate).update(anyString(), any(Object[].class));

        service.copyAtStart(user(1L), 9L, List.of(2L));
    }

    @Test
    void findRecipientsExcludesTheCurrentUserAndMapsRows() throws Exception {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<CarbonCopyRecipient>>any(),
                any(Object[].class)))
                .thenReturn(List.of(new CarbonCopyRecipient(2L, "bob", "鲍勃")));

        assertEquals(1, service.findRecipients(null).size());
        assertEquals(1, service.findRecipients(1L).size());

        ArgumentCaptor<RowMapper<CarbonCopyRecipient>> mapper =
                ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbcTemplate, times(2)).query(anyString(), mapper.capture(), any(Object[].class));
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(2L);
        when(resultSet.getString("username")).thenReturn("bob");
        when(resultSet.getString("name")).thenReturn("鲍勃");
        CarbonCopyRecipient recipient = mapper.getValue().mapRow(resultSet, 0);
        assertEquals("bob", recipient.username());
    }

    @Test
    void findMinePagesRowsAndMapsTheProjection() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(3L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ProcessVo>>any(),
                any(Object[].class)))
                .thenReturn(List.of());

        Page<ProcessVo> page = new Page<>(2, 10);
        var result = service.findMine(page, 1L);

        assertEquals(3L, result.getTotal());
        ArgumentCaptor<RowMapper<ProcessVo>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbcTemplate).query(anyString(), mapper.capture(), any(Object[].class));
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(9L);
        when(resultSet.getTimestamp("create_time")).thenReturn(new Timestamp(1L));
        when(resultSet.getString("process_code")).thenReturn("P-9");
        when(resultSet.getString("title")).thenReturn("请假");
        when(resultSet.getInt("status")).thenReturn(1);
        ProcessVo vo = mapper.getValue().mapRow(resultSet, 0);
        assertEquals(9L, vo.getId());
        assertEquals("请假", vo.getTitle());
        assertEquals(1, vo.getStatus());
    }

    @Test
    void findMineTreatsAMissingCountAsZero() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(null);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ProcessVo>>any(),
                any(Object[].class)))
                .thenReturn(List.of());

        assertEquals(0L, service.findMine(new Page<>(1, 10), 1L).getTotal());
    }

    @Test
    void isMemberRequiresIdentityAndAnExistingGrant() {
        assertFalse(service.isMember(null, process(9L)));
        assertFalse(service.isMember(user(null), process(9L)));
        assertFalse(service.isMember(user(1L), null));
        assertFalse(service.isMember(user(1L), process(null)));

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(null);
        assertFalse(service.isMember(user(1L), process(9L)));

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        assertTrue(service.isMember(user(1L), process(9L)));
    }

    @Test
    void copyOnTaskCreatedIgnoresIncompleteTriggers() {
        service.copyOnTaskCreated(null);
        service.copyOnTaskCreated(new CarbonCopyTrigger("leave", "task-1", "task-1", " "));
        service.copyOnTaskCreated(new CarbonCopyTrigger("leave", "task-1", " ", "9"));
        service.copyOnTaskCreated(new CarbonCopyTrigger("leave", "task-1", "task-1", "not-a-number"));

        verify(processMapper, never()).selectById(any());
    }

    @Test
    void copyOnTaskCreatedSkipsMissingProcessesAndTheApplicant() {
        when(processMapper.selectById(9L)).thenReturn(null);
        service.copyOnTaskCreated(new CarbonCopyTrigger("leave", "task-1", "task-1", "9"));

        Process process = process(9L);
        process.setUserId(1L);
        when(processMapper.selectById(9L)).thenReturn(process);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(1L, 2L));

        service.copyOnTaskCreated(new CarbonCopyTrigger("leave", "task-1", "task-1", "9"));

        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
    }

    private int code(org.junit.jupiter.api.function.Executable executable) {
        return assertThrows(GuiguException.class, executable).getCode();
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        return user;
    }

    private Process process(Long id) {
        Process process = new Process();
        process.setId(id);
        return process;
    }
}
