"""Synthetic tests for the daily CSV comparison command."""

import csv
import tempfile
import unittest
from pathlib import Path

from verify_outputs import COLUMNS, VerificationError, verify


class VerifyOutputsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.reference = self.root / "reference"
        self.actual = self.root / "actual"
        self.reference.mkdir()
        self.actual.mkdir()
        self.write(self.reference / "0102.csv", ["093000", "093003"])
        self.write(self.actual / "20240102.csv", ["093000", "093003"])

    def tearDown(self):
        self.temporary.cleanup()

    @staticmethod
    def write(path, times, first_factor=1.0):
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(COLUMNS)
            for time in times:
                writer.writerow([time, first_factor] + [index for index in range(2, 21)])

    def test_full_date_matches_course_month_day_filename(self):
        self.assertTrue(verify(self.reference, self.actual))

    def test_missing_timestamp_is_an_error(self):
        self.write(self.actual / "20240102.csv", ["093000"])
        with self.assertRaises(VerificationError):
            verify(self.reference, self.actual)

    def test_large_factor_error_fails_threshold(self):
        self.write(self.actual / "20240102.csv", ["093000", "093003"], 1.1)
        self.assertFalse(verify(self.reference, self.actual))

    def test_nonzero_value_against_zero_reference_fails(self):
        self.write(self.reference / "0102.csv", ["093000", "093003"], 0.0)
        self.assertFalse(verify(self.reference, self.actual))


if __name__ == "__main__":
    unittest.main()
