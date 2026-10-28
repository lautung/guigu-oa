import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

/**
 * 套件级工具：唯一标记、Compose 服务控制（#121 §5 数据策略）、流程 zip 构造。
 *
 * 数据策略：不新增种子、不依赖快照；每条用例用 runId 标记自造数据，
 * 失败可原地重跑，连跑不互相污染。唯一不清理的是发布后的模板（ADR 0005 冻结，无法删除）。
 */

let cachedRunId = ''

/** 每个进程一次运行共用一个 runId（单 worker 串行，进程即一轮）。 */
export function runId(): string {
  if (!cachedRunId) {
    cachedRunId = Date.now().toString(36) + Math.floor(Math.random() * 1296).toString(36)
  }
  return cachedRunId
}

/** 写进请假原因 / 文件名的唯一标记，供 UI 与 API 双向定位。 */
export function marker(tag: string): string {
  return `stk${runId()}-${tag}`
}

const COMPOSE_DIR =
  process.env.STACK_COMPOSE_DIR ?? fileURLToPath(new URL('../../../../', import.meta.url))

/** 控制演示栈里的 service-oa（P0-8 冻结服务制造「发起结果不明」）。 */
export function composeService(action: 'pause' | 'unpause'): void {
  execFileSync('docker', ['compose', action, 'service-oa'], {
    cwd: COMPOSE_DIR,
    stdio: 'pipe',
    timeout: 60_000,
  })
}

export const STACK_DIR_ENV = 'STACK_COMPOSE_DIR'

/** 无外部依赖的 store 型 zip（BPMN 文本压缩不是必须，Flowable 接受未压缩条目）。 */
export function buildZip(entries: { name: string; data: string }[]): Buffer {
  const locals: Buffer[] = []
  const centrals: Buffer[] = []
  let offset = 0

  for (const entry of entries) {
    const nameBuf = Buffer.from(entry.name, 'utf8')
    const dataBuf = Buffer.from(entry.data, 'utf8')
    const crc = crc32(dataBuf)

    const local = Buffer.alloc(30)
    local.writeUInt32LE(0x04034b50, 0)
    local.writeUInt16LE(20, 4)
    local.writeUInt16LE(0, 6)
    local.writeUInt16LE(0, 8) // stored
    local.writeUInt16LE(0, 10)
    local.writeUInt16LE(0, 12)
    local.writeUInt32LE(crc, 14)
    local.writeUInt32LE(dataBuf.length, 18)
    local.writeUInt32LE(dataBuf.length, 22)
    local.writeUInt16LE(nameBuf.length, 26)
    local.writeUInt16LE(0, 28)
    locals.push(local, nameBuf, dataBuf)

    const central = Buffer.alloc(46)
    central.writeUInt32LE(0x02014b50, 0)
    central.writeUInt16LE(20, 4)
    central.writeUInt16LE(20, 6)
    central.writeUInt16LE(0, 8)
    central.writeUInt16LE(0, 10)
    central.writeUInt16LE(0, 12)
    central.writeUInt16LE(0, 14)
    central.writeUInt32LE(crc, 16)
    central.writeUInt32LE(dataBuf.length, 20)
    central.writeUInt32LE(dataBuf.length, 24)
    central.writeUInt16LE(nameBuf.length, 28)
    central.writeUInt32LE(offset, 42)
    centrals.push(central, nameBuf)

    offset += 30 + nameBuf.length + dataBuf.length
  }

  const centralBuf = Buffer.concat(centrals)
  const end = Buffer.alloc(22)
  end.writeUInt32LE(0x06054b50, 0)
  end.writeUInt16LE(entries.length, 8)
  end.writeUInt16LE(entries.length, 10)
  end.writeUInt32LE(centralBuf.length, 12)
  end.writeUInt32LE(offset, 16)
  return Buffer.concat([...locals, centralBuf, end])
}

let crcTable: number[] | undefined

function crc32(data: Buffer): number {
  if (!crcTable) {
    crcTable = []
    for (let n = 0; n < 256; n++) {
      let c = n
      for (let k = 0; k < 8; k++) {
        c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
      }
      crcTable[n] = c
    }
  }
  let crc = 0xffffffff
  for (const byte of data) {
    crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8)
  }
  return (crc ^ 0xffffffff) >>> 0
}

/** 可被 Flowable 部署的最小 BPMN：单个人工任务，assignee 用占位用户避免真实流转。 */
export function bpmnXml(processKey: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://guigu-oa.com/stack">
  <process id="${processKey}" name="栈测试流程" isExecutable="true">
    <startEvent id="start" />
    <userTask id="review" name="复核" flowable:assignee="bob" />
    <endEvent id="end" />
    <sequenceFlow id="f1" sourceRef="start" targetRef="review" />
    <sequenceFlow id="f2" sourceRef="review" targetRef="end" />
  </process>
</definitions>
`
}
