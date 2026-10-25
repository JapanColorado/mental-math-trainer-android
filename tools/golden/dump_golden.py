"""Dump golden fixtures from the Python reference app (github: mental-math-trainer).

The Kotlin port's RNG is a bit-exact port of CPython's random.Random, so with the same seeds
the Kotlin generators must produce exactly the same problems, lessons, cards and session
traces as the Python app. This script records them as JSON under
core/src/test/resources/golden/; the Kotlin tests compare against those files.

Usage:  pixi run golden            (MMT_PY_SRC=/path/to/mental-math-trainer to override)
"""

from __future__ import annotations

import json
import os
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PY_SRC = Path(os.environ.get("MMT_PY_SRC", ROOT.parent / "mental-math-trainer")).resolve()
OUT = ROOT / "core/src/test/resources/golden"
sys.path.insert(0, str(PY_SRC))

from mmtrainer import content  # noqa: E402
from mmtrainer.content import (arithmetic, base, estimation, facts, fractions, logs,  # noqa: E402
                               powers, trig)
from mmtrainer.session import Session  # noqa: E402
from mmtrainer.store import DAY, Store  # noqa: E402

MODULES = {"facts": facts, "arithmetic": arithmetic, "fractions": fractions, "logs": logs,
           "powers": powers, "estimation": estimation, "trig": trig}
SEEDS = (0, 1)
PER_SEED = 30


def problem(p) -> dict:
    return {"prompt": p.prompt, "answer": p.answer, "display": p.display_answer, "tol_abs": p.tol_abs,
            "tol_rel": p.tol_rel, "explanation": p.explanation, "hint": p.hint, "format_hint": p.format_hint()}


def card(c) -> dict:
    return {"id": c.id, "prompt": c.prompt, "answer": c.answer, "display": c.display_answer,
            "tol_abs": c.tol_abs, "tol_rel": c.tol_rel, "note": c.note, "hint": c.hint}


def lesson(l) -> dict:
    return {"title": l.title, "body": l.body}


def dump_module(name: str, mod) -> dict:
    skills = []
    for s in getattr(mod, "SKILLS", []):
        levels = []
        for i, lv in enumerate(s.levels):
            runs = []
            for seed in SEEDS:
                rng = random.Random(seed * 1000 + i)
                runs.append({"seed": seed * 1000 + i, "problems": [problem(lv.generate(rng)) for _ in range(PER_SEED)]})
            levels.append({"description": lv.description, "target_seconds": lv.target_seconds, "runs": runs})
        skills.append({"id": s.id, "name": s.name, "track": s.track, "lesson": lesson(s.lesson), "levels": levels})
    decks = [{"id": d.id, "name": d.name, "track": d.track, "lesson": lesson(d.lesson),
              "target_seconds": d.target_seconds, "cards": [card(c) for c in d.cards]}
             for d in getattr(mod, "DECKS", [])]
    return {"module": name, "skills": skills, "decks": decks}


# ------------------------------------------------------------ python semantics helpers

def py_values() -> list[float]:
    rng = random.Random(99)
    vals = [0.0, -0.0, 1.0, -1.0, 0.5, 1.5, 2.5, -2.5, 0.125, 0.375, 0.0005, 0.00015, 1e-5, 1.23e-7,
            12.345, 2.675, 1.005, 99.995, 999.5, 1234.5, 9999.99, 123456.789, 1e15, 1.5e16, 1e22,
            3.14159265358979, 2.718281828459045, 0.1, 0.2, 0.30000000000000004, 1 / 3, 2 / 3, 1e-4,
            6.02214076e23, 0.000999, 9.9995, 0.045, 1e16, 123456789012345678.0, 5e-324]
    for _ in range(120):
        e = rng.randint(-8, 12)
        vals.append(round(rng.uniform(-10, 10), rng.randint(0, 6)) * 10.0 ** e)
        vals.append(rng.uniform(0, 1000))
    return vals


def dump_py() -> dict:
    cases = []
    for x in py_values():
        c = {"x": x, "repr": repr(x)}
        c["f"] = [f"{x:.{d}f}" for d in range(7)]
        c["f_grouped"] = f"{x:,.10f}" if abs(x) < 1e20 else None
        c["g"] = [f"{x:.{p}g}" for p in range(1, 8)]
        c["g_default"] = f"{x:g}"
        c["e"] = [f"{x:.{p}e}" for p in range(4)]
        c["round"] = [round(x, n) for n in range(-3, 7)]
        c["round_int"] = round(x) if abs(x) < 1e18 else None
        c["fmt_num"] = base.fmt_num(x) if abs(x) < 1e20 else None
        c["fmt_num_sig"] = [base.fmt_num(x, sig=s) if abs(x) < 1e20 else None for s in (1, 2, 3, 4)]
        c["fmt_num_dec"] = [base.fmt_num(x, decimals=d) if abs(x) < 1e20 else None for d in (0, 1, 2, 3)]
        c["fmt_sci"] = [base.fmt_sci(x, s) for s in (1, 2, 3, 4)] if x != 0 and abs(x) > 1e-300 else None
        c["sig_round"] = [base.sig_round(x, s) for s in (1, 2, 3, 4)] if abs(x) > 1e-300 or x == 0 else None
        cases.append(c)
    rngs = []
    for seed in (0, 1, 42, 2 ** 32 + 7, 123456789012):
        r = random.Random(seed)
        out = {"seed": seed}
        out["random"] = [r.random() for _ in range(5)]
        out["randint"] = [r.randint(1, 100) for _ in range(5)]
        out["randint_wide"] = [r.randint(-50, 10 ** 6) for _ in range(3)]
        out["choice"] = [r.choice(range(7)) for _ in range(5)]
        out["uniform"] = [r.uniform(0.5, 2) for _ in range(3)]
        out["randrange"] = [r.randrange(10), r.randrange(3, 30), r.randrange(0, 100, 5)]
        lst = list(range(12))
        r.shuffle(lst)
        out["shuffle"] = lst
        out["choices"] = [r.choices("abcde", [1, 2, 3, 4.5, 0.5])[0] for _ in range(5)]
        out["getrandbits40"] = [r.getrandbits(40) for _ in range(2)]
        rngs.append(out)
    return {"cases": cases, "rngs": rngs}


# ------------------------------------------------------------ session traces

class Clock:
    def __init__(self, t=1_800_000_000.0):
        self.t = t

    def __call__(self):
        return self.t


def trace_session(session, rule, seconds, limit, counter):
    items = []
    n = 0
    while n < limit:
        item = session.next_item()
        if item is None:
            break
        if item.kind == "lesson":
            items.append({"kind": "lesson", "source": item.source_id, "title": item.title})
            continue
        n += 1
        counter[0] += 1
        p = item.problem
        ok = rule(counter[0])
        value = p.answer if ok else p.answer * 3 + 7
        response = "" if not ok and counter[0] % 2 else str(value)
        if response == "":
            value = None
        secs = seconds(counter[0])
        r = session.submit(item, response, value, secs)
        items.append({"kind": "problem", "source": item.source_id, "card": item.card_id, "level": item.level,
                      "title": item.title, "tag": item.tag, "prompt": p.prompt, "new_card": item.new_card,
                      "relearn": item.relearn, "target": item.target_seconds, "correct": r.correct,
                      "promoted_to": r.promoted_to, "seconds": secs, "response": response})
    summary = session.finish()
    return {"items": items, "introduced": summary.introduced, "promotions": summary.promotions,
            "score": summary.score, "n": summary.n, "n_correct": summary.n_correct}


def store_state(store) -> dict:
    db = store.db
    return {
        "items": [dict(r) for r in db.execute("SELECT id, kind, introduced_ts, level, interval_days, due_ts FROM items ORDER BY id")],
        "cards": [dict(r) for r in db.execute("SELECT id, deck_id, state, ease, interval_days, due_ts, reps, lapses, last_ts FROM cards ORDER BY id")],
        "sessions": [dict(r) for r in db.execute("SELECT id, mode, start_ts, end_ts, n, n_correct, score, details FROM sessions ORDER BY id")],
        "streak": store.streak(),
        "skill_status": {sid: {"level": st.level, "n_recent": st.n_recent, "accuracy": st.accuracy,
                               "median_s": st.median_s, "mastered": st.mastered, "due": st.due,
                               "progress": st.progress}
                         for sid in content.SKILLS if store.is_introduced(sid)
                         for st in [store.skill_status(sid)]},
        "deck_stats": {did: store.deck_stats(did) for did in content.DECKS if store.is_introduced(did)},
        "daily_pace": store.daily_pace(),
        "due_cards": store.due_cards(),
        "next_to_introduce": store.next_to_introduce(),
        "learning_load": store.learning_load(),
    }


def scenarios() -> list[dict]:
    out = []

    def run(name, days, make, rule, seconds, limit):
        store = Store(":memory:")
        store.now = Clock()
        counter = [0]
        sessions = []
        for day in range(days):
            session = make(store, day)
            sessions.append(trace_session(session, rule, seconds, limit, counter))
            store.now.t += DAY
        out.append({"name": name, "sessions": sessions, "state": store_state(store)})

    def daily(seed):
        def make(store, day):
            s = Session(store, "daily", rng=random.Random(seed + day))
            s.budget = None
            return s
        return make

    run("daily_all_correct", 1, daily(1), lambda i: True, lambda i: 1.0, 60)
    run("daily_mixed", 1, daily(11), lambda i: i % 3 != 0, lambda i: 1.0 + (i % 5) * 0.7, 80)
    run("multi_day", 6, daily(21), lambda i: (i * 7) % 5 != 0, lambda i: 0.6 + (i % 7) * 0.9, 45)
    run("practice_pow2", 1, lambda store, d: (store.introduce("d_pow2"),
                                               Session(store, "practice", item_ids=["d_pow2"], rng=random.Random(4)))[1],
        lambda i: i % 4 != 1, lambda i: 2.0, 25)
    run("practice_add", 1, lambda store, d: (store.introduce("s_add"),
                                              Session(store, "practice", item_ids=["s_add"], rng=random.Random(2)))[1],
        lambda i: True, lambda i: 0.5, 30)
    run("benchmark", 1, lambda store, d: Session(store, "benchmark", rng=random.Random(5)),
        lambda i: i % 5 != 2, lambda i: 2.0 + (i % 3), 400)
    return out


def write(name: str, data) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{name}.json"
    path.write_text(json.dumps(data, ensure_ascii=False, indent=None, separators=(",", ":")) + "\n")
    print(f"wrote {path.relative_to(ROOT)} ({path.stat().st_size // 1024} KB)")


def main() -> None:
    write("py", dump_py())
    for name, mod in MODULES.items():
        write(name, dump_module(name, mod))
    write("session", {"scenarios": scenarios()})


if __name__ == "__main__":
    main()
