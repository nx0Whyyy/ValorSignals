"""Generate a compact, client-safe Minecraft meteor model and pixel texture."""
import json
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "resource-pack/assets/skysignals/models/item/meteor_flight.json"
TEXTURE = ROOT / "resource-pack/assets/skysignals/textures/item/meteor_flight.png"


def faces(texture="#rock"):
    return {face: {"uv": [0, 0, 16, 16], "texture": texture}
            for face in ("north", "south", "west", "east", "up", "down")}


def cube(start, end, rotation=None, texture="#rock"):
    value = {"from": start, "to": end, "faces": faces(texture)}
    if rotation:
        value["rotation"] = rotation
    return value


elements = [
    cube([3, 3, 3], [13, 13, 13]),
    cube([1, 5, 5], [5, 11, 11], {"origin": [4, 8, 8], "axis": "z", "angle": 22.5}),
    cube([11, 4, 4], [16, 12, 12], {"origin": [12, 8, 8], "axis": "z", "angle": -22.5}),
    cube([5, 11, 5], [11, 16, 11], {"origin": [8, 12, 8], "axis": "y", "angle": 22.5}),
    cube([4, 0, 4], [12, 5, 12], {"origin": [8, 4, 8], "axis": "y", "angle": -22.5}),
    cube([5, 5, 11], [11, 11, 16], {"origin": [8, 8, 12], "axis": "x", "angle": 22.5}),
    cube([4, 4, 0], [12, 12, 5], {"origin": [8, 8, 4], "axis": "x", "angle": -22.5}),
    cube([0, 2, 5], [4, 7, 10], {"origin": [3, 5, 8], "axis": "z", "angle": 45}),
    cube([12, 9, 5], [17, 14, 10], {"origin": [13, 11, 8], "axis": "z", "angle": 45}),
    cube([5, 12, 1], [10, 17, 5], {"origin": [8, 13, 4], "axis": "x", "angle": 45}),
    cube([6, -1, 11], [11, 4, 16], {"origin": [8, 3, 12], "axis": "x", "angle": -45}),
    cube([2, 10, 10], [6, 14, 14], {"origin": [5, 11, 11], "axis": "y", "angle": 45}, "#hot"),
    cube([10, 2, 2], [14, 6, 6], {"origin": [11, 5, 5], "axis": "y", "angle": -45}, "#hot"),
]

MODEL.parent.mkdir(parents=True, exist_ok=True)
MODEL.write_text(json.dumps({
    "credit": "ValorSky flight meteor",
    "textures": {
        "rock": "skysignals:item/meteor_flight",
        "hot": "skysignals:item/meteor_flight",
        "particle": "skysignals:item/meteor_flight"
    },
    "elements": elements
}, indent=2) + "\n", encoding="utf-8")

# Deterministic 32x32 dark stone with bright molten cracks.
random.seed(7421)
size = 32
pixels = []
for y in range(size):
    row = []
    crack = abs((y * 7 + 5) % size - 16) < 2
    for x in range(size):
        noise = random.randint(-12, 12)
        base = max(22, min(72, 46 + noise + ((x + y) % 5) * 2))
        molten = crack and ((x * 3 + y) % 11 < 4)
        if (x - (y * 7 + 5) % size) % size in (0, 1):
            molten = True
        row.extend((255, 122 + random.randint(0, 55), 8, 255) if molten
                   else (base, max(18, base - 8), max(15, base - 12), 255))
    pixels.append(bytes(row))

raw = b"".join(b"\x00" + row for row in pixels)
def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
TEXTURE.parent.mkdir(parents=True, exist_ok=True)
TEXTURE.write_bytes(png)
print(f"Generated {len(elements)} meteor elements and {size}x{size} texture")
