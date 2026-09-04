"""Deterministic Range-capable HTTP server for media download smoke tests."""

from __future__ import annotations

import argparse
import os
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


class SlowRangeHandler(BaseHTTPRequestHandler):
    server_version = "JieXiQAServer/1.0"

    def do_GET(self) -> None:
        source: Path = self.server.source  # type: ignore[attr-defined]
        if self.path.split("?", 1)[0] != f"/{source.name}":
            self.send_error(404)
            return

        total = source.stat().st_size
        start, end = 0, total - 1
        range_header = self.headers.get("Range", "")
        match = re.fullmatch(r"bytes=(\d+)-(\d*)", range_header.strip())
        partial = match is not None
        if match:
            start = int(match.group(1))
            if match.group(2):
                end = min(int(match.group(2)), total - 1)
            if start >= total or end < start:
                self.send_response(416)
                self.send_header("Content-Range", f"bytes */{total}")
                self.end_headers()
                return

        length = end - start + 1
        self.send_response(206 if partial else 200)
        self.send_header("Content-Type", "video/mp4")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        if partial:
            self.send_header("Content-Range", f"bytes {start}-{end}/{total}")
        self.end_headers()

        bytes_per_second: int = self.server.bytes_per_second  # type: ignore[attr-defined]
        chunk_size = min(64 * 1024, max(4096, bytes_per_second // 8))
        with source.open("rb") as media:
            media.seek(start)
            remaining = length
            while remaining > 0:
                chunk = media.read(min(chunk_size, remaining))
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                    self.wfile.flush()
                except (BrokenPipeError, ConnectionResetError):
                    break
                remaining -= len(chunk)
                time.sleep(len(chunk) / bytes_per_second)

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"[{self.log_date_time_string()}] {fmt % args}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("file", type=Path)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18765)
    parser.add_argument("--bytes-per-second", type=int, default=262_144)
    args = parser.parse_args()
    source = args.file.resolve(strict=True)
    if source.suffix.lower() != ".mp4":
        raise SystemExit("QA source must be an MP4 file")

    server = ThreadingHTTPServer((args.host, args.port), SlowRangeHandler)
    server.source = source  # type: ignore[attr-defined]
    server.bytes_per_second = max(4096, args.bytes_per_second)  # type: ignore[attr-defined]
    print(
        f"READY http://{args.host}:{args.port}/{source.name} "
        f"pid={os.getpid()} bytes={source.stat().st_size}",
        flush=True,
    )
    server.serve_forever()


if __name__ == "__main__":
    main()
