import csv
from pathlib import Path
from statistics import mean

import matplotlib.pyplot as plt


STRATEGIES = {
    "FIFO": "FIFO",
    "PRIORITY_AB": "Пріоритет A/B",
    "PRIORITY_C": "Пріоритет C",
}

plt.rcParams["font.family"] = "DejaVu Sans"
plt.rcParams["font.size"] = 11
plt.rcParams["savefig.dpi"] = 200

def read_csv(path):
    with open(path, encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file, delimiter=";"))


def finish(ax, xlabel, ylabel, filename):
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.grid(axis="y", alpha=0.25)
    ax.set_axisbelow(True)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    plt.tight_layout()
    plt.savefig(filename, bbox_inches="tight")
    plt.close()


def plot_pilot(pilot, scenario, title, filename, warmup=None):
    rows = [r for r in pilot if r["scenario"] == scenario]
    times = sorted({float(r["time_min"]) for r in rows})

    fig, ax = plt.subplots(figsize=(10.5, 5.7))
    for strategy, label in STRATEGIES.items():
        values = [mean(float(r["cum_avg_queue_tasks"]) for r in rows
                       if r["strategy"] == strategy and float(r["time_min"]) == t)
                  for t in times]
        ax.plot(times, values, marker="o", linewidth=2, label=label)

    if warmup is not None:
        ax.axvline(warmup, color="gray", linestyle="--", linewidth=1.2,
                   label="Обраний перехідний період")

    ax.set_title(title)
    ax.legend()
    finish(ax, "Модельний час, хв", "Накопичена середня довжина черги, завдань", filename)


def plot_ci(summary, filename):
    rows = {r["strategy"]: r for r in summary if r["scenario"] == "REDUCED"}
    labels = list(STRATEGIES.values())
    values = [float(rows[s]["queue_mean_tasks"]) for s in STRATEGIES]
    errors = [float(rows[s]["queue_CI95_halfwidth_tasks"]) for s in STRATEGIES]

    fig, ax = plt.subplots(figsize=(9.5, 5.7))
    x = range(3)
    ax.bar(x, values, width=0.6, yerr=errors, capsize=6,
           error_kw={"linewidth": 1.7})
    ax.set_xticks(list(x), labels)
    ax.set_ylim(0, max(v + e for v, e in zip(values, errors)) * 1.2)
    ax.set_title("Середня черга і 95% довірчі інтервали (зменшений потік)")
    for i, value in enumerate(values):
        ax.annotate(f"{value:.4f}", (i, value), xytext=(0, 10),
                    textcoords="offset points", ha="center")

    finish(ax, "Стратегія управління чергою", "Середня довжина черги, завдань", filename)


def plot_classes(summary, filename):
    rows = {r["strategy"]: r for r in summary if r["scenario"] == "BASE"}
    x = range(3)
    width = 0.24

    fig, ax = plt.subplots(figsize=(10, 5.7))
    for i, (strategy, label) in enumerate(STRATEGIES.items()):
        values = [float(rows[strategy][f"queue_{c}_mean"]) for c in "ABC"]
        ax.bar([j + (i - 1) * width for j in x], values, width, label=label)

    ax.set_xticks(list(x), ["Клас A", "Клас B", "Клас C"])
    ax.set_title("Середня довжина черги за класами (базовий сценарій)")
    ax.legend()
    finish(ax, "Клас завдань", "Середня довжина черги, завдань", filename)


def main():
    folder = Path(__file__).resolve().parent / "results"

    pilot = read_csv(folder / "pilot.csv")
    summary = read_csv(folder / "summary.csv")

    plot_pilot(pilot, "BASE", "Зростання накопиченої середньої черги (базовий сценарій)",
               folder / "overload_growth.png")
    plot_pilot(pilot, "REDUCED", "Збіжність оцінки середньої черги (зменшений потік)",
               folder / "stable_convergence.png", warmup=5000)
    plot_classes(summary, folder / "queue_by_class.png")
    plot_ci(summary, folder / "steady_state_CI.png")


if __name__ == "__main__":
    main()
