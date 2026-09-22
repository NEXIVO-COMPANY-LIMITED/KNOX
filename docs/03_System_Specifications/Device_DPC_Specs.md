# Device DPC Architecture Specifications

## 1. Application Overview
The Device Policy Controller (DPC) is a Kotlin-based application designed for Samsung enterprise hardware. It serves as the local enforcement agent for all organizational security policies.

## 2. Technical Architecture
The DPC follows a clean architecture pattern with a clear separation between the UI, business logic, and hardware interaction layers.

### 2.1 Manager / Repository Pattern
*   **KnoxManager:** Orchestrates Samsung-specific SDK operations.
*   **PolicyRepository:** Manages local persistence of server-pushed policies using encrypted storage.
*   **TelemetryManager:** Collects hardware metrics and handles transmission retry logic.
*   **CommandProcessor:** An observer-based engine that listens for and executes server instructions.

## 3. System API Interaction (DPM)
The DPC utilizes the `DevicePolicyManager` (DPM) to interface with the Android OS core.

### 3.1 Essential DPM Integrations
*   `setUninstallBlocked()`: Ensures the DPC cannot be removed by the end-user.
*   `addUserRestriction()`: Implements blocks for Factory Reset, Safe Mode, and USB Debugging.
*   `lockNow()`: Triggers the immediate system-level screen lock.
*   `wipeData()`: Performs a secure factory reset and data erasure.

## 4. Samsung Knox SDK Extensions
Beyond standard DPM, the DPC leverages the Knox SDK for enhanced control:
*   **RestrictionPolicy:** Blocks hardware key combinations for Recovery Mode.
*   **ApplicationPolicy:** Performs silent installation of required corporate applications.
*   **FirewallPolicy:** Enforces domain-level traffic filtering to ensure all data stays within the corporate network.

## 5. Background Persistence
*   **WorkManager:** Used for periodic telemetry sync and policy heartbeats to ensure reliability even after reboots.
*   **Foreground Service:** Maintains a persistent connection for real-time command polling during active management windows.
