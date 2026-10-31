# 行数对照（perf #182）

种子快照（两轮同源，before 轮造数后 `perf/ops/row-counts.sh`）：

```
oa_process	10001
oa_process_record	14002
ACT_RU_TASK	6000
ACT_RU_IDENTITYLINK	6000
ACT_HI_TASKINST	10001
ACT_HI_PROCINST	10001
ACT_HI_ACTINST	38005
ACT_GE_BYTEARRAY	26003
```

基线-1 档收尾（写负载每轮新增实例，档间由 restore 清除）：

```
before（before/rows-baseline-1.txt）
oa_process	22927
oa_process_record	40108
ACT_RU_TASK	5746
ACT_RU_IDENTITYLINK	5746
ACT_HI_TASKINST	22927
ACT_HI_PROCINST	22927
ACT_HI_ACTINST	103143
ACT_GE_BYTEARRAY	51601

after（after/rows-baseline-1.txt）
oa_process	26178
oa_process_record	46074
ACT_RU_TASK	6282
ACT_RU_IDENTITYLINK	6282
ACT_HI_TASKINST	26178
ACT_HI_PROCINST	26178
ACT_HI_ACTINST	118326
ACT_GE_BYTEARRAY	58639
```

两轮同机制：混合负载写请求使实例数增长，档间恢复快照归零；`ACT_*` 由引擎维护，无直插。
各档行数文件见 `before/`、`after/` 子目录 `rows-<档位>.txt`；生成命令见 `README.md`。
