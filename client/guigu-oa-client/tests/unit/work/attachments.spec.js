import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  clearDraftId,
  draftStorageKey,
  getOrCreateDraftId,
  uploadAttachment
} from '@/work/attachments'

describe('work/attachments', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('reuses a per-template draft id for pending attachments', () => {
    const values = new Map()
    const storage = {
      getItem: key => values.get(key) || null,
      setItem: (key, value) => values.set(key, value),
      removeItem: key => values.delete(key)
    }
    const uuid = vi.fn(() => 'fixed-draft-id')

    expect(getOrCreateDraftId(12, storage, uuid)).toBe('fixed-draft-id')
    expect(getOrCreateDraftId(12, storage, uuid)).toBe('fixed-draft-id')
    expect(uuid).toHaveBeenCalledTimes(1)
    clearDraftId(12, storage)
    expect(storage.getItem(draftStorageKey(12))).toBeNull()
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
