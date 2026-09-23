# Technical and Business Glossary

## 1. General MDM & Android Terminology
* **MDM (Mobile Device Management):** Enterprise system for securing, configuring, and managing corporate mobile fleets.
* **DPC (Device Policy Controller):** The Kotlin application (`android-mdm`) running on managed Android devices that interacts with `DevicePolicyManager` to enforce security policies.
* **Device Owner (DO):** The highest privilege management state on Android, granted during setup on a factory-reset device. Allows complete control over restrictions, apps, and security settings.
* **Device Admin (DA):** Legacy management mode with restricted privileges, subject to user removal in Settings. Sideloaded APKs can only become DA, never DO.
* **FRP (Factory Reset Protection):** Android security feature linking devices to Google accounts to deter theft. Distinct from MDM management state.
* **Managed Google Play Enterprise:** Sanctioned EMM integration model for silent installation, updates, and uninstallation of private and public apps on DO devices.

## 2. OEM & Samsung Knox Terms
* **Knox SDK:** Samsung's enterprise API framework for low-level device control beyond baseline Android Enterprise.
* **KME (Knox Mobile Enrollment):** Samsung's zero-touch enrollment mechanism that auto-triggers DPC installation during initial setup (OOBE).
* **KLM (Knox License Manager):** Samsung service used to activate Knox Platform for Enterprise (KPE) license keys at runtime.
* **OemCapabilityProvider:** On-device Kotlin interface that abstracts vendor-specific Knox SDK calls away from core policy logic.
* **OemIntegration:** Backend service interface for managing vendor-specific portal APIs and licensing workflows.

## 3. Architecture & Communication Terms
* **FCM-Triggered Pull:** Communication model where Firebase Cloud Messaging sends a silent data wake-up ping, prompting the DPC to pull pending commands or policies over HTTPS REST.
* **Idempotency Key:** Unique header/token included in API calls to ensure retried requests execute exactly once without side effects.
* **Command Queue:** Durable backend database queue tracking remote commands through `PENDING`, `DELIVERED`, `ACKED`, `SUCCEEDED`, `FAILED`, and `EXPIRED` states.
* **Audit Log:** Append-only database table capturing immutable records of all state-changing system events.
