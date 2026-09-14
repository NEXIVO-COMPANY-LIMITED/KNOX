# Backend Server Setup Guide

This guide explains how to deploy and configure the Knox MDM Backend (Node.js/Express) on a local machine or cloud server.

---

## 1. Prerequisites
- **Node.js** (v18 or higher)
- **NPM** (Node Package Manager)
- **SQLite3** (Included in project dependencies)

---

## 2. Installation Steps

### A. Clone and Enter Directory
```bash
cd D:/NEXIVO/KNOX/server
```

### B. Install Dependencies
```bash
npm install
```

### C. Configure API Key
Open `index.js` and update the `API_KEY` constant with a secure value. This must match the `SERVER_API_KEY` in the Android `build.gradle.kts`.

```javascript
const API_KEY = "your-secure-api-key-here";
```

---

## 3. Running the Server

### Local Development
```bash
node index.js
```
The server will start on `http://localhost:3000`.

### Production Deployment
For production, it is recommended to use **PM2** to keep the server running:
```bash
npm install -g pm2
pm2 start index.js --name knox-mdm-server
```

---

## 4. Verification
Once the server is running, the `mdm.db` file will be created automatically in the server folder. You can use a tool like **SQLite Browser** to inspect the tables.

### Testing Connection
```bash
curl -H "Authorization: Bearer <YOUR_API_KEY>" http://localhost:3000/api/v1/apps
```
Expected: `[]` (Empty list if no apps are registered yet).

---
*Nexivo Support — 2026*
