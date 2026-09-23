# Portal Registration & Account Setup Sequence (Phase 0)

## 1. Overview
This guide provides the concrete account setup sequence required prior to initiating Phase 1 development. All portal registrations should be completed early due to manual review lead times (particularly for Samsung Knox Partner status).

---

## 2. Samsung Knox Ecosystem Setup

### 2.1 Step 1: Knox Partner Program Signup
* **Portal:** [Samsung Knox Partner Program Signup](https://docs.samsungknox.com/partner/knox-partner-program/get-started/sign-up-for-a-partner-account/)
* **Prerequisites:** Organization D-U-N-S Number, official business email.
* **Role Selection:** Select **MDM / EMM / UEM Developer**.
* **Lead Time:** 2–5 business days for manual Samsung approval.

### 2.2 Step 2: Knox Developer Portal Access
* **Portal:** [Samsung Knox Developer Portal](https://docs.samsungknox.com/dev/fundamentals/sign-in-to-knox-developer-account/)
* **Tasks:**
  * Download Samsung Knox SDK binaries for integration in `android-mdm/oem/knox/`.
  * Generate Knox Development License Keys (KLM) for testing.

### 2.3 Step 3: Knox API Portal & Cloud Service Authentication Setup
Once access is granted to the **Samsung Knox API Portal**, follow this protocol to enable backend API communication with Samsung Knox Cloud Services (KME, Knox Guard, License Management):

1. **Select Regional API Portal:**
   * **US Region:** `https://us-api-portal.samsungknox.com`
   * **EU Region:** `https://eu-api-portal.samsungknox.com`
2. **Download Key Pair (`keys.json`):**
   * Download the `keys.json` file containing the `public_key` and `private_key` pair.
   * *Security Rule:* Store `keys.json` securely on the backend server (Vault / Environment secrets); never expose or commit private key data.
3. **Generate Client Identifier (`clientId`):**
   * Generate a unique Client Identifier in the Knox API Portal.
4. **Knox Cloud API Access Token Token Generation Protocol:**
   * **Sign Client ID:** Sign the `clientId` using the private key from `keys.json` to generate `signedClientId` (Client Identifier JWT).
   * **Fetch Access Token:** Call Knox AMS Token API:
     * Endpoint: `POST https://us-kcs-api.samsungknox.com/ams/v1/users/accesstoken` (or EU endpoint)
     * Request Body:
       ```json
       {
         "base64EncodedStringPublicKey": "<public_key_from_keys.json>",
         "clientIdentifierJwt": "<signedClientId>",
         "validityForAccessTokenInMinutes": 30
       }
       ```
   * **Sign Access Token:** Sign the returned `accessToken` with the private key to produce `signedAccessToken`.
   * **API Authorization Header:** Pass `x-knox-apitoken: <signedAccessToken>` in all Knox Cloud API requests. Handle token expiration (`403225` error code) by refreshing tokens automatically prior to the 30-minute validity window.

### 2.4 Step 4: Knox Deployment Program (KME)
* **Portal:** [Samsung Knox Admin Portal](https://central.samsungknox.com)
* **Tasks:**
  * Access Knox Mobile Enrollment (KME) service.
  * Link reseller IDs or configure direct serial/IMEI uploading.
  * Create KME Provisioning Profiles with DPC APK download URL and admin extras.

---

## 3. Google & Android Enterprise Ecosystem Setup

### 3.1 Step 1: EMM Binding Creation
* **Documentation:** [Android Enterprise EMM Overview](https://developers.google.com/android/work/overview)
* **Portal:** [Managed Google Play Enterprise Creation](https://developers.google.com/android/work/play/emm-api/create-enterprise)
* **Tasks:** Bind the backend to a Managed Google Play Enterprise for silent app distribution.

### 3.2 Step 2: Google Cloud Platform & FCM Project
* **Portal:** [Google Cloud Console](https://console.cloud.google.com)
* **Tasks:**
  * Create GCP Project for MDM Infrastructure.
  * Enable Firebase Cloud Messaging (FCM) API.
  * Download FCM Service Account JSON credentials for `mdm-backend/integrations/fcm/`.

### 3.3 Step 3: Google Play Console (Private App Publishing)
* **Portal:** [Google Play Console](https://play.google.com/console)
* **Tasks:** Upload internal DPC builds for private distribution to enterprise devices.
