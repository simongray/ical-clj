"""Write the direct $this->parse(...) calls with literal arguments of
sabre/vobject's tests/VObject/Recur/RRuleIteratorTest.php, the rules with
the instances they expect, as a Clojure namespace.

    git clone https://github.com/sabre-io/vobject /tmp/vobject
    git -C /tmp/vobject checkout 5e2f338811916c03927398bb24e527989de63194
    python3 dev/sabre_cases.py /tmp/vobject/tests/VObject/Recur/RRuleIteratorTest.php \\
      > test/dk/simongray/ical/sabre_cases.cljc
"""
import re, sys

STR = r"""'((?:[^'\\]|\\.)*)'|"((?:[^"\\]|\\.)*)\""""

HEADER = """(ns dk.simongray.ical.sabre-cases
  "The rules of sabre/vobject's tests/VObject/Recur/RRuleIteratorTest.php
  at commit 5e2f338811916c03927398bb24e527989de63194, with the instances
  they expect, in sabre's form (e.g. 2011-10-07 12:00:00) in the zone :tz.
  Made by dev/sabre_cases.py.

  sabre/vobject is Copyright (C) 2011-2016 fruux GmbH, under the 3-clause
  BSD licence. See https://github.com/sabre-io/vobject/blob/master/LICENSE.")

(def cases
  "Each case as a map of its :name in sabre/vobject, :rrule, :start, :tz
  and :expected."
  ["""

def strings(s):
    return [a or b for a, b in re.findall(STR, s)]

def edn(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'

def cases(src):
    for m in re.finditer(r"public function (test\w+)\(\)[^{]*\{(.*?)\n    \}", src, re.S):
        name, body = m.group(1), m.group(2)
        call = re.search(r"\$this->parse\(\s*(.*)\);\s*$", body, re.S)
        if not call:
            continue
        head = re.match(r"\s*(?:" + STR + r")\s*,\s*(?:" + STR + r")\s*,\s*\[(.*?)\]\s*(.*)$",
                        call.group(1), re.S)
        if not head:
            continue
        rule, start = head.group(1) or head.group(2), head.group(3) or head.group(4)
        rest = head.group(6).strip().lstrip(",").strip()
        extra = [x.strip() for x in rest.split(",") if x.strip()] if rest else []
        tz = strings(extra[1])[0] if len(extra) > 1 and strings(extra[1]) else "UTC"
        # a test that fast-forwards the iterator checks something else
        if extra and extra[0] != "null":
            continue
        yield name, rule, start, tz, strings(head.group(5))

src = open(sys.argv[1], encoding="utf-8").read()
print(HEADER, end="")
print("\n   ".join("{:name " + edn(n) + "\n    :rrule " + edn(r) + "\n    :start " + edn(s)
                    + "\n    :tz " + edn(t) + "\n    :expected [" + " ".join(edn(e) for e in x) + "]}"
                    for n, r, s, t, x in cases(src)), end="")
print("])")
