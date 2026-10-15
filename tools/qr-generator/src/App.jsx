import React, { useState, useMemo } from 'react';
import { QRCodeSVG } from 'qrcode.react';

export default function App() {
  const [preset, setPreset] = useState('prod');

  // Provisioning Fields
  const [componentName, setComponentName] = useState('com.nexivo.payo/.receiver.MyDeviceAdminReceiver');
  const [apkUrl, setApkUrl] = useState('https://your-server.com/downloads/app-prod-release.apk');
  const [signatureChecksum, setSignatureChecksum] = useState('');
  const [packageChecksum, setPackageChecksum] = useState('');
  const [leaveSystemAppsEnabled, setLeaveSystemAppsEnabled] = useState(true);

  // Wi-Fi Optional
  const [includeWifi, setIncludeWifi] = useState(false);
  const [wifiSsid, setWifiSsid] = useState('');
  const [wifiPassword, setWifiPassword] = useState('');
  const [wifiSecurity, setWifiSecurity] = useState('WPA');

  // Admin Extras Bundle
  const [enrollmentToken, setEnrollmentToken] = useState('ET-single-use-short-ttl');
  const [serverUrl, setServerUrl] = useState('https://your-server.com/');
  const [orgId, setOrgId] = useState('org-nexivo-01');
  const [labAutoReleaseMinutes, setLabAutoReleaseMinutes] = useState('30');

  const handlePresetChange = (p) => {
    setPreset(p);
    if (p === 'prod') {
      setComponentName('com.nexivo.payo/.receiver.MyDeviceAdminReceiver');
      setApkUrl('https://your-server.com/downloads/app-prod-release.apk');
      setEnrollmentToken('ET-prod-token-123');
      setServerUrl('https://api.payo.nexivo.com/');
    } else if (p === 'lab') {
      setComponentName('com.nexivo.payo.lab/com.nexivo.payo.receiver.MyDeviceAdminReceiver');
      setApkUrl('http://10.0.2.2:8000/app-lab-debug.apk');
      setEnrollmentToken('lab-enroll-token');
      setServerUrl('http://10.0.2.2:8000/');
    }
  };

  const provisioningData = useMemo(() => {
    const data = {
      "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": componentName,
      "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": apkUrl,
      "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": leaveSystemAppsEnabled,
      "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
        "enrollment_token": enrollmentToken,
        "server_url": serverUrl,
        "org_id": orgId,
      }
    };

    if (signatureChecksum.trim()) {
      data["android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"] = signatureChecksum.trim();
    }

    if (packageChecksum.trim()) {
      data["android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM"] = packageChecksum.trim();
    }

    if (includeWifi && wifiSsid.trim()) {
      data["android.app.extra.PROVISIONING_WIFI_SSID"] = wifiSsid.trim();
      data["android.app.extra.PROVISIONING_WIFI_PASSWORD"] = wifiPassword;
      data["android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"] = wifiSecurity;
    }

    if (preset === 'lab') {
      data["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"]["lab_auto_release_minutes"] = parseInt(labAutoReleaseMinutes, 10) || 30;
    }

    return data;
  }, [componentName, apkUrl, signatureChecksum, packageChecksum, leaveSystemAppsEnabled, includeWifi, wifiSsid, wifiPassword, wifiSecurity, enrollmentToken, serverUrl, orgId, labAutoReleaseMinutes, preset]);

  const jsonString = JSON.stringify(provisioningData, null, 2);

  const [copied, setCopied] = useState(false);
  const handleCopy = () => {
    navigator.clipboard.writeText(jsonString);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="max-w-7xl mx-auto p-6 bg-gray-950 text-gray-100 min-h-screen">
      <header className="mb-8 border-b border-gray-800 pb-4 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-3xl font-bold text-blue-400">Payo QR Provisioning Generator</h1>
          <p className="text-gray-400 text-sm mt-1">Generate Android Enterprise Device Owner QR codes for testing & production</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => handlePresetChange('prod')}
            className={`px-4 py-2 rounded-lg font-medium text-sm transition ${preset === 'prod' ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/30' : 'bg-gray-800 text-gray-300 hover:bg-gray-700'}`}
          >
            Production Preset
          </button>
          <button
            onClick={() => handlePresetChange('lab')}
            className={`px-4 py-2 rounded-lg font-medium text-sm transition ${preset === 'lab' ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/30' : 'bg-gray-800 text-gray-300 hover:bg-gray-700'}`}
          >
            Lab Debug Preset
          </button>
        </div>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Left Form Settings */}
        <div className="lg:col-span-7 space-y-6 bg-gray-900 p-6 rounded-2xl border border-gray-800 shadow-xl">
          <h2 className="text-xl font-semibold text-gray-200 border-b border-gray-800 pb-3">Provisioning Parameters</h2>

          <div>
            <label className="block text-sm font-medium text-gray-300 mb-1">Device Admin Component Name</label>
            <input
              type="text"
              value={componentName}
              onChange={(e) => setComponentName(e.target.value)}
              className="w-full bg-gray-950 border border-gray-800 rounded-xl px-3.5 py-2.5 text-sm text-gray-100 focus:ring-2 focus:ring-blue-500 focus:outline-none"
            />
            <p className="text-xs text-gray-500 mt-1">Format: package/.receiver.ClassName</p>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-300 mb-1">APK Download Location URL</label>
            <input
              type="text"
              value={apkUrl}
              onChange={(e) => setApkUrl(e.target.value)}
              className="w-full bg-gray-950 border border-gray-800 rounded-xl px-3.5 py-2.5 text-sm text-gray-100 focus:ring-2 focus:ring-blue-500 focus:outline-none"
            />
            <p className="text-xs text-gray-500 mt-1">Must be direct download URL (HTTPS recommended)</p>
          </div>

          {/* Checksums Section */}
          <div className="space-y-4 p-4 bg-gray-950/60 rounded-xl border border-gray-800">
            <h3 className="text-sm font-semibold text-blue-400">APK & Signature Checksums</h3>

            <div>
              <label className="block text-xs font-medium text-gray-300 mb-1">
                Signature Checksum <span className="text-gray-500 font-normal">(Recommended: URL-safe base64 SHA-256 of signing cert)</span>
              </label>
              <input
                type="text"
                value={signatureChecksum}
                onChange={(e) => setSignatureChecksum(e.target.value)}
                placeholder="e.g. jX9K... (survives APK rebuilds with same cert)"
                className="w-full bg-gray-900 border border-gray-800 rounded-lg px-3 py-2 text-xs text-gray-100 focus:ring-2 focus:ring-blue-500 focus:outline-none font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-300 mb-1">
                Package Checksum <span className="text-gray-500 font-normal">(Fallback: SHA-256 of APK bytes)</span>
              </label>
              <input
                type="text"
                value={packageChecksum}
                onChange={(e) => setPackageChecksum(e.target.value)}
                placeholder="e.g. 8f9b... (changes on every rebuild)"
                className="w-full bg-gray-900 border border-gray-800 rounded-lg px-3 py-2 text-xs text-gray-100 focus:ring-2 focus:ring-blue-500 focus:outline-none font-mono"
              />
            </div>

            <div className="text-xs text-gray-400 bg-gray-900/80 p-3 rounded-lg border border-gray-800 space-y-1">
              <p className="font-semibold text-gray-300">How to compute checksums:</p>
              <p><code className="text-blue-300">apksigner verify --print-certs app-release.apk</code> (for signature)</p>
              <p><code className="text-blue-300">openssl dgst -binary -sha256 app.apk | openssl base64 | tr '+/' '-_' | tr -d '=\n'</code></p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1">Enrollment Token</label>
              <input
                type="text"
                value={enrollmentToken}
                onChange={(e) => setEnrollmentToken(e.target.value)}
                className="w-full bg-gray-950 border border-gray-800 rounded-xl px-3.5 py-2.5 text-sm text-gray-100 focus:ring-2 focus:ring-blue-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1">Server URL</label>
              <input
                type="text"
                value={serverUrl}
                onChange={(e) => setServerUrl(e.target.value)}
                className="w-full bg-gray-950 border border-gray-800 rounded-xl px-3.5 py-2.5 text-sm text-gray-100 focus:ring-2 focus:ring-blue-500 focus:outline-none"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1">Org ID</label>
              <input
                type="text"
                value={orgId}
                onChange={(e) => setOrgId(e.target.value)}
                className="w-full bg-gray-950 border border-gray-800 rounded-xl px-3.5 py-2.5 text-sm text-gray-100 focus:ring-2 focus:ring-blue-500 focus:outline-none"
              />
            </div>
            {preset === 'lab' && (
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-1">Lab Dead-Man (Minutes)</label>
                <input
                  type="number"
                  value={labAutoReleaseMinutes}
                  onChange={(e) => setLabAutoReleaseMinutes(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-xl px-3.5 py-2.5 text-sm text-gray-100 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                />
              </div>
            )}
          </div>

          <div className="pt-2 border-t border-gray-800">
            <label className="flex items-center space-x-3 cursor-pointer">
              <input
                type="checkbox"
                checked={leaveSystemAppsEnabled}
                onChange={(e) => setLeaveSystemAppsEnabled(e.target.checked)}
                className="rounded bg-gray-950 border-gray-800 text-blue-600 focus:ring-blue-500 h-4 w-4"
              />
              <span className="text-sm font-medium text-gray-300">Leave All System Apps Enabled (Recommended)</span>
            </label>
          </div>

          <div className="pt-2 border-t border-gray-800">
            <label className="flex items-center space-x-3 cursor-pointer mb-3">
              <input
                type="checkbox"
                checked={includeWifi}
                onChange={(e) => setIncludeWifi(e.target.checked)}
                className="rounded bg-gray-950 border-gray-800 text-blue-600 focus:ring-blue-500 h-4 w-4"
              />
              <span className="text-sm font-medium text-gray-300">Include Wi-Fi Auto-Connect Extras</span>
            </label>

            {includeWifi && (
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3 bg-gray-950 p-4 rounded-xl border border-gray-800">
                <div>
                  <label className="block text-xs font-medium text-gray-400 mb-1">SSID</label>
                  <input
                    type="text"
                    value={wifiSsid}
                    onChange={(e) => setWifiSsid(e.target.value)}
                    placeholder="Network Name"
                    className="w-full bg-gray-900 border border-gray-800 rounded-lg px-3 py-2 text-xs text-gray-100"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-400 mb-1">Password</label>
                  <input
                    type="password"
                    value={wifiPassword}
                    onChange={(e) => setWifiPassword(e.target.value)}
                    placeholder="Password"
                    className="w-full bg-gray-900 border border-gray-800 rounded-lg px-3 py-2 text-xs text-gray-100"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-400 mb-1">Security Type</label>
                  <select
                    value={wifiSecurity}
                    onChange={(e) => setWifiSecurity(e.target.value)}
                    className="w-full bg-gray-900 border border-gray-800 rounded-lg px-3 py-2 text-xs text-gray-100"
                  >
                    <option value="WPA">WPA/WPA2</option>
                    <option value="WEP">WEP</option>
                    <option value="NONE">None</option>
                  </select>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Right QR Code & JSON Preview */}
        <div className="lg:col-span-5 space-y-6">
          <div className="bg-gray-900 p-6 rounded-2xl border border-gray-800 shadow-xl flex flex-col items-center">
            <h2 className="text-xl font-semibold text-gray-200 border-b border-gray-800 pb-3 w-full text-center mb-4">Provisioning QR Code</h2>

            <div className="bg-white p-6 rounded-2xl shadow-lg my-2 flex items-center justify-center">
              <QRCodeSVG value={jsonString} size={260} level="M" />
            </div>

            <p className="text-xs text-gray-400 text-center mt-3">
              Factory reset test device → Tap welcome screen 6 times → Scan this QR code.
            </p>
          </div>

          <div className="bg-gray-900 p-6 rounded-2xl border border-gray-800 shadow-xl">
            <div className="flex justify-between items-center mb-3">
              <h2 className="text-lg font-semibold text-gray-200">JSON Payload</h2>
              <button
                onClick={handleCopy}
                className="bg-blue-600 hover:bg-blue-500 text-white text-xs font-medium px-3 py-1.5 rounded-lg transition shadow-md shadow-blue-600/30"
              >
                {copied ? 'Copied!' : 'Copy JSON'}
              </button>
            </div>
            <pre className="bg-gray-950 text-green-400 p-4 rounded-xl text-xs font-mono overflow-x-auto max-h-60 border border-gray-800">
              {jsonString}
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
}
