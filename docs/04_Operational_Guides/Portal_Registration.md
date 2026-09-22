# Portal Registration and Configuration

## 1. Samsung Knox Admin Portal
The central hub for managing Samsung-specific features.

*   **URL:** [central.samsungknox.com](https://central.samsungknox.com)
*   **Key Tasks:**
    *   Registering the organization as a Samsung Knox Customer.
    *   Configuring Reseller IDs to automate IMEI syncing.
    *   Creating KME Profiles for zero-touch deployment.
    *   Managing commercial Knox License Keys (KLM).

## 2. Google Cloud Platform (GCP)
Used for Android Enterprise management and DPC hosting.

*   **URL:** [console.cloud.google.com](https://console.cloud.google.com)
*   **Key Tasks:**
    *   Enabling the **Android Management API**.
    *   Creating a **Service Account** with the `Android Management Administrator` role.
    *   Generating the **Service Account JSON Key** for backend integration.
    *   Registering the Enterprise ID.

## 3. Google Play Console
Used for publishing and updating the DPC application.

*   **URL:** [play.google.com/console](https://play.google.com/console)
*   **Key Tasks:**
    *   Uploading the DPC APK for private distribution to organizational devices.
    *   Managing app permissions and store listings.

## 4. Maintenance of Credentials
*   **License Renewal:** Ensure Knox license keys are tracked for expiration and renewed 30 days prior.
*   **Key Rotation:** Service account keys should be rotated annually to maintain a high security posture.
