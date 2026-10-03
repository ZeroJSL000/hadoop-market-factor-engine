"""Compare daily factor CSVs against locally supplied reference outputs."""

import argparse
import csv
import math
import re
import sys
from pathlib import Path


COLUMNS = ["tradeTime"] + [f"alpha_{index}" for index in range(1, 21)]
DATE_NAME = re.compile(r"(?:\d{4}|\d{8})")


class VerificationError(Exception):
    pass


def read_daily_csv(path):
    rows = {}
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != COLUMNS:
            raise VerificationError(f"Unexpected columns in {path}")
        for line_number, row in enumerate(reader, start=2):
            if None in row or any(value is None for value in row.values()):
                raise VerificationError(f"Malformed CSV row at {path}:{line_number}")
            time = row["tradeTime"]
            if not re.fullmatch(r"\d{6}", time):
                raise VerificationError(f"Invalid tradeTime at {path}:{line_number}")
            if time in rows:
                raise VerificationError(f"Duplicate tradeTime {time} in {path}")
            try:
                values = [float(row[column]) for column in COLUMNS[1:]]
            except ValueError as exception:
                raise VerificationError(f"Non-numeric factor at {path}:{line_number}") from exception
            if not all(math.isfinite(value) for value in values):
                raise VerificationError(f"Non-finite factor at {path}:{line_number}")
            rows[time] = values
    if not rows:
        raise VerificationError(f"No factor rows in {path}")
    return rows


def reference_path(reference_dir, day):
    candidates = [reference_dir / f"{day}.csv"]
    if len(day) == 8:
        candidates.append(reference_dir / f"{day[4:]}.csv")
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise VerificationError(f"No reference CSV for {day}")


def verify(reference_dir, actual_dir, days=None, threshold=0.01):
    if days is None:
        days = sorted(path.stem for path in actual_dir.glob("*.csv")
                      if DATE_NAME.fullmatch(path.stem))
    if not days:
        raise VerificationError("No daily CSV files selected")
    if threshold < 0:
        raise VerificationError("Threshold must be nonnegative")

    error_sums = [0.0] * 20
    observations = 0
    for day in days:
        if not DATE_NAME.fullmatch(day):
            raise VerificationError(f"Invalid day name: {day}")
        actual_path = actual_dir / f"{day}.csv"
        if not actual_path.is_file():
            raise VerificationError(f"No actual CSV for {day}")
        expected = read_daily_csv(reference_path(reference_dir, day))
        actual = read_daily_csv(actual_path)
        if expected.keys() != actual.keys():
            missing = len(expected.keys() - actual.keys())
            extra = len(actual.keys() - expected.keys())
            raise VerificationError(f"Timestamp mismatch for {day}: {missing} missing, {extra} extra")
        for time, expected_values in expected.items():
            actual_values = actual[time]
            for index in range(20):
                reference = expected_values[index]
                observed = actual_values[index]
                if reference == 0.0:
                    error = 0.0 if observed == 0.0 else math.inf
                else:
                    error = abs(observed - reference) / abs(reference)
                error_sums[index] += error
        observations += len(expected)
        print(f"{day}: {len(expected)} matching timestamps")

    means = [total / observations for total in error_sums]
    for index, mean in enumerate(means, start=1):
        print(f"alpha_{index:02d}: mean relative error {mean:.6%} "
              f"{'PASS' if mean <= threshold else 'FAIL'}")
    print(f"Compared {observations} timestamp rows across {len(days)} trading day(s).")
    return all(mean <= threshold for mean in means)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-dir", required=True, type=Path)
    parser.add_argument("--actual-dir", required=True, type=Path)
    parser.add_argument("--days", nargs="+", metavar="DATE")
    parser.add_argument("--threshold", type=float, default=0.01)
    arguments = parser.parse_args(argv)
    try:
        passed = verify(arguments.reference_dir, arguments.actual_dir,
                        arguments.days, arguments.threshold)
    except (OSError, VerificationError) as exception:
        print(f"Verification error: {exception}", file=sys.stderr)
        return 2
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
