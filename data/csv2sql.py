#!/bin/env python3

import sys


def main():
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} table")
        sys.exit(1)
    i = 0
    for line in sys.stdin:
        t = line.strip().split(",")
        if i == 0:
            print(f"INSERT INTO {sys.argv[1]} ({', '.join(t)})")
            print("VALUES")
        else:
            if i > 1:
                print(",")
            vs = []
            for v in t:
                try:
                    float(v)
                    vs.append(v)
                except ValueError:
                    vs.append(f"'{v}'")
            print(f"    ({', '.join(vs)})", end="")
        i += 1
    print(";")


if __name__ == "__main__":
    main()
