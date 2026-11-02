import { describe, expect, it } from 'vitest'

import {
  NOTICE_QUILL_FORMATS,
  NOTICE_QUILL_TOOLBAR,
  noticeQuillFormats,
  noticeQuillToolbar,
} from '@/notice/quill'

/** ADR 0055 允许的工具栏能力；header:false 只是“正文”选项，不是新格式。 */
const ALLOWED_FORMATS = [
  'header',
  'bold',
  'italic',
  'underline',
  'strike',
  'list',
  'blockquote',
  'code-block',
  'link',
  'image',
]

function toolbarFormats(toolbar) {
  const formats = new Set()
  for (const row of toolbar) {
    for (const item of row) {
      if (typeof item === 'string') {
        if (item !== 'clean') formats.add(item)
        continue
      }
      for (const key of Object.keys(item)) formats.add(key)
    }
  }
  return [...formats].sort()
}

describe('公告 Quill 2 工具栏（ADR 0055）', () => {
  it('opens exactly the allowed toolbar items', () => {
    expect(NOTICE_QUILL_TOOLBAR).toEqual([
      [{ header: [1, 2, 3, 4, false] }],
      ['bold', 'italic', 'underline', 'strike'],
      [{ list: 'ordered' }, { list: 'bullet' }],
      ['blockquote', 'code-block'],
      ['link', 'image'],
      ['clean'],
    ])
    expect(toolbarFormats(NOTICE_QUILL_TOOLBAR)).toEqual([...ALLOWED_FORMATS].sort())
    expect(NOTICE_QUILL_FORMATS).toEqual(ALLOWED_FORMATS)
  })

  it('never enables colour, font, size, background, align, table or video', () => {
    const serialized = JSON.stringify(NOTICE_QUILL_TOOLBAR)
    for (const forbidden of [
      'color',
      'background',
      'font',
      'size',
      'align',
      'table',
      'video',
      'script',
    ]) {
      expect(serialized).not.toContain(`"${forbidden}"`)
    }
  })

  it('hides the image button and image format when the user cannot upload', () => {
    expect(noticeQuillToolbar({ image: false })).toEqual([
      [{ header: [1, 2, 3, 4, false] }],
      ['bold', 'italic', 'underline', 'strike'],
      [{ list: 'ordered' }, { list: 'bullet' }],
      ['blockquote', 'code-block'],
      ['link'],
      ['clean'],
    ])
    expect(noticeQuillFormats({ image: false })).toEqual(ALLOWED_FORMATS.slice(0, -1))
  })

  it('returns fresh arrays so hiding images cannot mutate another editor', () => {
    const first = noticeQuillToolbar()
    first[0].push('mutated')
    expect(noticeQuillToolbar()).toEqual(NOTICE_QUILL_TOOLBAR)
    expect(noticeQuillToolbar()).not.toBe(noticeQuillToolbar())
  })
})
