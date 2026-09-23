# Maintenance and Troubleshooting Guide

## 1. System Liveness & Heartbeat Monitoring
The backend tracks device health via periodic `/api/v1/devices/{id}/heartbeat` pings driven by DPC WorkManager jobs.

| Heartbeat Status | Time Since Last Heartbeat | Action Triggered |
| :--- | :--- | :--- |
| **HEALTHY** | `< 1 hour` | Normal operation |
| **STALE** | `1 – 12 hours` | Monitored; pending commands accumulate |
| **OFFLINE** | `12 – 24 hours` | Flagged as offline in backend registry |
| **SILENT_ALERT** | `> 24 hours` | Automatic compliance alert triggered; flagged for IT investigation |

---

## 2. Command Queue Maintenance & Expiration
All remote commands in `commands` carry an explicit `expires_at` timestamp:
* **Pending Expiration:** If a device remains offline past `expires_at`, a backend cleanup job transitions the command status to `EXPIRED`.
* **Client Expiration Check:** When a re-connected device pulls pending commands via `GET /api/v1/devices/{id}/commands/pending`, the server omits expired items, preventing dangerous stale executions (e.g., executing a stale `LOCK` days later).

---

## 3. Offline Device Recovery Protocol
When a device reconnects after an extended offline period:
1. **Queue Flushing:** DPC flushes locally buffered diagnostic events and pending compliance reports stored in Room DB.
2. **Pending Command Pull:** DPC fetches unexpired pending commands and processes them sequentially.
3. **Policy Reconciliation:** DPC compares its local Room DB policy version against the backend resolved policy version, applying any deltas.

---

## 4. Factory Reset & Re-Enrollment Troubleshooting

### Scenario A: Device Was Reset via Hardware Recovery (KME Registered)
* **Observed State:** Device boots into OOBE Setup Wizard.
* **Expected Behavior:** Connecting to Wi-Fi causes Samsung KME to recognize the hardware IMEI, automatically downloading the DPC and reinstating Device Owner mode.
* **Troubleshooting:** If KME fails to trigger, verify that the device IMEI/Serial is active in the Knox Portal and assigned to a valid KME profile.

### Scenario B: Device Was Reset via Hardware Recovery (Not KME Registered)
* **Observed State:** Device boots into OOBE Setup Wizard without automatic DPC download.
* **Resolution:** Scan the enterprise fallback QR code during Setup Wizard to re-provision Device Owner mode.

---

## 5. Compliance Remediation
When a device reports non-compliant telemetry (e.g., OS patch out of date, lock screen disabled):
1. Backend writes a `compliance_results` row marked `NON_COMPLIANT`.
2. Backend enqueues an automated remediation command (`REFRESH_POLICY` or `LOCK`).
3. DPC executes the remediation command upon receipt.
