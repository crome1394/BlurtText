# AGENTS.md

Lean, modular Android messaging app built with Kotlin and Jetpack Compose.
Target: modern Android (min SDK 26+, target latest stable).
Goals, in priority order:
1. Lean at runtime: low idle CPU and memory, fast startup, no work for screens or features that are closed, efficient recomposition.
2. Easy to change: small files, one job each, clear boundaries, unidirectional data flow.
3. Correct and safe: lint-clean, no security regressions, graceful degradation, no leaks.
4. Clean, maintainable application that follows current Android best practices.

## Interaction style

Be a careful, collaborative coding partner rather than a silent executor.

- When something is ambiguous, under-specified, or you are not confident, ask clarifying questions before making changes. Prefer a short question over guessing.
- If a request looks wrong, incomplete, risky, or likely to cause problems (security, privacy, data loss, memory leaks, poor architecture, unnecessary complexity, violating existing patterns, breaking Compose rules, etc.), speak up clearly. Explain the concern and suggest a better approach.
- Prefer the safer, simpler, or more maintainable option when multiple paths exist. Flag trade-offs instead of just picking one.
- For non-trivial changes, briefly outline the plan and any risks before editing, then proceed once it makes sense (or after confirmation if the risk is high).
- After making changes, mention anything that still feels uncertain or that the user should review carefully.
- Stay helpful and direct. Do not lecture, but do not rubber-stamp ideas that look detrimental.
- If you notice existing code or patterns that seem problematic in the area you are working, mention them briefly and suggest a cleaner alternative.
- Automatically apply current Android best practices unless the project already has an explicit, documented exception.

## Code standards

- Kotlin idioms throughout. Prefer data classes, sealed classes/interfaces for UI state and events, and immutable data.
- Unidirectional data flow: state flows down, events flow up. No bidirectional coupling.
- UI is Jetpack Compose. Hoist state to the lowest common ancestor that needs it. Composables should be stateless where practical and take state + event lambdas.
- ViewModels expose immutable state (StateFlow / SharedFlow). Do not expose MutableStateFlow or mutable lists publicly. Use viewModelScope. Collect in Compose with collectAsStateWithLifecycle().
- Architecture: UI layer (Compose + ViewModel) ↔ Data layer (Repository). Repositories own data sources (network, Room, etc.). Business logic lives in ViewModels or small use-case classes — do not over-engineer with empty use-case layers.
- Dependency injection with Hilt (or Koin if the project already uses it). Prefer constructor injection.
- One focused file per type. Name files for what they do. Prefer packages that reflect feature boundaries (e.g. feature/chat, feature/conversation, core/data, core/ui).
- Colors, typography, spacing, and dimensions come from the theme. No hardcoded values in composables.
- No component reaches into another file’s private details. Data flows via parameters or shared state holders.
- Coroutines: use structured concurrency. Inject or abstract Dispatchers for testability. Prefer Flow over callbacks.
- Database: Room with type-safe queries. Migrations are explicit and tested.
- Networking: HTTPS only. Use Retrofit/OkHttp or Ktor with proper interceptors. Certificate pinning where appropriate for sensitive traffic.
- Security & privacy (messaging-specific):
  - Treat message content as sensitive. Prefer end-to-end encryption design where feasible; at minimum document threat model.
  - Store tokens and keys in Android Keystore. Never in SharedPreferences or plain files.
  - Minimize permissions. Request at runtime only when needed.
  - Do not log sensitive data (message bodies, keys, tokens).
  - Use Android’s backup exclusion rules for sensitive data.
- Strings and resources belong in res/. No magic strings in code for UI text.

## Quality gates and checks

Run these after meaningful changes and before considering a task done. Fix failures.

- `./gradlew ktlintCheck` (or ktlintFormat then check) — style must be clean.
- `./gradlew detekt` — static analysis must be clean (or only baseline exceptions).
- `./gradlew lintDebug` (or lint) — Android Lint clean.
- `./gradlew testDebugUnitTest` — unit tests pass.
- `./gradlew assembleDebug` — builds successfully.
- For UI or integration changes: relevant Compose UI tests or instrumented tests where practical.

If a tool is missing or not configured, suggest adding it and show the minimal setup.

## Suggested tools for a clean application

Recommend and help set up (when missing) tools that improve correctness, maintainability, or security:

- **ktlint** — Kotlin style and formatting.
- **Detekt** — static analysis, complexity, potential bugs.
- **Android Lint** — platform-specific issues.
- **LeakCanary** (debug) — memory leak detection.
- **Hilt** — dependency injection.
- **Room** — local persistence.
- **Retrofit + OkHttp** or **Ktor** — networking.
- **Navigation Compose** (or Navigation 3 if already adopted).
- **baseline-prof** / R8 optimizations for release.
- For security review: consider Android’s security lint rules, dependency scanning (e.g. OWASP Dependency-Check or similar), and explicit Keystore usage.
- Pre-commit or CI hooks that run the quality gates above.

Prefer official Jetpack libraries and current stable versions. Avoid adding heavy dependencies without a clear benefit.

## Testing

- Unit tests for ViewModels, repositories, and pure logic (JUnit + Turbine or similar for Flows).
- Compose UI tests for important screens and interactions.
- Keep tests fast. Prefer fakes over heavy mocks.
- New logic should have tests. Update existing tests when behavior changes.
- Run the relevant test task after changes that affect logic or UI state.

## Never

- Commit secrets, API keys, or keystores.
- Log message content, tokens, or keys.
- Hardcode Dispatchers in production code paths that should be testable.
- Use LiveData for new code (prefer StateFlow).
- Bypass the quality gates.
- Add unnecessary layers or abstractions “just in case.”
