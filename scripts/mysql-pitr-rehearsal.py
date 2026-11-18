#!/usr/bin/env python3
"""Compare a logical restore against position-based recovery on disposable MySQL servers."""

import hashlib
from functools import cache
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time

COMPOSE_FILE = Path(__file__).resolve().parents[1] / "infra/mysql/pitr/compose.yml"
DATABASE = "pitr_rehearsal"
BACKUP_POSITION = re.compile(
    rb"^-- CHANGE (?:REPLICATION SOURCE TO SOURCE|MASTER TO MASTER)_LOG_FILE="
    rb"'(mysql-bin\.\d+)', (?:SOURCE|MASTER)_LOG_POS=(\d+);\r?$", re.MULTILINE
)
EXPECTED = b"1\tbefore-backup\n2\tafter-backup\n3\tbefore-delete\n"


class InvalidReplay(ValueError):
    """A replay range or artifact failed preflight."""


@cache
def compose_command():
    for command in (("docker", "compose"), ("docker-compose",)):
        try:
            subprocess.run([*command, "version"], capture_output=True, check=True, timeout=10)
        except (FileNotFoundError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
            continue
        return command
    raise SystemExit("Docker Compose is required. Check `docker compose version` or `docker-compose version`.")


def compose(*args, data=None, timeout=120):
    return subprocess.run(
        [*compose_command(), "-f", str(COMPOSE_FILE), *args],
        input=data, stdout=subprocess.PIPE, check=True, timeout=timeout,
    ).stdout


def exec_mysql(service, *args, data=None):
    return compose("exec", "-T", service, "mysql", "-uroot", "--batch",
                   "--skip-column-names", *args, data=data)


def master_position():
    file, offset, *_ = exec_mysql("source", "-e", "SHOW MASTER STATUS").split(b"\t")
    return file.decode("ascii"), int(offset)


def dump_position(dump):
    match = BACKUP_POSITION.search(dump)
    if match is None:
        raise InvalidReplay("dump snapshot does not contain a supported binary log position")
    return match[1].decode("ascii"), int(match[2])


def prepare_replay(archive, digest, start, stop):
    if start[0] != stop[0] or archive.name != start[0] or not 4 <= start[1] < stop[1]:
        raise InvalidReplay("invalid or rotated binary log range")
    if not archive.is_file():
        raise InvalidReplay("binary log is missing")
    log_bytes = archive.read_bytes()
    if hashlib.sha256(log_bytes).digest() != digest:
        raise InvalidReplay("binary log checksum mismatch")
    if stop[1] >= len(log_bytes):
        raise InvalidReplay("binary log stop position is outside the archive")
    remote = "/tmp/pitr-input-binlog"
    compose("exec", "-T", "recovery", "sh", "-c", 'cat > "$1"', "sh", remote, data=log_bytes)
    decoded = compose("exec", "-T", "recovery", "mysqlbinlog",
                      "--verify-binlog-checksum", "--base64-output=DECODE-ROWS", "-v", remote)
    boundaries = {int(offset) for offset in re.findall(rb"^# at (\d+)\s*$", decoded, re.MULTILINE)}
    if not {start[1], stop[1]}.issubset(boundaries):
        raise InvalidReplay("replay positions must identify binary log event boundaries")
    return compose(
        "exec", "-T", "recovery", "mysqlbinlog", "--verify-binlog-checksum",
        f"--start-position={start[1]}", f"--stop-position={stop[1]}", remote,
    )


def restore(dump, replay=b""):
    exec_mysql("recovery", "-e", f"DROP DATABASE IF EXISTS {DATABASE}; CREATE DATABASE {DATABASE}")
    start = time.monotonic()
    exec_mysql("recovery", DATABASE, data=dump)
    if replay:
        exec_mysql("recovery", "--binary-mode", data=replay)
    rows = exec_mysql("recovery", DATABASE, "-e", "SELECT request_id, label FROM writes ORDER BY request_id")
    return rows, time.monotonic() - start


def require_rejected(label, action):
    try:
        action()
    except InvalidReplay as error:
        print(f"PASS {label}: {error}")
    else:
        raise AssertionError(f"{label} replay was accepted")


def run(scratch):
    compose("up", "-d", "--wait", "--wait-timeout", "90", timeout=600)
    try:
        version = compose("exec", "-T", "recovery", "mysqlbinlog", "--version")
    except subprocess.CalledProcessError as error:
        raise SystemExit("The recovery container cannot run mysqlbinlog. Check the image and platform.") from error
    print(f"Recovery tool: {version.decode().strip()}", flush=True)
    source_uuid = exec_mysql("source", "-e", "SELECT @@server_uuid")
    recovery_uuid = exec_mysql("recovery", "-e", "SELECT @@server_uuid")
    if not source_uuid.strip() or not recovery_uuid.strip() or source_uuid == recovery_uuid:
        raise RuntimeError("source and recovery server must be distinct")
    exec_mysql("source", "-e", f"CREATE DATABASE {DATABASE}")
    exec_mysql("source", DATABASE, data=b"""
        CREATE TABLE writes (request_id INT PRIMARY KEY, label VARCHAR(64) NOT NULL) ENGINE=InnoDB;
        INSERT INTO writes VALUES (1, 'before-backup');
    """)
    dump = compose("exec", "-T", "source", "mysqldump", "-uroot",
                   "--single-transaction", "--source-data=2", "--set-gtid-purged=OFF",
                   "--databases", DATABASE)
    start = dump_position(dump)
    for request_id, value in ((2, "after-backup"), (3, "before-delete")):
        exec_mysql("source", DATABASE, "-e",
                   f"START TRANSACTION; INSERT INTO writes VALUES ({request_id}, '{value}'); COMMIT")
    stop = master_position()
    exec_mysql("source", DATABASE, "-e", "START TRANSACTION; DELETE FROM writes WHERE request_id = 3; COMMIT")
    exec_mysql("source", DATABASE, "-e", "INSERT INTO writes VALUES (4, 'after-delete')")
    after_delete = master_position()
    if start[0] != stop[0] or after_delete[0] != stop[0] or stop[1] >= after_delete[1]:
        raise InvalidReplay("binary log rotated or the delete did not advance the log")
    damaged = exec_mysql("source", DATABASE, "-e", "SELECT request_id FROM writes ORDER BY request_id")
    if damaged != b"1\n2\n4\n":
        raise AssertionError(f"unexpected post-incident data: {damaged!r}")
    exec_mysql("source", "-e", "FLUSH BINARY LOGS")
    archive = scratch / start[0]
    # Read the mounted data directory from inside the container.
    log_bytes = compose("exec", "-T", "--user", "0", "source", "cat", f"/var/lib/mysql/{start[0]}")
    digest = hashlib.sha256(log_bytes).digest()
    archive.write_bytes(log_bytes)
    require_rejected("missing log", lambda: prepare_replay(scratch / "missing" / archive.name, digest, start, stop))
    require_rejected("invalid position", lambda: prepare_replay(archive, digest, start, start))
    require_rejected("position inside an event",
                     lambda: prepare_replay(archive, digest, start, (stop[0], stop[1] + 1)))
    require_rejected("different log", lambda: prepare_replay(archive, digest, start, ("mysql-bin.999999", stop[1])))
    corrupt = scratch / "corrupt" / archive.name
    corrupt.parent.mkdir()
    corrupt.write_bytes(archive.read_bytes()[:-1])
    require_rejected("corrupt log", lambda: prepare_replay(corrupt, digest, start, stop))
    baseline, baseline_seconds = restore(dump)
    replay_start = time.monotonic()
    replay = prepare_replay(archive, digest, start, stop)
    preparation = time.monotonic() - replay_start
    recovered, recovery_seconds = restore(dump, replay)
    if baseline != b"1\tbefore-backup\n" or recovered != EXPECTED:
        raise AssertionError(f"unexpected restore result: dump={baseline!r}, PITR={recovered!r}")
    print(f"Snapshot {start[0]}:{start[1]} -> delete begins at {stop[0]}:{stop[1]} (excluded)")
    print("Expected rows: request IDs 1, 2, 3. Request 4 committed after the cutoff.")
    print(f"Dump only  | recovered IDs 1      | missing 2 | {baseline_seconds:.3f}s")
    print(f"PITR       | recovered IDs 1, 2, 3 | missing 0 | {preparation + recovery_seconds:.3f}s")
    print("PASS request IDs and values, deleted row recovered, post-cutoff write excluded")


if __name__ == "__main__":
    print(f"Compose command: {' '.join(compose_command())}", flush=True)
    with tempfile.TemporaryDirectory(prefix="dbfleet-pitr-") as directory:
        os.environ["COMPOSE_PROJECT_NAME"] = Path(directory).name.lower().replace("_", "-")
        try:
            compose("ps", "--quiet")
        except subprocess.CalledProcessError as error:
            raise SystemExit("Docker Compose could not reach the Docker engine. Check `docker info`.") from error
        try:
            run(Path(directory))
        finally:
            compose("down", "--timeout", "5")
