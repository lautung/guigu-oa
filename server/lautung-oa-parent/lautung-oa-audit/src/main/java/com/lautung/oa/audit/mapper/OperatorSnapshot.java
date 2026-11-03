package com.lautung.oa.audit.mapper;

import lombok.Data;

/**
 * 操作人快照（写入时快照，读侧不 join）。
 */
@Data
public class OperatorSnapshot {

    private String userName;

    private Long deptId;

    private String deptName;
}
