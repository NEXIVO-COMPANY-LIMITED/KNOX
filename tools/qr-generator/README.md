# Payo QR Provisioning Generator (React)

A local React + Vite tool to generate Android Enterprise Device Owner QR codes for testing and production deployment of the Payo app.

## Running Locally

1. Install dependencies:
   ```bash
   npm install
   ```
2. Start development server:
   ```bash
   npm run dev
   ```
3. Open http://localhost:3000 in your browser.

## Features
- Switch between **Production** and **Lab Debug** presets.
- Configure component names, APK download URLs, signature checksums, and admin extras bundle (`enrollment_token`, `server_url`, `org_id`, `lab_auto_release_minutes`).
- Optional Wi-Fi auto-configuration bundle.
- Live QR code rendering and JSON clipboard copy.
