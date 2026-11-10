# 考勤日历收成一个 module，日状态与请假折算共用同一优先级

ADR 0073 定了考勤域的工作日公式（休假日覆盖 > 补班日覆盖 > 组工作日）与「日状态读时推导」；#281/#282 落地后，同一公式出现两份实现：`AttendanceWorkCalendar.isWorkday`（`AttendanceWorkCalendar.java:88-98`）给请假提交定稿天数，`AttendanceDayStatusServiceImpl.dayType`（`AttendanceDayStatusServiceImpl.java:339-351`）给逐日状态与月度统计的应出勤。两者只共享 `containsWorkday`，各自装载成员/考勤组/班次/节假日区间，无组或无班次时口径已经不同——`WorkCalendar.calculate` 抛 201（`AttendanceWorkCalendar.java:61-71`），日状态返回 `hasGroup=false` 的未落状态 VO（`AttendanceDayStatusServiceImpl.java:160-165`、`190-193`）。今天的一致性靠人工同步，改动只落在一边时两套合同测试各自都绿；请假天数和统计应出勤会静默分叉。本 ADR 只定 module 形状与规则 owner，不改 ADR 0073 的工作日公式、日状态九枚举、统计口径或表结构。

## 决定

- 新增考勤日历 module（`attendance.calendar`）：按用户与日期区间打开一个日历视图，视图回答这一天是什么日类型、区间内有多少个工作日、日工时多少。打开时一次性装载成员、考勤组、班次与区间节假日，视图可反复问，不逐日回表。
- 工作日优先级唯一 owner：休假日覆盖补班日与组工作日，补班日覆盖组工作日；组工作日的多选解析公式随迁。`AttendanceWorkCalendar` 删除，不保留第二份实现。
- 日工时唯一 owner：下班 − 上班 − 午休（无午休不减），非正数兜底 8 小时；从 `AttendanceWorkCalendar` 迁入日历。
- 事实与拒绝分离：日历不回答「能不能提交请假」，也不抛「未加入考勤组/无有效班次」的 201。未入组、无班次返回可判定的空形状；请假提交仍由请假 adapter 按现有文案拒绝，日状态仍返回未落状态的 VO。拒绝策略留在消费方，日历只回答日期事实。
- 消费方：`AttendanceDayStatusService`（逐日、月度、统计下钻与月度统计的应出勤）与请假提交的定稿（天数、小时数、日工时）都从日历取同一份口径。打卡校验与补卡预览继续经日状态，不直接消费日历。
- 不改表结构、不改 HTTP 契约、不改客户端；日状态 VO 与月统计 VO 的字段形状不变。

## 测试

- 日历 module 在自己的模块内建单测（`lautung-oa-attendance/src/test`，考勤模块此前没有）钉住：休假日覆盖工作日、补班日覆盖休息日、组工作日解析、未入组、无班次、跨月区间、午休参与日工时、非正工时兜底 8 小时。
- `AttendanceDayStatusContractTest` 与 `AttendanceLeaveContractTest` 保留接线、权限、文案与响应形状断言；删掉与日历单测重复的规则组合，规则只在一个测试面断言。
- 合同测试不得因抽取改变现有响应：请假提交的 201 文案、日状态九枚举与 `pending` 语义都保持。

## 考虑过的替代方案

- **只抽 `isWorkday` 静态函数**：两份上下文装载、无组兜底与拒绝口径仍在两处，规则 owner 没有真正收拢。
- **日状态逐日调用 `WorkCalendar.calculate`**：每个日期都会重新查成员/组/班次/节假日，区间推导退化成 N 次查询；把装载收进区间视图才保留今天的单次读。
- **让日历抛 201**：读模块耦合写路径的拒绝文案，日状态页会被迫处理「未入组」异常；事实与拒绝分离更诚实。
- **把日历放 `model` 或 `common`**：它需要本域 mapper 与配置读取，放通用模块会把 attendance 的依赖倒到上游。
- **保留两份实现、加一条对账测试**：能发现分叉但不能阻止分叉，规则改动仍要改两处。

## 后果

- `AttendanceWorkCalendar` 删除；请假提交与日状态推导共用日历视图，统计应出勤与请假定稿不再可能各算一套。
- GLOSSARY 增补「考勤日历」。
- 服务端模块地图在实现票更新：attendance 模块新增 `calendar` 包，仍只依赖 `model`/`framework`/`common` 与本域 mapper，不新增模块依赖边。
- 实现票（C2）先落；业务单据生命周期（ADR 0075）的请假 adapter 依赖本 module 的区间视图，作为其阻塞前置。
- ADR 0073 的工作日公式、日状态优先级与统计口径不变；本 ADR 只把同一决定的实现收成一处。

Related: ADR 0073（考勤域形状与工作日公式）、0075（业务单据生命周期）；issue #278/#281/#282/#283；蓝图 2.0 §9。
