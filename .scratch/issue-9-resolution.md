## Resolution

Implemented mixed sidebar and viewport access policy in `guigu-oa-client` (commit 909a0c1).

### Delivered

- **`src/navigation/access-policy.js`** — single test seam: sidebar merge, config permission derivation, route classification, viewport policy, navigation guard
- **WorkLayout / ConfigLayout** — route-class shells with shared TopBar + AppSidebar
- **Mobile config hard block** — `/mobile-config-block` full-screen intercept on mobile viewport
- **Routes** — work five items built-in; config routes under `/system`, `/processSet`, `/processMgr` (placeholder pages for later tickets)
- **Unit tests** — 24 tests in `tests/unit/navigation/access-policy.spec.js`, all passing

### Acceptance criteria

- [x] 办事五项始终出现在侧栏，顺序正确
- [x] 无配置权限用户看不到配置组
- [x] 有配置权限用户看到办事 + 配置合并树
- [x] 办事页在移动壳可渲染；配置页在桌面壳可渲染
- [x] 手机访问配置路由 → 全屏拦截
- [x] 手机侧配置组底部折叠 + 「请在电脑浏览器使用」
- [x] 导航与访问策略模块单测全部通过

### Verify locally

```powershell
cd 前端代码/guigu-oa-client
npm run test:unit -- --testPathPattern=access-policy
npm run dev
```
