from pathlib import Path
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy import stats

FOLDER = Path(__file__).resolve().parent / "results"
STRATEGIES = ["FIFO", "FIRST_FIT", "FAIR_RESERVE", "BOUNDED_BYPASS"]
LABELS = ["FIFO", "First fit", "Fair reserve", "Bounded bypass"]

plt.rcParams["font.family"] = "DejaVu Sans"
plt.rcParams["savefig.dpi"] = 180

def save(name):
    plt.grid(axis="y", alpha=0.2)
    plt.tight_layout()
    plt.savefig(FOLDER / name)
    plt.close()


def analyze(data):
    grouped = data.groupby(["scenario", "strategy"])
    summary = grouped.agg(
        n=("avg_queue_tasks", "size"),
        queue_mean=("avg_queue_tasks", "mean"),
        queue_sd=("avg_queue_tasks", "std"),
        waiting_sd=("avg_waiting_tasks", "std"),
        waiting_mean=("avg_waiting_tasks", "mean"),
        load_mean=("load_percent", "mean"),
        blocked_D0_mean=("blocked_D0_pct", "mean"),
        idle_with_queue_mean=("idle_with_queue_pct", "mean"),
        queue_A_mean=("avg_A_tasks", "mean"),
        queue_B_mean=("avg_B_tasks", "mean"),
        queue_C_mean=("avg_C_tasks", "mean"),
        waiting_A_mean=("avg_wait_A", "mean"),
        waiting_B_mean=("avg_wait_B", "mean"),
        waiting_C_mean=("avg_wait_C", "mean"),
        throughput_mean=("throughput_tasks_per_min", "mean"),
    ).reset_index()
    summary["CI95_halfwidth"] = stats.t.ppf(0.975, summary["n"] - 1) * summary["waiting_sd"] / np.sqrt(summary["n"])
    summary.to_csv(FOLDER / "summary.csv", sep=";", index=False, float_format="%.6f")

    comparisons = []
    anova = []
    for scenario in ["BASE", "REDUCED"]:
        rows = data[data.scenario == scenario]
        matrix = rows.pivot(index="rep", columns="strategy", values="avg_waiting_tasks")
        f, p = stats.f_oneway(*(matrix[s].to_numpy() for s in STRATEGIES))
        anova.append({"scenario": scenario, "F": f, "p_value": p})
        for strategy in STRATEGIES[1:]:
            diff = matrix[strategy] - matrix["FIFO"]
            ci = stats.t.interval(0.95, len(diff) - 1, loc=diff.mean(), scale=stats.sem(diff))
            comparisons.append({"scenario": scenario, "comparison": strategy + " - FIFO",
                                "mean_difference": diff.mean(), "CI95_low": ci[0],
                                "CI95_high": ci[1], "p_value": stats.ttest_rel(matrix[strategy], matrix["FIFO"]).pvalue})
    pd.DataFrame(anova).to_csv(FOLDER / "anova.csv", sep=";", index=False, float_format="%.8g")
    pd.DataFrame(comparisons).to_csv(FOLDER / "paired_comparisons.csv", sep=";", index=False, float_format="%.6f")
    return summary


def plot_pilot(pilot, scenario, filename):
    data = pilot[pilot.scenario == scenario]
    plt.figure(figsize=(9, 5))
    for i, (strategy, label) in enumerate(zip(STRATEGIES, LABELS)):
        rows = data[data["strategy"] == strategy].groupby("time_min")["cum_avg_waiting_tasks"].mean()
        plt.plot(rows.index, rows.values, linestyle=(0, (5+i, 10+i)), label=label)
    if scenario == "REDUCED":
        plt.axvline(5000, linestyle="--", color="gray")
    plt.xlabel("Модельний час, хв")
    plt.ylabel("Середня кількість в очікуванні (Q + D0)")
    plt.legend()
    save(filename)


def plot_comparison(summary):
    rows = summary.set_index(["scenario", "strategy"])
    x = np.arange(4)
    reduced = rows.loc["REDUCED"].reindex(STRATEGIES)
    plt.figure(figsize=(9, 5))
    plt.bar(x, reduced.waiting_mean, yerr=reduced.CI95_halfwidth, capsize=4)
    plt.xticks(x, LABELS)
    plt.ylabel("Середня кількість в очікуванні (Q + D0)")
    save("queue_CI95.png")

    base = rows.loc["BASE"].reindex(STRATEGIES)
    plt.figure(figsize=(9, 5))
    for i, col in enumerate(["waiting_A_mean", "waiting_B_mean", "waiting_C_mean"]):
        plt.bar(x + (i - 1) * 0.25, base[col], 0.25, label="Клас " + "ABC"[i])
    plt.xticks(x, LABELS)
    plt.ylabel("Середня кількість в очікуванні за класами")
    plt.legend()
    save("queue_by_class.png")

    plt.figure(figsize=(9, 5))
    for scenario, offset in [("BASE", -0.18), ("REDUCED", 0.18)]:
        values = rows.loc[scenario].reindex(STRATEGIES)
        plt.bar(x + offset, values.load_mean, width=0.36, label=scenario)
    plt.xticks(x, LABELS)
    plt.ylabel("Фактичне завантаження, %")
    plt.ylim(0, 105)
    plt.legend()
    save("utilization.png")


def plot_sample():
    states = pd.read_csv(FOLDER / "queue_states.csv", sep=";")
    tasks = pd.read_csv(FOLDER / "tasks.csv", sep=";")

    for col in ["time_min", "duration_min"]:
        states[col] = pd.to_numeric(
            states[col].astype(str).str.replace(",", ".", regex=False)
        )

    for col in ["wait_min", "service_min", "system_min"]:
        tasks[col] = pd.to_numeric(
            tasks[col].astype(str).str.replace(",", ".", regex=False)
        )
    plt.figure(figsize=(8, 5))
    bins = np.arange(states.queue.max() + 2) - 0.5
    plt.hist(states.queue, bins=bins, weights=states.duration_min / states.duration_min.sum())
    plt.xlabel("Кількість завдань у черзі")
    plt.ylabel("Частка модельного часу")
    save("queue_histogram.png")

    plt.figure(figsize=(8, 5))
    plt.hist(tasks.wait_min, bins=20)
    plt.xlabel("Час очікування, хв")
    plt.ylabel("Кількість завершених завдань")
    save("waiting_histogram.png")

    mean = np.average(states.queue, weights=states.duration_min)
    sd = np.sqrt(np.average((states.queue - mean) ** 2, weights=states.duration_min))
    tasks.groupby("type")[["wait_min", "service_min", "system_min"]].agg(["mean", "std"]).to_csv(
        FOLDER / "task_statistics.csv", sep=";", float_format="%.6f")
    with open(FOLDER / "queue_statistics.txt", "w", encoding="utf-8") as file:
        file.write(f"Mean queue: {mean:.6f}\nStd queue: {sd:.6f}\nMax queue: {states.queue.max()}\n")


def main():
    pilot = pd.read_csv(FOLDER / "pilot.csv", sep=";", encoding="utf-8-sig", decimal=",")
    runs = pd.read_csv(FOLDER / "replications.csv", sep=";", encoding="utf-8-sig", decimal=",")
    summary = analyze(runs)
    plot_pilot(pilot, "BASE", "overload_growth.png")
    plot_pilot(pilot, "REDUCED", "stable_convergence.png")
    plot_comparison(summary)
    if (FOLDER / "queue_states.csv").exists() and (FOLDER / "tasks.csv").exists():
        plot_sample()
    print(summary.to_string(index=False))
    print(pd.read_csv(FOLDER / "anova.csv", sep=";").to_string(index=False))


if __name__ == "__main__":
    main()
