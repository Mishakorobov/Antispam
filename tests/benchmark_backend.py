import sys
from pathlib import Path

# Ensure backend package is discoverable
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import asyncio
import time
import random
from statistics import median
from fastapi.testclient import TestClient
from rich.console import Console
from rich.table import Table
from backend.api.app import app
from backend.database import init_db, SessionLocal
from backend.importer import lookup_phone_number
from backend.clustering import get_current_version

console = Console()

def run_local_screening_benchmark(iterations: int = 5_000) -> dict:
    """
    Stress-test local interval lookup across 453,882 registered ranges.
    Measures cold start latency and p50, p95, p99 distribution.
    """
    init_db()
    
    # Generate 5,000 test numbers across various Russian codes (903, 495, 800, etc.)
    rng = random.Random(42)
    test_numbers = []
    codes = [903, 495, 800, 916, 926, 960, 301, 812]
    for _ in range(iterations):
        c = rng.choice(codes)
        local_part = rng.randint(100_000, 9_999_999)
        test_numbers.append(70_000_000_000 + c * 10_000_000 + local_part)

    # Pre-warm DB connection as BootCompletedReceiver does on Android startup
    session = SessionLocal()

    # 1. Cold start query (first incoming call screening on initialized database)
    t_cold_start = time.perf_counter()
    _ = lookup_phone_number(test_numbers[0], session=session)
    cold_ms = (time.perf_counter() - t_cold_start) * 1000

    # 2. Sequential warm queries using active connection session (reused as in Room/Service)
    latencies_ms = []
    try:
        for num in test_numbers:
            t0 = time.perf_counter()
            _ = lookup_phone_number(num, session=session)
            latencies_ms.append((time.perf_counter() - t0) * 1000)
    finally:
        session.close()

    latencies_ms.sort()
    p50 = median(latencies_ms)
    p95 = latencies_ms[int(iterations * 0.95)]
    p99 = latencies_ms[int(iterations * 0.99)]
    max_lat = latencies_ms[-1]

    return {
        "iterations": iterations,
        "cold_start_ms": cold_ms,
        "p50_ms": p50,
        "p95_ms": p95,
        "p99_ms": p99,
        "max_ms": max_lat,
    }

def run_api_http_304_and_delta_benchmark(requests_count: int = 500) -> dict:
    """
    Benchmark FastAPI endpoint /api/v1/pools:
    Tests throughput and latency of HTTP 304 Not Modified vs delta payloads.
    """
    init_db()
    session = SessionLocal()
    cur_v = get_current_version(session)
    session.close()

    latencies_304 = []
    latencies_200 = []

    with TestClient(app) as client:
        # Test HTTP 304 (Client up-to-date)
        t_start_304 = time.perf_counter()
        for _ in range(requests_count):
            t0 = time.perf_counter()
            resp = client.get(f"/api/v1/pools?since={cur_v}")
            assert resp.status_code == 304
            latencies_304.append((time.perf_counter() - t0) * 1000)
        dur_304 = time.perf_counter() - t_start_304
        rps_304 = requests_count / dur_304

        # Test HTTP 200 (Delta sync)
        since_v = max(0, cur_v - 1)
        t_start_200 = time.perf_counter()
        for _ in range(requests_count):
            t0 = time.perf_counter()
            resp = client.get(f"/api/v1/pools?since={since_v}")
            assert resp.status_code == 200
            latencies_200.append((time.perf_counter() - t0) * 1000)
        dur_200 = time.perf_counter() - t_start_200
        rps_200 = requests_count / dur_200

    latencies_304.sort()
    latencies_200.sort()

    return {
        "rps_304": rps_304,
        "p50_304_ms": median(latencies_304),
        "p99_304_ms": latencies_304[int(requests_count * 0.99)],
        "rps_200": rps_200,
        "p50_200_ms": median(latencies_200),
        "p99_200_ms": latencies_200[int(requests_count * 0.99)],
    }

def main():
    console.print("\n[bold cyan]=== AntiSpam Stage 5: Performance & SLA Benchmark ===[/bold cyan]\n")

    # Part 1: Local Screening Lookup SLA
    with console.status("[bold green]Running Local Screening Benchmark (5,000 lookups)..."):
        res_lookup = run_local_screening_benchmark(5_000)

    table1 = Table(title="1. Local Screening SLA (< 15 ms target)")
    table1.add_column("Metric", style="cyan")
    table1.add_column("Result", style="bold green")
    table1.add_column("SLA Threshold", style="yellow")
    table1.add_column("Status", style="bold")

    table1.add_row(
        "Cold Start (1st query)",
        f"{res_lookup['cold_start_ms']:.3f} ms",
        "< 15.0 ms",
        "[green]PASSED[/green]" if res_lookup['cold_start_ms'] < 15.0 else "[red]FAILED[/red]"
    )
    table1.add_row(
        "Median Latency (p50)",
        f"{res_lookup['p50_ms']:.3f} ms",
        "< 1.0 ms",
        "[green]PASSED[/green]" if res_lookup['p50_ms'] < 1.0 else "[red]FAILED[/red]"
    )
    table1.add_row(
        "95th Percentile (p95)",
        f"{res_lookup['p95_ms']:.3f} ms",
        "< 2.0 ms",
        "[green]PASSED[/green]" if res_lookup['p95_ms'] < 2.0 else "[red]FAILED[/red]"
    )
    table1.add_row(
        "99th Percentile (p99)",
        f"{res_lookup['p99_ms']:.3f} ms",
        "< 5.0 ms",
        "[green]PASSED[/green]" if res_lookup['p99_ms'] < 5.0 else "[red]FAILED[/red]"
    )
    table1.add_row(
        "Max Latency",
        f"{res_lookup['max_ms']:.3f} ms",
        "< 15.0 ms",
        "[green]PASSED[/green]" if res_lookup['max_ms'] < 15.0 else "[red]FAILED[/red]"
    )
    console.print(table1)

    # Part 2: API Delta & HTTP 304 Throughput
    with console.status("[bold green]Running API HTTP 304 & Delta Benchmark (1,000 requests)..."):
        res_api = run_api_http_304_and_delta_benchmark(500)

    table2 = Table(title="2. API Delta Throughput & HTTP 304 (Zero Traffic)")
    table2.add_column("Scenario", style="cyan")
    table2.add_column("Throughput (RPS)", style="bold green")
    table2.add_column("Median Latency", style="magenta")
    table2.add_column("p99 Latency", style="yellow")

    table2.add_row(
        "HTTP 304 Not Modified (0 KB)",
        f"{res_api['rps_304']:.1f} req/s",
        f"{res_api['p50_304_ms']:.2f} ms",
        f"{res_api['p99_304_ms']:.2f} ms"
    )
    table2.add_row(
        "HTTP 200 Delta Sync",
        f"{res_api['rps_200']:.1f} req/s",
        f"{res_api['p50_200_ms']:.2f} ms",
        f"{res_api['p99_200_ms']:.2f} ms"
    )
    console.print(table2)

if __name__ == "__main__":
    main()
