# Task 9: Dead code cleanup — Effort: S

- Delete `identity/DeviceKeyManager.kt` if unused (grep first)
- Remove unused `signData` from DeviceIdentityManager OR keep with KDoc "reserved" — prefer delete if unused
- Keep refreshToken storage; KDoc "reserved — rotation not implemented"
- Do NOT commit
- Verify compile: `./gradlew :app:compileProdDebugKotlin`
