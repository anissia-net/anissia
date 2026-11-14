#!/usr/bin/env python3
"""실제 서명 정보로 검토용 App Links/Universal Links 파일을 생성한다."""

from __future__ import annotations

import argparse
import copy
import json
import os
from pathlib import Path
import re
import tempfile


ANDROID_PACKAGE = "anissia.android.schedule"
IOS_BUNDLE_ID = "net.anissia.schedule"
HANDLE_URLS = "delegate_permission/common.handle_all_urls"
SCHEDULE_PATHS = (
    "/schedule", "/schedule/", "/schedule/2026", "/schedule/2026/",
    "/schedule/2015", "/schedule/2015/",
)


def certificate_fingerprint(value: str) -> str:
    value = value.strip()
    if not re.fullmatch(r"[0-9a-fA-F]{64}|(?:[0-9a-fA-F]{2}:){31}[0-9a-fA-F]{2}", value):
        raise argparse.ArgumentTypeError("Android SHA256는 실제 인증서의 64자리 hex 또는 콜론으로 구분한 32바이트여야 합니다.")
    raw = value.replace(":", "").upper()
    if len(set(bytes.fromhex(raw))) == 1:
        raise argparse.ArgumentTypeError("반복된 바이트로 채운 SHA256 대신 실제 앱 서명 인증서 지문을 입력하세요.")
    return ":".join(raw[index:index + 2] for index in range(0, 64, 2))


def application_identifier_prefix(value: str) -> str:
    value = value.strip()
    examples = {"ABCDE12345", "ABCDEFGHIJ", "YOURTEAMID", "TEAMID1234"}
    if not re.fullmatch(r"[A-Z0-9]{10}", value) or len(set(value)) == 1 or value in examples:
        raise argparse.ArgumentTypeError("Apple application-identifier의 실제 10자리 prefix를 입력하세요. 예시 값은 사용할 수 없습니다.")
    return value


def _unique_object(pairs: list[tuple]) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"중복 JSON 키가 있습니다: {key}")
        result[key] = value
    return result


def _read_json(path: Path, default):
    if path.is_symlink():
        raise ValueError(f"심볼릭 링크 출력 파일은 덮어쓰지 않습니다: {path}")
    if not path.exists():
        return default
    if not path.is_file():
        raise ValueError(f"출력 경로가 일반 파일이 아닙니다: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=_unique_object)
    except (ValueError, UnicodeError) as error:
        raise ValueError(f"기존 JSON을 읽을 수 없어 파일을 보존합니다: {path}: {error}") from error


def merge_assetlinks(existing, fingerprints: list[str]) -> list:
    if not isinstance(existing, list) or any(not isinstance(entry, dict) for entry in existing):
        raise ValueError("기존 assetlinks.json은 JSON 객체의 배열이어야 합니다.")
    generated = {
        "relation": [HANDLE_URLS],
        "target": {
            "namespace": "android_app",
            "package_name": ANDROID_PACKAGE,
            "sha256_cert_fingerprints": list(dict.fromkeys(fingerprints)),
        },
    }
    found = False
    for entry in existing:
        target = entry.get("target", {})
        if isinstance(target, dict) and target.get("namespace") == "android_app" and target.get("package_name") == ANDROID_PACKAGE:
            if entry != generated or found:
                raise ValueError("기존 Android 애니편성표 연결 항목과 충돌합니다. 기존 파일을 유지하고 별도 출력 폴더에서 생성한 뒤 병합을 검토하세요.")
            found = True
    return copy.deepcopy(existing if found else [*existing, generated])


def merge_association(existing, prefix: str) -> dict:
    if not isinstance(existing, dict):
        raise ValueError("기존 apple-app-site-association은 JSON 객체여야 합니다.")
    result = copy.deepcopy(existing)
    applinks = result.setdefault("applinks", {})
    if not isinstance(applinks, dict):
        raise ValueError("기존 AASA applinks는 JSON 객체여야 합니다.")
    details = applinks.setdefault("details", [])
    if not isinstance(details, list) or any(not isinstance(entry, dict) for entry in details):
        raise ValueError("기존 AASA applinks.details는 객체의 배열이어야 합니다. 이전 형식은 수동 병합이 필요합니다.")
    generated = {
        "appIDs": [f"{prefix}.{IOS_BUNDLE_ID}"],
        "defaults": {"caseSensitive": True, "percentEncoded": True},
        "components": [
            {"?": {"_anissia_browser": "1"}, "exclude": True},
            *({"/": path} for path in SCHEDULE_PATHS),
        ],
    }
    found = False
    for entry in details:
        ids = entry.get("appIDs", [])
        if not isinstance(ids, list) or any(not isinstance(value, str) for value in ids):
            raise ValueError("기존 AASA appIDs 형식이 올바르지 않습니다.")
        if "appID" in entry:
            if not isinstance(entry["appID"], str):
                raise ValueError("기존 AASA appID 형식이 올바르지 않습니다.")
            ids = [*ids, entry["appID"]]
        if not ids:
            raise ValueError("기존 AASA details에 앱 식별자가 없는 항목이 있습니다.")
        if any(value.endswith(f".{IOS_BUNDLE_ID}") for value in ids):
            if entry != generated or found:
                raise ValueError("기존 iOS 애니편성표 연결 항목과 충돌합니다. 기존 파일을 유지하고 별도 출력 폴더에서 생성한 뒤 병합을 검토하세요.")
            found = True
    if not found:
        details.append(generated)
    return result


def generate(output_dir: Path, fingerprints: list[str], prefix: str) -> tuple[Path, Path]:
    fingerprints = [certificate_fingerprint(value) for value in fingerprints]
    if not fingerprints:
        raise ValueError("실제 Android 앱 서명 인증서 SHA256가 필요합니다.")
    prefix = application_identifier_prefix(prefix)
    android_path = output_dir / "assetlinks.json"
    apple_path = output_dir / "apple-app-site-association"
    previous_android = _read_json(android_path, [])
    previous_apple = _read_json(apple_path, {})
    android = merge_assetlinks(previous_android, fingerprints)
    apple = merge_association(previous_apple, prefix)
    pending = [(path, data) for path, data, previous in (
        (android_path, android, previous_android), (apple_path, apple, previous_apple)
    ) if not path.exists() or data != previous]
    output_dir.mkdir(parents=True, exist_ok=True)
    staged = []
    try:
        for destination, content in pending:
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=output_dir, prefix=".app-links-", delete=False) as temporary:
                staged.append((Path(temporary.name), destination))
                json.dump(content, temporary, ensure_ascii=False, indent=2, allow_nan=False)
                temporary.write("\n")
                temporary.flush()
                os.fsync(temporary.fileno())
            os.chmod(temporary.name, destination.stat().st_mode & 0o777 if destination.exists() else 0o644)
        for temporary, destination in staged:
            os.replace(temporary, destination)
    finally:
        for temporary, _ in staged:
            temporary.unlink(missing_ok=True)
    return android_path, apple_path


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="실제 서명 정보로 검토용 연결 파일 2개를 생성합니다. 배포는 수행하지 않습니다.",
        epilog="다른 앱 항목은 보존합니다. 기존 애니편성표 항목이 생성 결과와 다르면 덮어쓰지 않고 실패합니다.",
    )
    parser.add_argument("--android-sha256", type=certificate_fingerprint, action="append", required=True,
                        help="배포 앱 서명 인증서 SHA256. Play App Signing 사용 시 앱 서명 지문을 사용하며, 복수 인증서는 옵션을 반복합니다.")
    parser.add_argument("--apple-app-id-prefix", type=application_identifier_prefix, required=True,
                        help="서명된 앱의 application-identifier에서 Bundle ID 앞의 실제 prefix")
    parser.add_argument("--output-dir", type=Path, required=True, help="검토용 JSON 파일을 저장할 디렉터리")
    args = parser.parse_args(argv)
    try:
        paths = generate(args.output_dir, args.android_sha256, args.apple_app_id_prefix)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    for path in paths:
        print(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
