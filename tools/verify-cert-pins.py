#!/usr/bin/env python3
"""
Compute OkHttp CertificatePinner SPKI SHA-256 pins and compare to configured release pins.

Usage:
  python tools/verify-cert-pins.py --host api.example.com
  python tools/verify-cert-pins.py --pem path/to/leaf-or-intermediate.pem
  python tools/verify-cert-pins.py --host api.example.com --primary sha256/... --backup sha256/...

Configured pins (checked in order):
  1) --primary / --backup CLI flags
  2) env CERT_PIN_SHA256 (or CERT_PIN_SHA256_PRIMARY) + CERT_PIN_SHA256_BACKUP
  3) local.properties keys of the same names

Requires CERT_PIN_SHA256_BACKUP (at least one backup pin). Exit 0 on match, 1 on mismatch/error.

Pin rotation: see docs/04_Operational_Guides/Release_Build_Config.md § Certificate pin rotation.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import os
import ssl
import sys
from pathlib import Path
from typing import Optional, Tuple


def spki_sha256_pin_from_der_cert(der: bytes) -> str:
    from cryptography import x509
    from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

    cert = x509.load_der_x509_certificate(der)
    spki = cert.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo)
    digest = hashlib.sha256(spki).digest()
    return "sha256/" + base64.b64encode(digest).decode("ascii")


def spki_sha256_pin_from_pem(pem_text: str) -> str:
    from cryptography import x509
    from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

    cert = x509.load_pem_x509_certificate(pem_text.encode("utf-8"))
    spki = cert.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo)
    digest = hashlib.sha256(spki).digest()
    return "sha256/" + base64.b64encode(digest).decode("ascii")


def fetch_peer_cert_der(host: str, port: int = 443) -> bytes:
    ctx = ssl.create_default_context()
    # We need the peer cert even if local trust store differs; still verify hostname shape.
    with ssl.create_connection((host, port), timeout=30) as sock:
        with ctx.wrap_socket(sock, server_hostname=host) as ssock:
            der = ssock.getpeercert(binary_form=True)
            if not der:
                raise RuntimeError(f"No peer certificate from {host}:{port}")
            return der


def load_local_properties(root: Path) -> dict:
    path = root / "local.properties"
    out: dict = {}
    if not path.is_file():
        return out
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip()
    return out


def resolve_configured_pins(
    primary: Optional[str],
    backup: Optional[str],
    root: Path,
) -> Tuple[str, str]:
    props = load_local_properties(root)
    p = (
        primary
        or os.environ.get("CERT_PIN_SHA256_PRIMARY")
        or os.environ.get("CERT_PIN_SHA256")
        or props.get("CERT_PIN_SHA256_PRIMARY")
        or props.get("CERT_PIN_SHA256")
    )
    b = (
        backup
        or os.environ.get("CERT_PIN_SHA256_BACKUP")
        or props.get("CERT_PIN_SHA256_BACKUP")
    )
    if not p or not p.strip():
        raise SystemExit("FAIL: primary pin missing (CERT_PIN_SHA256 / CERT_PIN_SHA256_PRIMARY)")
    if not b or not b.strip():
        raise SystemExit(
            "FAIL: backup pin required (CERT_PIN_SHA256_BACKUP). "
            "Rotation needs at least one backup pin."
        )
    return p.strip(), b.strip()


def looks_like_pin(value: str) -> bool:
    if not value.startswith("sha256/"):
        return False
    b64 = value[len("sha256/") :]
    try:
        raw = base64.b64decode(b64, validate=True)
    except Exception:
        return False
    return len(raw) == 32


def printable_byte_ratio(pin: str) -> float:
    """Secondary heuristic only — not used for pass/fail against live SPKI."""
    b64 = pin[len("sha256/") :]
    raw = base64.b64decode(b64)
    printable = sum(1 for x in raw if 32 <= x <= 126)
    return printable / max(len(raw), 1)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    src = parser.add_mutually_exclusive_group(required=True)
    src.add_argument("--host", help="Live TLS hostname (port 443)")
    src.add_argument("--pem", help="Path to PEM certificate (leaf or pinned intermediate)")
    parser.add_argument("--port", type=int, default=443)
    parser.add_argument("--primary", help="Expected primary pin sha256/...")
    parser.add_argument("--backup", help="Expected backup pin sha256/...")
    parser.add_argument(
        "--repo-root",
        default=str(Path(__file__).resolve().parents[1]),
        help="Repo root for local.properties (default: parent of tools/)",
    )
    args = parser.parse_args()

    try:
        from cryptography import x509  # noqa: F401
    except ImportError:
        print(
            "FAIL: Python package 'cryptography' required. "
            "pip install cryptography",
            file=sys.stderr,
        )
        return 1

    root = Path(args.repo_root)
    try:
        primary, backup = resolve_configured_pins(args.primary, args.backup, root)
    except SystemExit as e:
        print(str(e), file=sys.stderr)
        return 1

    if not looks_like_pin(primary):
        print(f"FAIL: primary pin is not a 32-byte SPKI sha256/ pin: {primary[:48]}...", file=sys.stderr)
        return 1
    if not looks_like_pin(backup):
        print(f"FAIL: backup pin is not a 32-byte SPKI sha256/ pin: {backup[:48]}...", file=sys.stderr)
        return 1

    if args.pem:
        pem_text = Path(args.pem).read_text(encoding="utf-8")
        computed = spki_sha256_pin_from_pem(pem_text)
        source = f"pem:{args.pem}"
    else:
        der = fetch_peer_cert_der(args.host, args.port)
        computed = spki_sha256_pin_from_der_cert(der)
        source = f"host:{args.host}:{args.port}"

    print(f"computed={computed} source={source}")
    print(f"primary ={primary}")
    print(f"backup  ={backup}")
    # Secondary heuristic (informational only).
    for label, pin in (("primary", primary), ("backup", backup)):
        ratio = printable_byte_ratio(pin)
        if ratio > 20 / 32:
            print(
                f"WARN: {label} pin has high printable-byte ratio ({ratio:.2f}) — "
                "secondary check only; confirm against live SPKI.",
                file=sys.stderr,
            )

    if computed == primary or computed == backup:
        matched = "primary" if computed == primary else "backup"
        print(f"OK: live/PEM SPKI matches configured {matched} pin")
        if primary == backup:
            print("WARN: primary and backup are identical; rotation still needs a distinct next pin")
        return 0

    print(
        "FAIL: computed SPKI pin matches neither primary nor backup. "
        "Update CERT_PIN_* or pin the correct cert in the chain.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
