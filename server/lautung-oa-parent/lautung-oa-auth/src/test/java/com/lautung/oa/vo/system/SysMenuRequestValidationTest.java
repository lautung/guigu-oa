package com.lautung.oa.vo.system;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADR 0056：按钮点必须带权限标识、path/component 为空；目录与页面仍要求路由地址。 */
class SysMenuRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void buttonSaveAcceptsABlankPathAndComponentWhenPermsArePresent() {
        SysMenuSaveRequest request = saveRequest(2);
        request.setPath("");
        request.setComponent("");
        request.setPerms("system:user:add");

        assertTrue(messages(request).isEmpty());
    }

    @Test
    void buttonSaveRequiresPermsAndRejectsPathOrComponent() {
        SysMenuSaveRequest noPerms = saveRequest(2);
        noPerms.setPath("");
        noPerms.setComponent("");

        assertTrue(messages(noPerms).contains("按钮权限标识不能为空"));

        SysMenuSaveRequest withPath = saveRequest(2);
        withPath.setPath("sysUser");
        withPath.setComponent("");
        withPath.setPerms("system:user:add");

        assertTrue(messages(withPath).contains("按钮的路由地址必须为空"));

        SysMenuSaveRequest withComponent = saveRequest(2);
        withComponent.setPath("");
        withComponent.setComponent("system/sysUser/list");
        withComponent.setPerms("system:user:add");

        assertTrue(messages(withComponent).contains("按钮的组件路径必须为空"));
    }

    @Test
    void directoryAndPageStillRequireAPath() {
        SysMenuSaveRequest directory = saveRequest(0);
        directory.setPath("");

        assertTrue(messages(directory).contains("路由地址不能为空"));

        SysMenuSaveRequest page = saveRequest(1);
        page.setPath("");
        page.setComponent("system/sysUser/list");

        assertTrue(messages(page).contains("路由地址不能为空"));
    }

    @Test
    void buttonUpdateRequiresPermsOnlyWhenTypeIsExplicitlySent() {
        SysMenuUpdateRequest update = updateRequest(2);
        update.setPerms("system:user:edit");

        assertTrue(messages(update).isEmpty());

        SysMenuUpdateRequest noPerms = updateRequest(2);
        assertTrue(messages(noPerms).contains("按钮权限标识不能为空"));

        SysMenuUpdateRequest withPath = updateRequest(2);
        withPath.setPath("sysUser");
        withPath.setPerms("system:user:edit");
        assertTrue(messages(withPath).contains("按钮的路由地址必须为空"));

        SysMenuUpdateRequest nameOnly = new SysMenuUpdateRequest();
        nameOnly.setId(7L);
        nameOnly.setName("按钮改名");
        assertTrue(messages(nameOnly).isEmpty());
    }

    private SysMenuSaveRequest saveRequest(int type) {
        SysMenuSaveRequest request = new SysMenuSaveRequest();
        request.setParentId(1L);
        request.setName("菜单");
        request.setType(type);
        return request;
    }

    private SysMenuUpdateRequest updateRequest(int type) {
        SysMenuUpdateRequest request = new SysMenuUpdateRequest();
        request.setId(7L);
        request.setType(type);
        return request;
    }

    private Set<String> messages(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
