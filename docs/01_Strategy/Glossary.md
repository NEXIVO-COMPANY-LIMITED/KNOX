# Technical and Business Glossary

## 1. General MDM Terminology
*   **MDM (Mobile Device Management):** A technical framework used to secure and manage mobile devices within an organization.
*   **DPC (Device Policy Controller):** The specialized Android application that resides on the device to enforce organizational policies.
*   **Device Owner:** A specific Android mode that grants an application elevated privileges to manage the entire device. This must be set during initial setup on a factory-reset device.
*   **Provisioning:** The automated process of configuring a device with required applications and security policies.
*   **Telemetry:** The periodic transmission of device health and security data to the central management server.

## 2. Samsung Knox Specifics
*   **Knox SDK:** An advanced set of APIs provided by Samsung that interact directly with the device hardware and firmware.
*   **KLM (Knox License Management):** The service used to activate enterprise licenses on Samsung devices.
*   **KME (Knox Mobile Enrollment):** A cloud-based service that automatically deploys the DPC application as soon as a device is connected to the internet for the first time.
*   **Warranty Bit:** A hardware fuse (Knox bit) that permanently records if a device's security has been compromised via rooting or unauthorized firmware.
*   **Kiosk Mode:** A lockdown state where the device is restricted to one or more specific applications, disabling navigation and settings.

## 3. System Components
*   **OemCapabilityProvider:** An internal software interface that abstracts manufacturer-specific functionalities, isolating Samsung Knox logic from the core management engine.
*   **Compliance Engine:** A system module that monitors device telemetry against a set of security rules and triggers automated responses if violations occur.
*   **Hard Lock:** A state where the device screen is completely blocked, preventing user interaction until a specific administrative unlock command is received.
