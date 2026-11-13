import time
import logging
from pathlib import Path
import typer
from rich.console import Console
from rich.table import Table
from backend.config import settings, DEFAULT_DB_PATH
from backend.database import init_db, SessionLocal, engine
from backend.importer import run_sync_pipeline, lookup_phone_number
from backend.models import RegistryRange, RegistrySyncLog
from backend.seeders.vciom_seeder import seed_vciom_pools
from sqlalchemy import func, text

app = typer.Typer(help="AntiSpam Numbering Plan CLI")
console = Console()

@app.command()
def sync(
    force: bool = typer.Option(False, "--force", "-f", help="Force re-download of CSV files"),
    verbose: bool = typer.Option(False, "--verbose", "-v", help="Show detailed debug logs"),
    include_vciom: bool = typer.Option(False, "--include-vciom", help="Also seed official VCIOM survey pools into active blocks")
):
    """Download, parse, and synchronize official Mintsifry numbering plan into database."""
    log_level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(level=log_level, format="%(asctime)s [%(levelname)s] %(message)s")

    console.print("[bold cyan]Starting Russian Numbering Plan Synchronization...[/bold cyan]")
    with console.status("[bold green]Executing ETL pipeline (Download -> Staging -> Quality Gate -> Swap)..."):
        result = run_sync_pipeline(force_download=force)

    if result.get("status") == "SUCCESS":
        console.print("[bold green]Synchronization completed successfully![/bold green]")
        table = Table(title="Sync Summary")
        table.add_column("Metric", style="cyan")
        table.add_column("Value", style="magenta")
        table.add_row("Total Ranges", f"{result['total_records']:,}")
        table.add_row("Geographic (ABC)", f"{result['records_abc']:,}")
        table.add_row("Mobile / Non-geo (DEF)", f"{result['records_def']:,}")
        table.add_row("Duration (seconds)", f"{result['duration_seconds']:.2f}s")
        console.print(table)

        if include_vciom:
            console.print("[bold cyan]Seeding official VCIOM survey pools...[/bold cyan]")
            session = SessionLocal()
            try:
                vciom_res = seed_vciom_pools(session, fetch_web=True)
                console.print(f"[bold green]VCIOM Seeding Succeeded: {vciom_res['total_numbers_covered']} numbers blocked with category {vciom_res['category']}![/bold green]")
            finally:
                session.close()
    else:
        console.print(f"[bold red]Sync Failed: {result.get('error')}[/bold red]")
        raise typer.Exit(code=1)

@app.command(name="seed-vciom")
def seed_vciom(
    web: bool = typer.Option(True, "--web/--no-web", help="Attempt fetching from live wciom.ru portal"),
    verbose: bool = typer.Option(False, "--verbose", "-v", help="Show detailed debug logs")
):
    """Seed official VCIOM sociological survey pools into active blocks with category SURVEY_VCIOM."""
    log_level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(level=log_level, format="%(asctime)s [%(levelname)s] %(message)s")

    init_db()
    session = SessionLocal()
    try:
        console.print("[bold cyan]Seeding verified VCIOM sociological survey pools...[/bold cyan]")
        res = seed_vciom_pools(session, fetch_web=web)
        console.print(f"[bold green]Successfully seeded VCIOM pools![/bold green]")
        table = Table(title="VCIOM Seeding Summary")
        table.add_column("Metric", style="cyan")
        table.add_column("Value", style="green")
        table.add_row("Category", res["category"])
        table.add_row("Single Numbers Seeded", str(res["seeded_single_numbers"]))
        table.add_row("Micro-100 Pools Seeded", str(res["seeded_micro_pools"]))
        table.add_row("Total Numbers Covered", str(res["total_numbers_covered"]))
        console.print(table)
    finally:
        session.close()

@app.command()
def lookup(phone: str = typer.Argument(..., help="11-digit phone number, e.g. 74951234567 or +7 (495) 123-45-67")):
    """Lookup phone number operator, region, and pool info."""
    clean_digits = "".join(ch for ch in phone if ch.isdigit())
    if clean_digits.startswith("8") and len(clean_digits) == 11:
        clean_digits = "7" + clean_digits[1:]
    elif len(clean_digits) == 10:
        clean_digits = "7" + clean_digits

    if len(clean_digits) != 11:
        console.print(f"[red]Invalid phone number format: '{phone}'. Expected 11 digits (7XXXXXXXXXX).[/red]")
        raise typer.Exit(code=1)

    phone_num = int(clean_digits)
    t0 = time.perf_counter()
    info = lookup_phone_number(phone_num)
    elapsed_ms = (time.perf_counter() - t0) * 1000

    if not info:
        console.print(f"[yellow]Phone number {phone_num} not found in official numbering plan.[/yellow]")
        return

    table = Table(title=f"Lookup Result for +{phone_num} ({elapsed_ms:.2f} ms)")
    table.add_column("Field", style="cyan")
    table.add_column("Value", style="green")
    table.add_row("Code (ABC/DEF)", str(info["code"]))
    table.add_row("Range Start", str(info["range_start"]))
    table.add_row("Range End", str(info["range_end"]))
    table.add_row("Pool Capacity", f"{info['capacity']:,} numbers")
    table.add_row("Operator", info["operator_name"])
    table.add_row("Clean Operator", info["operator_clean"] or "-")
    table.add_row("Region / Territory", info["region"] or "-")
    table.add_row("INN", info["inn"] or "-")
    table.add_row("B2B / SIP Pool", "[bold red]YES[/bold red]" if info["is_b2b_sip"] else "No")
    console.print(table)

@app.command()
def stats():
    """Show database statistics, top operators, and SIP pool metrics."""
    init_db()
    session = SessionLocal()
    try:
        total_ranges = session.query(func.count(RegistryRange.id)).scalar() or 0
        if total_ranges == 0:
            console.print("[yellow]Database is empty. Run 'python -m backend.cli sync' first.[/yellow]")
            return

        total_capacity = session.query(func.sum(RegistryRange.capacity)).scalar() or 0
        sip_ranges = session.query(func.count(RegistryRange.id)).filter(RegistryRange.is_b2b_sip == True).scalar() or 0
        sip_capacity = session.query(func.sum(RegistryRange.capacity)).filter(RegistryRange.is_b2b_sip == True).scalar() or 0

        # Last sync
        last_sync = session.query(RegistrySyncLog).order_by(RegistrySyncLog.id.desc()).first()

        table = Table(title="AntiSpam Registry Statistics")
        table.add_column("Metric", style="cyan")
        table.add_column("Value", style="bold green")
        table.add_row("Total Registered Ranges", f"{total_ranges:,}")
        table.add_row("Total Assigned Phone Capacity", f"{total_capacity:,} numbers")
        table.add_row("Identified B2B/SIP Ranges", f"{sip_ranges:,}")
        table.add_row("B2B/SIP Phone Capacity", f"{sip_capacity:,} numbers")
        table.add_row("Database File", str(DEFAULT_DB_PATH))
        table.add_row("Database File Size", f"{(DEFAULT_DB_PATH.stat().st_size / (1024*1024)):.2f} MB" if DEFAULT_DB_PATH.exists() else "0 MB")
        if last_sync:
            table.add_row("Last Sync Status", last_sync.status)
            table.add_row("Last Sync Finished", str(last_sync.finished_at) if last_sync.finished_at else "-")
        console.print(table)

        # Top 10 Operators by range count
        top_ops = (
            session.query(
                RegistryRange.operator_clean,
                func.count(RegistryRange.id).label("ranges_count"),
                func.sum(RegistryRange.capacity).label("total_capacity")
            )
            .group_by(RegistryRange.operator_clean)
            .order_by(func.count(RegistryRange.id).desc())
            .limit(10)
            .all()
        )

        ops_table = Table(title="Top 10 Operators by Range Count")
        ops_table.add_column("Operator", style="cyan")
        ops_table.add_column("Ranges", style="yellow")
        ops_table.add_column("Total Number Capacity", style="green")
        for op in top_ops:
            ops_table.add_row(op[0] or "Unknown", f"{op[1]:,}", f"{op[2]:,}")
        console.print(ops_table)

    finally:
        session.close()

@app.command()
def export_android(
    output_path: str = typer.Option(
        "android_assets/antispam_registry_seed.db",
        "--output", "-o",
        help="Target output SQLite database file for Android app asset"
    )
):
    """
    Export an optimized, vacuumed SQLite database that can be bundled
    directly into Android app (assets) for instant zero-latency offline start.
    """
    out_file = Path(output_path)
    out_file.parent.mkdir(parents=True, exist_ok=True)
    if out_file.exists():
        out_file.unlink()

    console.print(f"[cyan]Exporting optimized SQLite database to {out_file}...[/cyan]")
    with engine.connect() as conn:
        conn.execute(text(f"VACUUM INTO '{out_file.as_posix()}';"))

    console.print(f"[bold green]Successfully exported Android seed database: {out_file} ({out_file.stat().st_size / (1024*1024):.2f} MB)[/bold green]")

@app.command()
def server(
    host: str = typer.Option("127.0.0.1", "--host", "-h", help="Bind host"),
    port: int = typer.Option(8000, "--port", "-p", help="Bind port"),
    reload: bool = typer.Option(False, "--reload", help="Enable auto-reload")
):
    """Run the AntiSpam FastAPI server with background workers."""
    import uvicorn
    console.print(f"[bold green]Starting AntiSpam REST API server on http://{host}:{port}...[/bold green]")
    uvicorn.run("backend.api.app:app", host=host, port=port, reload=reload)

@app.command()
def report(
    phone: str = typer.Argument(..., help="Phone number to report (e.g. 79031234567)"),
    device_hash: str = typer.Argument(..., help="Device hash string (e.g. dev1)"),
    category: str = typer.Option("SPAM", "--category", "-c", help="Category: SURVEY, SPAM, FINANCIAL, SILENT")
):
    """Submit a test spam/survey complaint."""
    from backend.clustering import evaluate_report, normalize_incoming_phone
    init_db()
    session = SessionLocal()
    try:
        norm = normalize_incoming_phone(phone)
        if not norm:
            console.print(f"[red]Invalid phone number format: '{phone}'[/red]")
            raise typer.Exit(code=1)
        res = evaluate_report(norm, device_hash, category, None, session)
        console.print(f"[cyan]Report Result:[/cyan] {res}")
    finally:
        session.close()

@app.command()
def blocks(
    active_only: bool = typer.Option(True, "--active/--all", help="Show only active blocks")
):
    """List blocked numbering ranges and their statuses."""
    from backend.models import ActiveBlock
    init_db()
    session = SessionLocal()
    try:
        q = session.query(ActiveBlock)
        if active_only:
            q = q.filter(ActiveBlock.status == "ACTIVE")
        items = q.order_by(ActiveBlock.id.desc()).all()

        table = Table(title=f"Blocked Number Pools ({len(items)} found)")
        table.add_column("Range Start", style="cyan")
        table.add_column("Range End", style="cyan")
        table.add_column("Type", style="yellow")
        table.add_column("Operator", style="green")
        table.add_column("Status", style="magenta")
        table.add_column("Expires At", style="white")
        table.add_column("Ver", style="blue")

        for b in items:
            table.add_row(
                str(b.range_start),
                str(b.range_end),
                b.block_type,
                b.operator_name or "-",
                b.status,
                str(b.expires_at),
                str(b.version)
            )
        console.print(table)
    finally:
        session.close()

@app.command()
def whitelist(
    toggle_group: str = typer.Option(None, "--toggle", "-t", help="Group key to toggle (e.g. TBANK)"),
    enable: bool = typer.Option(True, "--enable/--disable", help="Enable or disable the group")
):
    """View and toggle Server Whitelist organization groups."""
    from backend.whitelist import (
        seed_default_whitelist,
        get_whitelist_groups_summary,
        set_whitelist_group_status
    )
    init_db()
    session = SessionLocal()
    try:
        seed_default_whitelist(session)

        if toggle_group:
            updated = set_whitelist_group_status(toggle_group, enable, session)
            console.print(f"[bold green]Group '{toggle_group}' is_enabled set to {enable} (affected {updated} ranges)[/bold green]")

        groups = get_whitelist_groups_summary(session)
        table = Table(title="Server Whitelist Organization Groups")
        table.add_column("Group Key", style="cyan")
        table.add_column("Category", style="yellow")
        table.add_column("Status", style="bold")
        table.add_column("Ranges", style="magenta")
        table.add_column("Organizations Included", style="white")

        for g in groups:
            status_str = "[green]PROTECTED (ON)[/green]" if g["is_enabled"] else "[red]UNPROTECTED (OFF)[/red]"
            table.add_row(
                g["group_key"],
                g["category"],
                status_str,
                str(g["range_count"]),
                g["description"]
            )
        console.print(table)
    finally:
        session.close()

@app.command()
def export_ios(
    output: Path = typer.Option(Path("CallDirectoryNumbers.txt"), "--output", "-o", help="Output text file path"),
    limit: int = typer.Option(50_000, "--limit", "-l", help="Max phone numbers to export")
):
    """Export active blocked numbers for Apple iOS CallKit CXCallDirectoryExtension."""
    from backend.models import ActiveBlock
    init_db()
    session = SessionLocal()
    try:
        blocks = (
            session.query(ActiveBlock)
            .filter(ActiveBlock.status == "ACTIVE")
            .order_by(ActiveBlock.range_start.asc())
            .all()
        )
        numbers = []
        last_num = 0
        for b in blocks:
            span = (b.range_end - b.range_start) + 1
            if len(numbers) + span > limit:
                remaining = limit - len(numbers)
                span = max(0, remaining)
            curr = max(b.range_start, last_num + 1)
            target_end = min(b.range_end, curr + span - 1)
            while curr <= target_end and len(numbers) < limit:
                if curr > last_num:
                    numbers.append(curr)
                    last_num = curr
                curr += 1
            if len(numbers) >= limit:
                break

        output.parent.mkdir(parents=True, exist_ok=True)
        with open(output, "w", encoding="utf-8") as f:
            for num in numbers:
                f.write(f"{num}\n")

        console.print(f"[bold green]Exported {len(numbers):,} strictly ascending numbers for iOS to {output}[/bold green]")
    finally:
        session.close()

@app.command()
def ttl_cleanup():
    """Run one-shot TTL cleanup for expired blocks and report purging."""
    from backend.ttl_worker import run_ttl_cleanup
    init_db()
    session = SessionLocal()
    try:
        res = run_ttl_cleanup(session)
        console.print(f"[bold green]TTL cleanup completed:[/bold green] {res}")
    finally:
        session.close()

@app.command(name="export-aspb")
def export_aspb(
    output: Path = typer.Option(Path("data/pools.aspb"), "--output", "-o", help="Output path for binary ASPB file"),
    limit: int = typer.Option(500_000, "--limit", "-l", help="Max active blocks to include")
):
    """Compile and export active blocked pools into high-density binary ASPB format for CDN distribution."""
    from backend.aspb import generate_aspb_snapshot, parse_aspb_payload
    init_db()
    session = SessionLocal()
    try:
        data = generate_aspb_snapshot(session, limit=limit)
        output.parent.mkdir(parents=True, exist_ok=True)
        with open(output, "wb") as f:
            f.write(data)

        parsed = parse_aspb_payload(data)
        console.print(
            f"[bold green]Successfully exported {len(parsed['added']):,} pools to {output} "
            f"({len(data):,} bytes, delta v{parsed['current_version']})[/bold green]"
        )
    finally:
        session.close()

if __name__ == "__main__":
    app()
