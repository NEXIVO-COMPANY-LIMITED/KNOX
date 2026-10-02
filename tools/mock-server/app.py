#!/usr/bin/env python3
"""
LAB ONLY mock Payo backend. NEVER deploy. Keys are fake. Not shipped in any APK.

Command signing private key is generated at process start and never written to disk.
"""

from __future__ import annotations

import base64
import os
import secrets
import threading
import time
from flask import Flask, jsonify, request
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

app = Flask(__name__)

VALID_TOKEN = os.environ.get("PAYO_LAB_ENROLL_TOKEN", "lab-enroll-token")
_used_tokens: set[str] = set()
_lock = threading.Lock()
_devices: dict[str, dict] = {}
_commands: dict[str, list] = {}
_last_seq: dict[str, int] = {}

# Offline OTP HMAC key (32 bytes) — lab fixed (public test material, not a command signing key)
LAB_OFFLINE_KEY = base64.b64encode(bytes(range(32))).decode("ascii")

# ECDSA P-256 command signing key — generated once per process; never persisted.
_cmd_private = ec.generate_private_key(ec.SECP256R1())
_cmd_public = _cmd_private.public_key()
_cmd_public_spki_b64 = base64.b64encode(
    _cmd_public.public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
).decode("ascii")
CMD_KID = os.environ.get("PAYO_LAB_CMD_KID", "lab-cmd-1")


def _bearer_ok() -> bool:
    auth = request.headers.get("Authorization", "")
    return auth.startswith("Bearer ") and len(auth) > 8


def _canonical(device_id, type_, sequence, expires_at, nonce, kid, id_, payload) -> bytes:
    return "\n".join(
        [
            device_id,
            type_,
            str(sequence),
            str(expires_at),
            nonce,
            kid,
            id_,
            payload or "",
        ]
    ).encode("utf-8")


def _sign(canonical: bytes) -> str:
    sig = _cmd_private.sign(canonical, ec.ECDSA(hashes.SHA256()))
    return base64.b64encode(sig).decode("ascii")


def _make_command(
    device_id: str,
    type_: str = "LOCK",
    sequence: int | None = None,
    expires_at: int | None = None,
    payload: str | None = None,
    corrupt_sig: bool = False,
    wrong_device: bool = False,
    kid: str | None = None,
) -> dict:
    now = int(time.time() * 1000)
    seq = sequence if sequence is not None else _last_seq.get(device_id, 0) + 1
    exp = expires_at if expires_at is not None else now + 600_000
    did = "wrong-device" if wrong_device else device_id
    cmd_id = "cmd-" + secrets.token_hex(4)
    nonce = secrets.token_hex(8)
    key_id = kid or CMD_KID
    canonical = _canonical(did, type_, seq, exp, nonce, key_id, cmd_id, payload or "")
    signature = _sign(canonical)
    if corrupt_sig:
        signature = base64.b64encode(b"\x00" * 64).decode("ascii")
    return {
        "id": cmd_id,
        "type": type_,
        "payload": payload,
        "issued_at": now,
        "timestamp": now,
        "expires_at": exp,
        "sequence": seq,
        "device_id": did,
        "nonce": nonce,
        "kid": key_id,
        "signature": signature,
    }


@app.post("/api/v1/enrollment")
def enrollment():
    body = request.get_json(silent=True) or {}
    token = (body.get("enrollmentToken") or "").strip()
    print(f"[enroll] tokenPresent={bool(token)}")  # never log token value
    if not token:
        return jsonify(success=False, message="missing token"), 400
    with _lock:
        if token != VALID_TOKEN or token in _used_tokens:
            return jsonify(success=False, message="invalid or used token"), 401
        _used_tokens.add(token)
        device_id = "lab-" + secrets.token_hex(4)
        access = secrets.token_urlsafe(24)
        server_count = int(body.get("offlineUnlockCount") or 0)
        _devices[device_id] = {
            "access": access,
            "locked": False,
            "public_key": body.get("publicKey"),
            "offline_unlock_count": server_count,
        }
        _commands[device_id] = []
        # Do not reset device sequence watermark here — app must keep lastSequence.
        if device_id not in _last_seq:
            _last_seq[device_id] = 0
    # commandPublicKey is informational for lab operators (put in QR); device must not trust HTTP.
    return jsonify(
        success=True,
        deviceId=device_id,
        accessToken=access,
        offlineUnlockKey=LAB_OFFLINE_KEY,
        lockStatus="ACTIVE",
        commandPublicKey=_cmd_public_spki_b64,
        commandPublicKeyKid=CMD_KID,
        offlineUnlockCount=server_count,
    )


@app.post("/api/v1/devices/reauth")
def reauth():
    body = request.get_json(silent=True) or {}
    device_id = (body.get("deviceId") or "").strip()
    challenge = body.get("challenge") or ""
    sig_b64 = body.get("signatureBase64") or ""
    if not device_id or not challenge or not sig_b64:
        return jsonify(success=False, message="missing fields"), 400
    if device_id not in _devices:
        return jsonify(success=False, message="unknown device"), 404
    access = secrets.token_urlsafe(24)
    _devices[device_id]["access"] = access
    print(f"[reauth] device={device_id} ok (lab accepts signature presence)")
    return jsonify(success=True, accessToken=access)


@app.post("/api/v1/devices/<device_id>/heartbeat")
def heartbeat(device_id: str):
    if not _bearer_ok():
        return jsonify(error="unauthorized"), 401
    locked = _devices.get(device_id, {}).get("locked", False)
    now = int(time.time() * 1000)
    return jsonify(
        success=True,
        message="ok",
        content={"is_locked": locked, "loan_complete": False},
        actions={"hardlock": False, "reminder": False},
        next_payment=None,
        server_time=str(now),
    )


@app.get("/api/v1/devices/<device_id>/commands")
def commands(device_id: str):
    if not _bearer_ok():
        return jsonify(error="unauthorized"), 401
    scenario = (request.args.get("scenario") or "valid").strip()
    now = int(time.time() * 1000)
    last = _last_seq.get(device_id, 0)

    if scenario == "expired":
        cmd = _make_command(device_id, expires_at=now - 1_000)
    elif scenario == "stale_sequence":
        cmd = _make_command(device_id, sequence=last)
    elif scenario == "wrong_device":
        cmd = _make_command(device_id, wrong_device=True)
    elif scenario == "bad_signature":
        cmd = _make_command(device_id, corrupt_sig=True)
    elif scenario == "release_ok":
        payload = (
            f'{{"confirmDeviceId":"{device_id}","releaseExpiresAtEpochMs":{now + 300_000}}}'
        )
        cmd = _make_command(device_id, type_="RELEASED", payload=payload)
    elif scenario == "release_bad_claim":
        cmd = _make_command(device_id, type_="OFFBOARD", payload="{}")
    elif scenario == "wipe":
        cmd = _make_command(device_id, type_="WIPE")
    elif scenario == "reboot":
        cmd = _make_command(device_id, type_="REBOOT")
    else:
        cmd = _make_command(device_id, type_="LOCK")

    return jsonify([cmd])


@app.post("/api/v1/devices/<device_id>/commands/<command_id>/ack")
def ack(device_id: str, command_id: str):
    if not _bearer_ok():
        return jsonify(error="unauthorized"), 401
    _last_seq[device_id] = _last_seq.get(device_id, 0) + 1
    return jsonify(success=True, message="acked")


@app.post("/api/v1/devices/<device_id>/offline-unlock-report")
def offline_unlock_report(device_id: str):
    if not _bearer_ok():
        return jsonify(error="unauthorized"), 401
    print(f"[offline-unlock-report] device={device_id}")
    return jsonify(success=True, message="accepted")


@app.post("/api/v1/devices/report")
@app.post("/api/v1/devices/enroll")
@app.post("/api/v1/devices/enroll-sync")
def stub_ok():
    if not _bearer_ok():
        return jsonify(error="unauthorized"), 401
    return jsonify(success=True, message="ok")


@app.get("/api/v1/devices/<imei>/status")
def status(imei: str):
    if not _bearer_ok():
        return jsonify(error="unauthorized"), 401
    return jsonify(success=True, message="ok")


@app.get("/lab/command-public-key")
def lab_command_public_key():
    """Operator helper: copy SPKI + kid into QR admin extras. Not a trust channel for the app."""
    return jsonify(kid=CMD_KID, commandPublicKey=_cmd_public_spki_b64)


# Lab support reactivation (state-lost PARTIAL_LOCK). Single-use window codes.
_used_reactivation_windows: set[str] = set()
import hmac as _hmac
import hashlib as _hashlib


def _reactivation_code(device_hw: str, window: int) -> str:
    key = bytes(range(32))
    msg = f"{device_hw}|{window}|reactivate".encode()
    digest = _hmac.new(key, msg, _hashlib.sha256).digest()
    return digest[:4].hex()


@app.post("/api/v1/devices/reactivate")
def reactivate():
    """
    Support reactivation: body { deviceHardwareId, code }.
    Lab verifies HMAC window (±1) single-use. Never returns secrets.
    """
    body = request.get_json(silent=True) or {}
    hw = (body.get("deviceHardwareId") or "").strip()
    code = (body.get("code") or "").strip().lower()
    if not hw or not code:
        return jsonify(success=False, message="missing fields"), 400
    now = int(time.time() * 1000)
    window = now // (15 * 60 * 1000)
    for w in (window - 1, window, window + 1):
        token = f"{hw}|{w}"
        if token in _used_reactivation_windows:
            continue
        if _reactivation_code(hw, w) == code:
            _used_reactivation_windows.add(token)
            print(f"[reactivate] device_hw={hw} ok")
            return jsonify(success=True, message="reactivated", lockStatus="ACTIVE")
    return jsonify(success=False, message="invalid or expired"), 401


@app.get("/lab/reactivation-code")
def lab_reactivation_code():
    """Operator helper — mint a lab code for a hardware id (IMEI/serial)."""
    hw = (request.args.get("deviceHardwareId") or "").strip()
    if not hw:
        return jsonify(error="deviceHardwareId required"), 400
    now = int(time.time() * 1000)
    window = now // (15 * 60 * 1000)
    return jsonify(deviceHardwareId=hw, code=_reactivation_code(hw, window), ttlMinutes=15)


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8080"))
    print("WARNING: LAB ONLY — never deploy; keys are fake; not in any APK.")
    print(f"Payo lab mock on 0.0.0.0:{port} enroll_token={VALID_TOKEN!r}")
    print(f"Put into QR admin extras:")
    print(f"  command_pubkey_kid={CMD_KID}")
    print(f"  command_pubkey_spki={_cmd_public_spki_b64}")
    app.run(host="0.0.0.0", port=port, debug=False)
