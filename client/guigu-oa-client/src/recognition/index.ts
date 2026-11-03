import type { LoginTokens, UserInfo } from '@/types/domain'

export type FailureKind = 'superseded' | 'invalid' | 'disabled' | 'forbidden' | 'retryable'

export class RecognitionFailure extends Error {
  constructor(
    readonly kind: FailureKind,
    message: string,
  ) {
    super(message)
    this.name = 'RecognitionFailure'
  }
}

export interface RecoveryState {
  status: 'idle' | 'loading' | 'retryable'
  target: string
}

export interface RequestContext {
  generation: number
  entryId: string
  silent: boolean
}

export interface RecognitionAdapter {
  credentials(): LoginTokens
  entryId(): string
  writeTokens(tokens: LoginTokens): void
  clearUser(): void
  hasUser(): boolean
  writeUser(user: UserInfo): void
  recovery(): RecoveryState
  showRecovery(state: RecoveryState): void
  loadUser(): Promise<UserInfo>
  currentPath(): string
  goTo(path: string): void
  goToLogin(target: string): void
  notify(message: string): void
}

function failure(error: unknown): RecognitionFailure {
  return error instanceof RecognitionFailure
    ? error
    : new RecognitionFailure('retryable', error instanceof Error ? error.message : '请求失败')
}

function nullableString(value: unknown): boolean {
  return value == null || typeof value === 'string'
}

function validStringArray(value: unknown): boolean {
  return value == null || (Array.isArray(value) && value.every((item) => typeof item === 'string'))
}

function validMenus(value: unknown): boolean {
  if (value == null) return true
  if (!Array.isArray(value)) return false
  return value.every((entry: unknown) => {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) return false
    const node = entry as Record<string, unknown>
    return (
      typeof node.path === 'string' &&
      nullableString(node.parentPath) &&
      typeof node.title === 'string' &&
      typeof node.type === 'number' &&
      (node.hidden == null || typeof node.hidden === 'boolean') &&
      (node.sort == null || typeof node.sort === 'number') &&
      nullableString(node.icon)
    )
  })
}

function validUser(user: UserInfo): boolean {
  return (
    !!user &&
    typeof user.name === 'string' &&
    nullableString(user.avatar) &&
    validStringArray(user.roles) &&
    validStringArray(user.permissions) &&
    validMenus(user.menus)
  )
}

/** Owns recognition ordering; credentials and user data stay in the adapter. */
export function createRecognition(adapter: RecognitionAdapter) {
  let generation = 0
  let flight: { context: RequestContext; promise: Promise<UserInfo> } | null = null

  function capture(silent = false): RequestContext {
    return { generation, entryId: adapter.entryId(), silent }
  }

  function isCurrent(context: RequestContext): boolean {
    return context.generation === generation && context.entryId === adapter.entryId()
  }

  function assertCurrent(context: RequestContext): void {
    if (!isCurrent(context)) throw new RecognitionFailure('superseded', '请求所属的进入结果已变化')
  }

  function target(): string {
    return adapter.recovery().target || adapter.currentPath()
  }

  function forget(): void {
    generation += 1
    flight = null
    adapter.clearUser()
    adapter.showRecovery({ status: 'idle', target: '' })
  }

  function validateTokens(tokens: LoginTokens): void {
    if (
      !tokens ||
      typeof tokens.token !== 'string' ||
      !tokens.token ||
      (tokens.refreshToken != null && typeof tokens.refreshToken !== 'string')
    ) {
      throw new RecognitionFailure('retryable', '暂时无法验证用户，请重试')
    }
  }

  function establish(tokens: LoginTokens): void {
    validateTokens(tokens)
    forget()
    adapter.writeTokens(tokens)
  }

  function commitRefresh(context: RequestContext, tokens: LoginTokens): void {
    assertCurrent(context)
    validateTokens(tokens)
    adapter.writeTokens(tokens)
  }

  function reject(
    context: RequestContext,
    error: unknown,
    notifyNormal = true,
  ): RecognitionFailure {
    if (!isCurrent(context)) return new RecognitionFailure('superseded', '请求所属的进入结果已变化')
    const reason = failure(error)
    if (context.silent || reason.kind === 'superseded') return reason
    if (reason.kind === 'invalid' || reason.kind === 'disabled') {
      const destination = target()
      forget()
      adapter.notify(reason.message)
      adapter.goToLogin(destination)
    } else if (notifyNormal) adapter.notify(reason.message)
    return reason
  }

  function recognize(
    destination = adapter.currentPath(),
    options: { quiet?: boolean; afterPending?: boolean } = {},
  ): Promise<UserInfo> {
    const context = capture()
    const hadUser = adapter.hasUser()
    if (!hadUser) adapter.showRecovery({ status: 'loading', target: destination })
    if (flight && isCurrent(flight.context)) {
      if (!options.afterPending) return flight.promise
      // A completed write needs a read started after that write, rather than a prior snapshot.
      return flight.promise
        .catch(() => {})
        .then(() => {
          assertCurrent(context)
          return recognize(destination, { quiet: options.quiet })
        })
    }
    const promise = Promise.resolve()
      .then(() => {
        assertCurrent(context)
        return adapter.loadUser()
      })
      .then((user) => {
        assertCurrent(context)
        if (!validUser(user)) throw new RecognitionFailure('retryable', '暂时无法获取用户信息')
        adapter.writeUser(user)
        adapter.showRecovery({ status: 'idle', target: '' })
        return user
      })
      .catch((error: unknown) => {
        if (!isCurrent(context))
          throw new RecognitionFailure('superseded', '请求所属的进入结果已变化')
        const reason = failure(error)
        if (reason.kind === 'invalid' || reason.kind === 'disabled') throw reject(context, reason)
        if (reason.kind !== 'superseded') {
          if (hadUser && !options.quiet) adapter.notify(reason.message)
          else if (!hadUser)
            adapter.showRecovery({ status: 'retryable', target: adapter.recovery().target })
        }
        throw reason
      })
      .finally(() => {
        if (flight?.promise === promise) flight = null
      })
    flight = { context, promise }
    return promise
  }

  async function resume(destination = adapter.recovery().target): Promise<void> {
    const context = capture()
    try {
      await recognize(destination)
      assertCurrent(context)
      adapter.goTo(destination)
    } catch {
      // recognize already owns recovery or confirmed invalidation.
    }
  }

  /** 退出后登录回哪里由应用传入（工作台），不把「刚才在哪」当落点。 */
  function exit(destination = target()): void {
    forget()
    adapter.goToLogin(destination)
  }

  return {
    recognize,
    resume,
    exit,
    establish,
    forget,
    capture,
    assertCurrent,
    reject,
    commitRefresh,
    credentials: adapter.credentials,
  }
}

export type Recognition = ReturnType<typeof createRecognition>
