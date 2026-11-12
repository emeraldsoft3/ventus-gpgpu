#!/usr/bin/env python3
"""
Utility script to migrate legacy (35-entry) standalone kernel metadata fixtures
to the v2 (45-entry) format required by sim-verilator commit 89366aa.
"""

import argparse
import os
import sys

def migrate_file(filepath, work_dim, local_x, local_y, local_z, global_x, global_y, global_z):
    with open(filepath, "r") as f:
        lines = [line.strip() for line in f if line.strip()]
    if len(lines) % 2 != 0:
        raise ValueError(f"Odd number of hex words ({len(lines)}) in {filepath}")
    vals = [int(lines[i], 16) | (int(lines[i + 1], 16) << 32) for i in range(0, len(lines), 2)]
    if len(vals) == 45:
        print(f"Already v2 format (45 entries): {filepath}")
        return
    if len(vals) != 35:
        raise ValueError(f"Expected 35 entries (v1) or 45 (v2), got {len(vals)} in {filepath}")

    header = vals[:13]
    tail = vals[13:]
    v2_fields = [
        work_dim,
        global_x, global_y, global_z,
        local_x, local_y, local_z,
        0, 0, 0  # threadIdxOffset
    ]
    new_vals = header + v2_fields + tail
    assert len(new_vals) == 45

    with open(filepath, "w") as f:
        for val in new_vals:
            lo = val & 0xffffffff
            hi = (val >> 32) & 0xffffffff
            f.write(f"{lo:08x}\n{hi:08x}\n")
    print(f"Successfully migrated {filepath} to v2 format.")

def main():
    parser = argparse.ArgumentParser(description="Migrate standalone metadata from v1 to v2 format.")
    parser.add_argument("file", help="Path to .metadata file")
    parser.add_argument("--dim", type=int, default=1, help="Work dimension (1, 2, or 3)")
    parser.add_argument("--local", nargs=3, type=int, default=[32, 1, 1], help="Local workgroup thread sizes (x y z)")
    parser.add_argument("--global", nargs=3, type=int, default=[1024, 1, 1], dest="global_size", help="Global thread sizes (x y z)")
    args = parser.parse_args()

    migrate_file(args.file, args.dim, args.local[0], args.local[1], args.local[2],
                 args.global_size[0], args.global_size[1], args.global_size[2])

if __name__ == "__main__":
    main()
