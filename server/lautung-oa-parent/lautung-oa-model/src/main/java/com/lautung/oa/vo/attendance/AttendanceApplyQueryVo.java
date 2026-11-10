package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 我的申请列表筛选（#282）：按状态档可选过滤；不按人过滤——构造上只读本人。
 */
@Data
@Schema(description = "我的申请查询")
public class AttendanceApplyQueryVo {

    @Schema(description = "状态（0 草稿 / 1 审批中 / 2 通过 / -1 拒绝 / -2 撤回；缺省全部）")
    private Integer status;
}
