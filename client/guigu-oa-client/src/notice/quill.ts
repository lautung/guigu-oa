/**
 * 公告富文本工具栏（ADR 0055）：只开标题 h1–h4、粗体、斜体、下划线、删除线、
 * 有序/无序列表、引用、代码块、链接、图片、清除格式。字体颜色/字号/背景/对齐/
 * 表格/视频一律不开；`formats` 同时收口粘贴内容，避免粘贴把未开放格式带进正文。
 *
 * 图片按钮受服务端 `system:notice:add` 约束（上传端点复用 add 按钮点），只有 edit
 * 权限时由调用方传 `image: false` 隐藏按钮，而不是让上传撞 205。
 */

export type NoticeQuillToolbarItem = string | Record<string, unknown>
export type NoticeQuillToolbarRow = NoticeQuillToolbarItem[]

export interface NoticeQuillOptions {
  image?: boolean
}

const BASE_FORMATS = [
  'header',
  'bold',
  'italic',
  'underline',
  'strike',
  'list',
  'blockquote',
  'code-block',
  'link',
] as const

/** 每次返回新数组：Quill 会就地消费工具栏配置，共享常量会让隐藏图片的页面互相污染。 */
export function noticeQuillToolbar({
  image = true,
}: NoticeQuillOptions = {}): NoticeQuillToolbarRow[] {
  return [
    [{ header: [1, 2, 3, 4, false] }],
    ['bold', 'italic', 'underline', 'strike'],
    [{ list: 'ordered' }, { list: 'bullet' }],
    ['blockquote', 'code-block'],
    image ? ['link', 'image'] : ['link'],
    ['clean'],
  ]
}

export function noticeQuillFormats({ image = true }: NoticeQuillOptions = {}): string[] {
  return image ? [...BASE_FORMATS, 'image'] : [...BASE_FORMATS]
}

/** ADR 0055 的默认工具栏：图片按钮可用。 */
export const NOTICE_QUILL_TOOLBAR = noticeQuillToolbar()

/** 工具栏里真正开放的能力集合（去掉 header:false 这类显示项）。 */
export const NOTICE_QUILL_FORMATS = noticeQuillFormats()
