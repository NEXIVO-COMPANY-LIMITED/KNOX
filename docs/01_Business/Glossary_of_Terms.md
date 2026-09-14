# MDM and Samsung Knox Glossary of Terms

This document provides definitions for technical terms and acronyms used throughout the documentation and the KNOX MDM system.

---

## General MDM Terms

| Term | Definition |
| :--- | :--- |
| **MDM** | Mobile Device Management. A software solution used to manage, secure, and enforce policies on mobile devices. |
| **DPC** | Device Policy Controller. The Android application (in our case, the 'Payo' app) that resides on the device and enforces policies. |
| **Device Owner** | A specialized type of device administrator that can only be set on a factory-reset device. It provides the highest level of control over the Android OS. |
| **Provisioning** | The process of setting up a new device with the required management software and configurations. |
| **Telemetry** | The automated process of collecting and transmitting data from the device to a remote server for monitoring. |

---

## Samsung Knox Specific Terms

| Term | Definition |
| :--- | :--- |
| **Knox SDK** | A set of APIs provided by Samsung that allow developers to access hardware-level security features not available in standard Android. |
| **KLM** | Knox License Management. The system used to activate and validate commercial or trial Knox licenses. |
| **ELM** | Enterprise License Management. A legacy term often used interchangeably with KLM for Knox license keys. |
| **KME** | Knox Mobile Enrollment. A zero-touch deployment service that automatically installs and configures the MDM app when a device is first turned on. |
| **Warranty Bit** | A hardware fuse in Samsung devices that "trips" (changes from 0x0 to 0x1) if the device is rooted or tampered with. This is irreversible. |
| **Knox Guard** | A specialized Samsung service for device financing that allows for hard-locking devices based on payment status. |

---
[Back to Main Documentation](../README.md)
