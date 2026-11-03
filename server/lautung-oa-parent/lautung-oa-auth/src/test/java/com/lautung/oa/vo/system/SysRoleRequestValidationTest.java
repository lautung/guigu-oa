package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysRole;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #225：角色数据范围五档在 HTTP 边界的值域与 save 默认值。 */
class SysRoleRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void aNewRoleWithoutADataScopeDefaultsToSelfOnly() {
        SysRoleSaveRequest request = saveRequest();
        request.setDeptIds(java.util.List.of(7L));

        SysRole role = request.toEntity();

        assertEquals(5, role.getDataScope());
        assertEquals("审批员", role.getRoleName());
    }

    @Test
    void saveKeepsAnExplicitDataScopeAndNeverWritesDeptIdsIntoTheEntity() {
        SysRoleSaveRequest request = saveRequest();
        request.setDataScope(2);
        request.setDeptIds(java.util.List.of(7L));

        SysRole role = request.toEntity();

        assertEquals(2, role.getDataScope());
    }

    @Test
    void updateLeavesAnOmittedDataScopeForTheExistingRow() {
        SysRoleUpdateRequest request = new SysRoleUpdateRequest();
        request.setId(3L);

        assertNull(request.toEntity().getDataScope());
    }

    @Test
    void dataScopeOutsideOneToFiveIsRejected() {
        SysRoleSaveRequest tooLow = saveRequest();
        tooLow.setDataScope(0);
        SysRoleSaveRequest tooHigh = saveRequest();
        tooHigh.setDataScope(6);

        assertTrue(messages(tooLow).contains("数据范围只能为 1、2、3、4 或 5"));
        assertTrue(messages(tooHigh).contains("数据范围只能为 1、2、3、4 或 5"));
    }

    @Test
    void allFiveTiersAreAccepted() {
        for (int scope = 1; scope <= 5; scope++) {
            SysRoleSaveRequest request = saveRequest();
            request.setDataScope(scope);
            assertTrue(messages(request).isEmpty(), "scope " + scope + " should be valid");
        }
    }

    private SysRoleSaveRequest saveRequest() {
        SysRoleSaveRequest request = new SysRoleSaveRequest();
        request.setRoleName("审批员");
        request.setRoleCode("approver");
        return request;
    }

    private Set<String> messages(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
