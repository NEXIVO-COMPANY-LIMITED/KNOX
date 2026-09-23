# KNOX Enterprise MDM Documentation

Welcome to the official technical documentation for the **KNOX Mobile Device Management (MDM)** platform.

## Architecture Overview
The platform is a **single-tenant, single-organization core MDM system** consisting of:
* **Kotlin Device Policy Controller (`android-mdm`):** Android application operating in Device Owner mode with an OEM abstraction layer (`OemCapabilityProvider`).
* **Kotlin / Spring Boot Backend (`mdm-backend`):** Stateless REST API server with PostgreSQL storage and Redis caching.
* **No Admin UI / Web Console:** Built strictly as a backend + DPC solution exposing clean REST APIs for integration.

---

## Documentation Index

### [01 Strategy](./01_Strategy/README.md)
Foundational goals, scope boundaries, privacy standards, and glossary.
* [Business Requirements & Product Scope](./01_Strategy/Business_Requirements.md)
* [Privacy and Compliance Standards](./01_Strategy/Privacy_and_Compliance.md)
* [Technical and Business Glossary](./01_Strategy/Glossary.md)

### [02 Architecture](./02_Architecture/README.md)
System architecture, OEM abstraction, operational flows, and security model.
* [Master Architecture Specification](./02_Architecture/Master_Architecture.md)
* [System Operational Flows](./02_Architecture/Operational_Flows.md)
* [Security Model and Hardening](./02_Architecture/Security_Model.md)

### [03 System Specifications](./03_System_Specifications/README.md)
Detailed technical specifications for developers and architects.
* [Backend Developer Handoff Guide](./03_System_Specifications/Backend_Developer_Handoff_Guide.md)
* [Backend REST API Reference](./03_System_Specifications/Backend_API_Reference.md)
* [Database Schema & Data Architecture (PostgreSQL)](./03_System_Specifications/Database_Schema.md)
* [Device DPC Specifications (`android-mdm`)](./03_System_Specifications/Device_DPC_Specs.md)

### [04 Operational Guides](./04_Operational_Guides/README.md)
Guides for provisioning, account setup, and maintenance.
* [Provisioning & Device Owner Enrollment Guide](./04_Operational_Guides/Provisioning_Guide.md)
* [Portal Registration & Account Setup Sequence](./04_Operational_Guides/Portal_Registration.md)
* [Maintenance and Troubleshooting Guide](./04_Operational_Guides/Maintenance_Troubleshooting.md)

### [05 Implementation Roadmap](./05_Implementation_Roadmap/README.md)
Master technical implementation plan and development phases.
* [Master Technical Implementation Plan](./05_Implementation_Roadmap/Full_Implementation_Plan.md)

---
*Nexivo Enterprise Standards - 2026*
