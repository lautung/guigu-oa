import { beforeEach, describe, expect, it, vi } from 'vitest'

const { messageMock, confirmMock, closeMock } = vi.hoisted(() => ({
  messageMock: vi.fn(),
  confirmMock: vi.fn(),
  closeMock: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: Object.assign(messageMock, {
    error: vi.fn(),
    success: vi.fn(),
    close: vi.fn(),
  }),
  ElMessageBox: { confirm: confirmMock, close: closeMock },
}))

import { listPageEffects } from '@/list/list-page-browser'

describe('listPageEffects', () => {
  beforeEach(() => {
    messageMock.mockReset()
    confirmMock.mockReset()
  })

  it('shows a timed toast with the requested kind', () => {
    listPageEffects().notify('保存成功', 'success')

    expect(messageMock).toHaveBeenCalledWith({
      message: '保存成功',
      type: 'success',
      duration: 3000,
    })
  })

  it('treats a confirmed dialog as approval', async () => {
    confirmMock.mockResolvedValue('confirm')

    await expect(listPageEffects().confirm('确定删除？')).resolves.toBe(true)
    expect(confirmMock).toHaveBeenCalledWith('确定删除？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  })

  it('treats a dismissed dialog as refusal', async () => {
    confirmMock.mockRejectedValue(new Error('cancel'))

    await expect(listPageEffects().confirm('确定删除？')).resolves.toBe(false)
  })
})
