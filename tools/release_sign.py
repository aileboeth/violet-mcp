"""
给发布的 SHA256SUMS.txt 签名（Violet MCP 1.4.4；用户 2026-11-08：国内服务器经 GitHub 加速站下载中继时，
加速站不能改动中继程序）。

  python tools/release_sign.py <文件>       写出 <文件>.sig（一行 base64 的 Ed25519 签名）

Violet（violet/tools/violet_sign.py 里的 MCP_RELAY_KEY）经加速站下载中继时核对这个签名；直接从 github.com
下载时没有签名也接受（1.4.3 及更早的版本）。

私钥：signing/relay-sums-ed25519.key（64 个十六进制字符；signing/ 不进仓库、不进交接包），
公钥：下面的 RELAY_PUBLIC_KEY，必须和 Violet 里的 MCP_RELAY_KEY 一样。
Ed25519 按 RFC 8032 第 6 节，只用标准库（签名只在发布电脑上运行，不要求常数时间）。
"""
from __future__ import annotations

import base64
import hashlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KEY_FILE = ROOT / "signing" / "relay-sums-ed25519.key"
RELAY_PUBLIC_KEY = "Tf0YPcokxMSZwmpDfqucHE8HZqpgv0NXXcY+d78nw0I="

_P = 2 ** 255 - 19
_Q = 2 ** 252 + 27742317777372353535851937790883648493
_D = -121665 * pow(121666, _P - 2, _P) % _P
_SQRT_M1 = pow(2, (_P - 1) // 4, _P)


def _inv(x: int) -> int:
    return pow(x, _P - 2, _P)


def _add(a: tuple, b: tuple) -> tuple:
    x1 = (a[1] - a[0]) * (b[1] - b[0]) % _P
    x2 = (a[1] + a[0]) * (b[1] + b[0]) % _P
    c = 2 * a[3] * b[3] * _D % _P
    d = 2 * a[2] * b[2] % _P
    e, f, g, h = x2 - x1, d - c, d + c, x2 + x1
    return e * f % _P, g * h % _P, f * g % _P, e * h % _P


def _mul(s: int, point: tuple) -> tuple:
    out = (0, 1, 1, 0)
    while s > 0:
        if s & 1:
            out = _add(out, point)
        point = _add(point, point)
        s >>= 1
    return out


def _recover_x(y: int, sign: int) -> int:
    x2 = (y * y - 1) * _inv(_D * y * y + 1)
    x = pow(x2, (_P + 3) // 8, _P)
    if (x * x - x2) % _P != 0:
        x = x * _SQRT_M1 % _P
    if (x & 1) != sign:
        x = _P - x
    return x


_GY = 4 * _inv(5) % _P
_GX = _recover_x(_GY, 0)
_G = (_GX, _GY, 1, _GX * _GY % _P)


def _compress(point: tuple) -> bytes:
    zinv = _inv(point[2])
    x, y = point[0] * zinv % _P, point[1] * zinv % _P
    return int.to_bytes(y | ((x & 1) << 255), 32, "little")


def _expand(seed: bytes) -> tuple[int, bytes]:
    h = hashlib.sha512(seed).digest()
    a = int.from_bytes(h[:32], "little")
    a &= (1 << 254) - 8
    a |= 1 << 254
    return a, h[32:]


def _h(data: bytes) -> int:
    return int.from_bytes(hashlib.sha512(data).digest(), "little")


def public_key(seed: bytes) -> bytes:
    return _compress(_mul(_expand(seed)[0], _G))


def sign(seed: bytes, message: bytes) -> bytes:
    a, prefix = _expand(seed)
    pub = _compress(_mul(a, _G))
    r = _h(prefix + message) % _Q
    big_r = _compress(_mul(r, _G))
    s = (r + _h(big_r + pub + message) % _Q * a) % _Q
    return big_r + int.to_bytes(s, 32, "little")


def load_key(path: Path = KEY_FILE) -> bytes:
    if not path.is_file():
        raise SystemExit(f"找不到签名私钥 {path}（signing/ 不进仓库；没有它就不能给 SHA256SUMS.txt 签名）")
    seed = bytes.fromhex(path.read_text(encoding="ascii").strip())
    if len(seed) != 32 or base64.b64encode(public_key(seed)).decode() != RELAY_PUBLIC_KEY:
        raise SystemExit(f"{path} 不是公钥 {RELAY_PUBLIC_KEY} 对应的私钥")
    return seed


def sign_file(path: Path, seed: bytes | None = None) -> Path:
    seed = seed if seed is not None else load_key()
    target = path.with_name(path.name + ".sig")
    target.write_bytes(base64.b64encode(sign(seed, path.read_bytes())) + b"\n")
    return target


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("用法：python tools/release_sign.py <文件>")
    print(sign_file(Path(sys.argv[1])))
