package com.lautung.oa.vo.process;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AddSignVo {

    @NotNull(message = "加签类型不能为空")
    @Schema(description = "加签类型（1：前加签 2：后加签）")
    private Integer signType;

    @NotEmpty(message = "加签人不能为空")
    @Schema(description = "加签人用户id清单（1..N 人，会签口径全部通过；不能含本人或申请人）")
    private List<Long> assigneeIds;
}
