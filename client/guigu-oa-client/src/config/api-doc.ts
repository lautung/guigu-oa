/**
 * 接口文档页的探测（#246，ADR 0061）：页面只回答「当前环境能不能用」。
 *
 * 文档端点是 springdoc 默认路径，只在 dev profile 开启；demo/prod 关闭时
 * 服务端直接 404（nginx 的 /v3 与 /swagger-ui 段只把 404 透传回来）。
 */

/** Swagger UI 内部按绝对路径取 spec，页面里同样只用绝对路径（同源代理见 vite/nginx）。 */
export const API_DOCS_PATH = '/v3/api-docs'
export const SWAGGER_UI_PATH = '/swagger-ui.html'

export const API_DOC_PROBE_TIMEOUT_MS = 3000

export type ApiDocAvailability = 'available' | 'unavailable'

type ProbeResponse = { ok: boolean }
type ProbeFetch = (input: string, init: { signal: AbortSignal }) => Promise<ProbeResponse>

/**
 * 200 才可用；404、网络错误、超时一律归并为 unavailable，不设第三种失败态（ADR 0061）。
 *
 * 超时用 race 兜住不理会 abort 的实现（jsdom 的测试替身、被代理占住的连接）：
 * 只 abort 不 race 时，一个永不 settle 的 fetch 会让探测永远停在加载态。
 */
export async function probeApiDoc(
  fetcher: ProbeFetch = (input, init) => fetch(input, init),
  timeoutMs = API_DOC_PROBE_TIMEOUT_MS,
): Promise<ApiDocAvailability> {
  const controller = new AbortController()
  let timer: ReturnType<typeof setTimeout> | undefined
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => {
      controller.abort()
      reject(new Error('api-doc probe timeout'))
    }, timeoutMs)
  })

  try {
    const response = await Promise.race([
      fetcher(API_DOCS_PATH, { signal: controller.signal }),
      timeout,
    ])
    return response.ok ? 'available' : 'unavailable'
  } catch {
    return 'unavailable'
  } finally {
    if (timer) clearTimeout(timer)
  }
}
