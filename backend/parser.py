import csv
import logging
from pathlib import Path
from typing import Generator, Any
from backend.normalizer import normalize_range, detect_encoding
from backend.sip_classifier import clean_operator_name, is_b2b_sip_pool

logger = logging.getLogger(__name__)

def parse_registry_csv(filepath: Path) -> Generator[dict[str, Any], None, None]:
    """
    Stream rows from an official registry CSV file.
    Yields normalized dict records suitable for database insertion.
    """
    encoding = detect_encoding(filepath)
    logger.info(f"Parsing {filepath.name} using encoding: {encoding}")

    with open(filepath, "r", encoding=encoding, errors="replace") as f:
        reader = csv.reader(f, delimiter=";")
        try:
            headers = next(reader)
        except StopIteration:
            return

        # Clean header names (strip BOM, whitespace, quotes)
        cleaned_headers = [h.strip().strip("\ufeff").strip('"') for h in headers]

        for line_num, row in enumerate(reader, start=2):
            if not row or len(row) < 5:
                continue

            try:
                # Code ABC/DEF
                raw_code = row[0].strip()
                if not raw_code.isdigit():
                    continue
                code = int(raw_code)

                raw_start = row[1].strip()
                raw_end = row[2].strip()
                raw_capacity = row[3].strip()
                capacity = int(raw_capacity) if raw_capacity.isdigit() else 0

                raw_operator = row[4].strip().strip('"')

                # Region & Territory
                raw_region = row[5].strip().strip('"') if len(row) > 5 else ""
                territory = row[6].strip().strip('"') if len(row) > 6 else ""

                # Prefer territory if region is '-' or empty
                region = territory if (raw_region in ("-", "", "None") and territory) else raw_region

                inn = row[7].strip().strip('"') if len(row) > 7 else None
                if inn == "-" or not inn:
                    inn = None

                range_start, range_end = normalize_range(code, raw_start, raw_end)

                # Integrity check on capacity:
                calculated_capacity = range_end - range_start + 1
                if capacity <= 0 or capacity != calculated_capacity:
                    capacity = calculated_capacity

                operator_clean = clean_operator_name(raw_operator)
                is_b2b_sip = is_b2b_sip_pool(raw_operator, capacity, code)

                yield {
                    "code_abc_def": code,
                    "range_start": range_start,
                    "range_end": range_end,
                    "capacity": capacity,
                    "operator_name": raw_operator,
                    "operator_clean": operator_clean,
                    "region": region,
                    "inn": inn,
                    "is_b2b_sip": is_b2b_sip,
                }
            except Exception as e:
                logger.debug(f"Skipping malformed row {line_num} in {filepath.name}: {e}")
                continue
