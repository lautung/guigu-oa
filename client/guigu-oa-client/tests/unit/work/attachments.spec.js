import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  uploadAttachment
} from '@/work/attachments'

describe('work/attachments', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('uploads directly to the signed URL and registers only after PUT succeeds', async () => {
    const calls = []
    const file = { name: 'receipt.pdf', type: 'application/pdf', size: 31 }
    const result = await uploadAttachment(file, {
      draftId: 'draft-id',
      createUpload: async request => {
        calls.push(['grant', request])
        return { data: { attachmentId: 42, uploadUrl: 'https://objects.test/presigned', headers: { 'Content-Type': 'application/pdf' } } }
      },
      put: async (url, options) => {
        calls.push(['put', url, options])
        return { ok: true }
      },
      completeUpload: async id => {
        calls.push(['complete', id])
        return { data: { id, fileName: file.name } }
      }
    })

    expect(result).toEqual({ id: 42, fileName: file.name })
    expect(calls.map(call => call[0])).toEqual(['grant', 'put', 'complete'])
    expect(calls[0][1]).toMatchObject({
      draftId: 'draft-id',
      fileName: 'receipt.pdf',
      contentType: 'application/pdf',
      sizeBytes: 31
    })
    expect(calls[1][2]).toMatchObject({
      method: 'PUT',
      headers: { 'Content-Type': 'application/pdf' },
      body: file
    })
  })

  it('does not register an upload when the direct PUT fails', async () => {
    const completeUpload = vi.fn()

    await expect(uploadAttachment({ name: 'bad.bin', type: '', size: 2 }, {
      draftId: 'draft-id',
      createUpload: async () => ({ data: { attachmentId: 3, uploadUrl: 'https://objects.test/presigned', headers: {} } }),
      put: async () => ({ ok: false }),
      completeUpload
    })).rejects.toThrow('附件上传失败')

    expect(completeUpload).not.toHaveBeenCalled()
  })
})

describe('upload registration remains in the original entry', () => {
  it('does not complete an old upload when its owner changes during PUT', async () => {
    let current = true
    const completeUpload = vi.fn()
    await expect(uploadAttachment({ name: 'proof.txt', size: 1, type: 'text/plain' }, {
      draftId: 'draft',
      assertCurrent: () => { if (!current) throw new Error('进入结果已变化') },
      createUpload: async () => ({ data: { attachmentId: 7, uploadUrl: 'https://objects.test/put', headers: {} } }),
      put: async () => { current = false; return { ok: true } },
      completeUpload,
    })).rejects.toThrow('进入结果已变化')
    expect(completeUpload).not.toHaveBeenCalled()
  })
})
