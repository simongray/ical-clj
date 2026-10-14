"""Write the literal rrule cases of python-dateutil's tests/test_rrule.py
as RFC 5545 rules with their expected instances, as a Clojure namespace.

    git clone https://github.com/dateutil/dateutil /tmp/dateutil
    git -C /tmp/dateutil checkout 2642afacc33fb839c404b75b230fff58b79793f2
    python3 dev/dateutil_cases.py /tmp/dateutil/tests/test_rrule.py \
      > test/dk/simongray/ical/dateutil_cases.cljc
"""
import ast, sys

FREQS = ["YEARLY", "MONTHLY", "WEEKLY", "DAILY", "HOURLY", "MINUTELY", "SECONDLY"]
DAYS = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"]

class Skip(Exception):
    pass

def num(node):
    if isinstance(node, ast.Constant) and isinstance(node.value, int):
        return node.value
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.USub, ast.UAdd)):
        v = num(node.operand)
        return -v if isinstance(node.op, ast.USub) else v
    if isinstance(node, ast.Call) and getattr(node.func, "id", None) == "long":
        return num(node.args[0])
    raise Skip("number")

def items(node):
    if isinstance(node, (ast.Tuple, ast.List, ast.Set)):
        return list(node.elts)
    return [node]

def weekday(node):
    if isinstance(node, ast.Name) and node.id in DAYS:
        return node.id
    if isinstance(node, ast.Call) and getattr(node.func, "id", None) in DAYS:
        n = num(node.args[0])
        return f"{n}{node.func.id}"
    if isinstance(node, ast.Constant) and isinstance(node.value, int):
        return DAYS[node.value]
    raise Skip("weekday")

def dt(node):
    if not (isinstance(node, ast.Call) and getattr(node.func, "id", None) in ("datetime", "date")):
        raise Skip("datetime")
    parts = [num(a) for a in node.args] + [0] * 6
    y, mo, d, h, mi, s = parts[:6]
    if node.func.id == "date":
        return f"{y:04d}-{mo:02d}-{d:02d}"
    return f"{y:04d}-{mo:02d}-{d:02d}T{h:02d}:{mi:02d}:{s:02d}"

def compact(v):
    return v.replace("-", "").replace(":", "")

def rule(call):
    if getattr(call.func, "id", None) != "rrule":
        raise Skip("not rrule")
    freq = call.args[0]
    if not (isinstance(freq, ast.Name) and freq.id in FREQS):
        raise Skip("freq")
    parts, start = [f"FREQ={freq.id}"], None
    for kw in call.keywords:
        k, v = kw.arg, kw.value
        if k == "dtstart":
            start = dt(v)
        elif k == "cache":
            pass
        elif k in ("count", "interval"):
            parts.append(f"{k.upper()}={num(v)}")
        elif k == "until":
            parts.append(f"UNTIL={compact(dt(v))}")
        elif k == "wkst":
            parts.append(f"WKST={weekday(v)}")
        elif k == "byweekday":
            parts.append("BYDAY=" + ",".join(weekday(x) for x in items(v)))
        elif k in ("bymonth", "bymonthday", "byyearday", "byweekno", "byhour",
                   "byminute", "bysecond", "bysetpos"):
            parts.append(f"{k.upper()}=" + ",".join(str(num(x)) for x in items(v)))
        else:
            raise Skip(k)
    if start is None:
        raise Skip("no dtstart")
    return ";".join(parts), start

def cases(tree):
    for fn in ast.walk(tree):
        if not isinstance(fn, ast.FunctionDef):
            continue
        for node in ast.walk(fn):
            if not (isinstance(node, ast.Call) and getattr(node.func, "attr", None) == "assertEqual"):
                continue
            a, b = node.args[:2]
            if not (isinstance(a, ast.Call) and getattr(a.func, "id", None) == "list" and a.args
                    and isinstance(a.args[0], ast.Call) and isinstance(b, ast.List)):
                continue
            try:
                r, start = rule(a.args[0])
                expected = [dt(x) for x in b.elts]
            except Skip:
                continue
            yield fn.name, r, start, expected

def edn(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'

HEADER = """(ns dk.simongray.ical.dateutil-cases
  "The rules of python-dateutil's tests/test_rrule.py at commit
  2642afacc33fb839c404b75b230fff58b79793f2, with the instances they
  expect. Made by dev/dateutil_cases.py.

  python-dateutil is Copyright (c) 2003-2011 Gustavo Niemeyer, 2012-2014
  Tomi Pievil\u00e4inen, 2014-2016 Yaron de Leeuw, and 2015- Paul Ganssle
  and the dateutil contributors, under the 3-clause BSD licence, with the
  Apache License 2.0 for contributions since 1 December 2017. See
  https://github.com/dateutil/dateutil/blob/master/LICENSE.")

(def cases
  "Each case as a map of its :name in dateutil, :rrule, :dtstart and
  :expected."
  ["""

tree = ast.parse(open(sys.argv[1], encoding="utf-8").read())
print(HEADER.replace("\\u00e4", "\u00e4"), end="")
print("\n".join("{:name " + edn(name) + "\n    :rrule " + edn(r) + "\n    :dtstart " + edn(start)
                 + "\n    :expected [" + " ".join(edn(e) for e in expected) + "]}"
                 for name, r, start, expected in cases(tree)).replace("\n{", "\n   {"), end="")
print("])")
