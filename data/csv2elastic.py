#!/bin/env python3

import sys


def main():
    i = 0
    for line in sys.stdin:
        t = line.strip().split(",")
        if i == 0:
            names = t
        else:
            vs = []
            for v in t:
                try:
                    float(v)
                    vs.append(v)
                except ValueError:
                    vs.append(f'"{v}"')
            body = ", ".join(f'"{k}": {v}' for k, v in zip(names, vs))
            print('{ "create": {} }')
            print(f'{{ {body} }}')
        i += 1
    print


if __name__ == "__main__":
    main()
