package com.lautung.oa.auth.datascope;

import com.lautung.oa.model.system.DataScope;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DataScope 值对象（ADR 0057）：不可变、三个状态字段，空集与 {@code unrestricted} 互不冒充。
 */
class DataScopeTest {

    @Test
    void unrestrictedCarriesNoCondition() {
        DataScope scope = DataScope.unrestricted();

        assertTrue(scope.isUnrestricted());
        assertTrue(scope.getDeptIds().isEmpty());
        assertNull(scope.getSelfUserId());
    }

    @Test
    void ofKeepsBothVisibleDepartmentsAndTheSelfRow() {
        DataScope scope = DataScope.of(new LinkedHashSet<>(Set.of(10L, 11L)), 7L);

        assertFalse(scope.isUnrestricted());
        assertEquals(Set.of(10L, 11L), scope.getDeptIds());
        assertEquals(7L, scope.getSelfUserId());
    }

    @Test
    void ofWithoutAnyVisibleRowIsNotUnrestricted() {
        DataScope scope = DataScope.of(Set.of(), null);

        assertFalse(scope.isUnrestricted());
        assertTrue(scope.getDeptIds().isEmpty());
        assertNull(scope.getSelfUserId());
    }

    @Test
    void ofToleratesNullDepartmentSetAndNullEntries() {
        DataScope nullSet = DataScope.of(null, null);
        assertTrue(nullSet.getDeptIds().isEmpty());

        Set<Long> withHole = new LinkedHashSet<>();
        withHole.add(10L);
        withHole.add(null);
        assertEquals(Set.of(10L), DataScope.of(withHole, null).getDeptIds());
    }

    @Test
    void departmentSetIsImmutable() {
        Set<Long> source = new LinkedHashSet<>(Set.of(10L));
        DataScope scope = DataScope.of(source, null);

        source.add(11L);

        assertEquals(Set.of(10L), scope.getDeptIds());
        assertThrows(UnsupportedOperationException.class, () -> scope.getDeptIds().add(12L));
    }
}
