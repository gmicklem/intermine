#!/usr/bin/env python3
"""Write a Markdown summary of the Gradle JUnit XML results below a directory.

Used by the GitHub Actions workflows: the output is appended to
$GITHUB_STEP_SUMMARY so a failed run shows which tests failed without having
to open the log. It only reports; it never fails the job (the test tasks and
config/lib/parse_test_report.py do that). Standard library only.

Usage: junit-summary.py <workspace_dir> [title]
"""

import os
import sys
import xml.etree.ElementTree as ET

MAX_FAILURES_LISTED = 50


def result_files(root):
    for dirpath, dirnames, filenames in os.walk(root):
        # Do not wander into sources or dependency trees.
        dirnames[:] = [d for d in dirnames if d not in (".git", "node_modules")]
        if not dirpath.endswith(os.path.join("build", "test-results", "test")):
            continue
        for name in sorted(filenames):
            if name.endswith(".xml") and not name.endswith("TestSuites.xml"):
                yield dirpath, os.path.join(dirpath, name)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    root = os.path.abspath(sys.argv[1])
    title = sys.argv[2] if len(sys.argv) > 2 else "Test results"

    per_project = {}
    failures = []
    for dirpath, path in result_files(root):
        project = os.path.relpath(dirpath, root).rsplit(
            os.sep + "build" + os.sep, 1
        )[0]
        totals = per_project.setdefault(project, [0, 0, 0, 0])
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError as e:
            failures.append((project, os.path.basename(path), f"unreadable XML: {e}"))
            continue
        totals[0] += int(suite.get("tests", 0))
        totals[1] += int(suite.get("failures", 0))
        totals[2] += int(suite.get("errors", 0))
        totals[3] += int(suite.get("skipped", 0))
        for case in suite.iter("testcase"):
            bad = case.find("failure")
            if bad is None:
                bad = case.find("error")
            if bad is not None:
                message = (bad.get("message") or bad.get("type") or "").strip()
                failures.append(
                    (
                        project,
                        f"{case.get('classname')}.{case.get('name')}",
                        message.splitlines()[0] if message else "",
                    )
                )

    print(f"### {title}\n")
    if not per_project:
        print("No JUnit XML results were found (the build probably failed "
              "before running any tests).")
        return 0

    sums = [sum(t[i] for t in per_project.values()) for i in range(4)]
    print(f"**{sums[0]} tests, {sums[1]} failures, {sums[2]} errors, "
          f"{sums[3]} skipped**\n")
    # Fold a long per-project table so the summary stays readable.
    fold = len(per_project) > 12
    if fold:
        print(f"<details><summary>{len(per_project)} projects</summary>\n")
    print("| Project | Tests | Failures | Errors | Skipped |")
    print("|---|---:|---:|---:|---:|")
    for project in sorted(per_project):
        t = per_project[project]
        print(f"| {project} | {t[0]} | {t[1]} | {t[2]} | {t[3]} |")
    if fold:
        print("\n</details>")

    if failures:
        print(f"\n#### Failed tests ({len(failures)})\n")
        for project, name, message in failures[:MAX_FAILURES_LISTED]:
            message = message.replace("|", "\\|")[:200]
            print(f"- `{project}`: `{name}` {message}")
        if len(failures) > MAX_FAILURES_LISTED:
            print(f"- ... and {len(failures) - MAX_FAILURES_LISTED} more "
                  "(see the uploaded JUnit XML)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
