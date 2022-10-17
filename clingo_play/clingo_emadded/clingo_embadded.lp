# script (python)

import clingo


def divisors (a):
    a = a . number
    for i in range(1, a + 1):
        if a % i == 0:
            yield clingo.Number(i)

# -------------END-----------------
# run clingo example.lp embedded.lp
