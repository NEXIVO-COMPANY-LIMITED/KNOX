# Knox Partner Program & SDK Access Guide

This guide clarifies the difference between Samsung Knox account types, explains why your Partner application might be "Pending Approval," and provides actionable steps to acquire the `knoxsdk.aar` file and KME APIs needed for development.

---

## 1. Understanding Samsung Knox Account Types

Samsung divides its Knox ecosystem into different portals based on the user's role. It is critical to understand which portal you are using and what it provides.

### A. Business "Admin" Account (Knox Admin Portal)
*   **Who it is for:** IT Managers and businesses using Knox to manage devices.
*   **URL:** `central.samsungknox.com`
*   **What you can do:** Buy and manage Knox licenses (e.g., Knox Configure, Knox Guard), enroll devices via Knox Mobile Enrollment (KME), and monitor device fleets.
*   **What you CANNOT do:** You **cannot** download the developer SDKs (like `knoxsdk.aar`) or access developer API credentials.

### B. "Partner" Account (Knox Partner Portal / Developer Portal)
*   **Who it is for:** Developers, software engineers, and companies building custom MDM/DPC applications (like the Payo App).
*   **URL:** `partner.samsungknox.com` or `developer.samsungknox.com`
*   **What you can do:** Download the **Knox SDK (`knoxsdk.aar`)**, access technical documentation, generate API keys for backend integration, and get direct technical support from Samsung engineers.
*   **Requirement:** Requires explicit approval from Samsung to join the "Knox Partner Program."

---

## 2. Why is My Partner Application "Pending Approval"?

When you apply to become a Knox Partner (even if you already have a Business Admin account), Samsung manually reviews your application.

*   **Status:** *"We're currently reviewing your application."*
*   **Reason:** Samsung restricts access to its powerful Knox security libraries. They verify that the applicant is a legitimate business developing an enterprise solution. This process can take anywhere from a few hours to several weeks.
*   **Company Registration:** If you see "Company Registration: Approved" but "Knox Partner Portal Access: Pending," it means your business entity (e.g., NEXIVO) is recognized, but the developer engineering access is still in the queue.

---

## 3. How to Proceed While "Pending" (The Shortcut)

You do not need to pause development while waiting for Samsung's approval. If you are working with an **Authorized Samsung Knox Reseller** (e.g., your African reseller providing IMEI API access), you can leverage their Partner status.

### Action Plan:
1.  **Request the SDK (`knoxsdk.aar`):** Email your Knox Reseller and state: *"My Knox Partner account is pending approval. Could you please send me the latest `knoxsdk.aar` file so my engineering team can compile our custom DPC app?"* Resellers have the authority and access to provide these development tools to their clients.
2.  **Request KME API Documentation:** Ask the Reseller for their API endpoints and credentials. Instead of your backend communicating directly with Samsung (which requires Partner API keys), your backend (`sponsa_backend`) will communicate with the Reseller's API, and the Reseller will push the IMEI to Samsung KME on your behalf.

---

## 4. How the App is Installed (Without KME vs. With KME)

A common question is how the app gets onto the phone if you don't have KME access yet. The installation method changes based on the phase of your project.

### Phase 1: Development & Testing (Current Phase)
During development, you **do not need** Samsung Cloud or KME. You will install the app manually using a computer.
1.  Factory reset a test Samsung device.
2.  Enable USB Debugging in Developer Options.
3.  Install the app via ADB: `adb install app-debug.apk`
4.  Set the app as the Device Owner via ADB:
    `adb shell dpm set-device-owner com.example.payo/.receiver.MyDeviceAdminReceiver`
5.  The app will launch, verify its Knox License locally, and communicate with your backend.

### Phase 2: Production & Mass Deployment (Future Phase)
Once testing is complete and you are selling devices to customers, you will use **Samsung KME** for "Zero-Touch" installation.
1.  **Host the APK:** You host the compiled `payo-release.apk` on your own secure server (e.g., AWS or your backend server). It does not need to be hosted by Samsung.
2.  **Create KME Profile:** Using your Admin Portal (or via the Reseller), you create a KME profile containing the URL to your hosted APK.
3.  **Enroll Devices:** When a shop sells a phone, your backend sends the IMEI to the Reseller's API, which registers it with Samsung KME.
4.  **Zero-Touch Boot:** The customer turns on the phone and connects to Wi-Fi. Samsung KME recognizes the IMEI, forces the phone to download your APK from your server, installs it, and sets it as the Device Owner automatically without any USB cables.

---
*Nexivo Enterprise Standards - 2026*
