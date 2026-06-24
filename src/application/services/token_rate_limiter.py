import json
import math
import os
import threading
from dataclasses import dataclass
from datetime import date
from typing import Any, Optional

from filelock import FileLock


class DailyTokenLimitExceeded(Exception):
    def __init__(self, user_id: str, daily_limit: int, used_tokens: int, requested_tokens: int):
        self.user_id = user_id
        self.daily_limit = daily_limit
        self.used_tokens = used_tokens
        self.requested_tokens = requested_tokens
        self.remaining_tokens = max(daily_limit - used_tokens, 0)
        super().__init__(
            f"Daily token limit exceeded for user '{user_id}'. "
            f"Limit: {daily_limit}, used: {used_tokens}, requested: {requested_tokens}, "
            f"remaining: {self.remaining_tokens}."
        )


@dataclass(frozen=True)
class TokenReservation:
    user_id: str
    day: str
    tokens: int


class DailyTokenRateLimiter:
    def __init__(self, daily_limit: int, usage_file: str):
        self.daily_limit = max(daily_limit, 0)
        self.usage_file = usage_file
        self.file_lock = FileLock(f"{usage_file}.lock")
        self.memory_lock = threading.Lock()

    @classmethod
    def from_environment(cls, data_directory: str) -> "DailyTokenRateLimiter":
        raw_limit = os.getenv("USER_DAILY_TOKEN_LIMIT", "0").strip()
        try:
            daily_limit = int(raw_limit) if raw_limit else 0
        except ValueError:
            raise ValueError("USER_DAILY_TOKEN_LIMIT must be an integer.")

        usage_file = os.getenv(
            "TOKEN_USAGE_FILE",
            os.path.join(data_directory, "logs", "daily_token_usage.json"),
        )
        return cls(daily_limit=daily_limit, usage_file=usage_file)

    @property
    def enabled(self) -> bool:
        return self.daily_limit > 0

    def ensure_available(self, user_id: str, requested_tokens: int = 1) -> None:
        if not self.enabled:
            return
        requested_tokens = max(requested_tokens, 1)
        used_tokens = self.get_used_tokens(user_id)
        if used_tokens + requested_tokens > self.daily_limit:
            raise DailyTokenLimitExceeded(
                user_id=user_id,
                daily_limit=self.daily_limit,
                used_tokens=used_tokens,
                requested_tokens=requested_tokens,
            )

    def reserve(self, user_id: Optional[str], tokens: int) -> Optional[TokenReservation]:
        if not self.enabled or not user_id:
            return None

        tokens = max(math.ceil(tokens), 1)
        day = self._current_day()
        with self.memory_lock:
            with self.file_lock:
                data = self._read_usage_unlocked()
                used_tokens = self._get_used_tokens(data, day, user_id)
                if used_tokens + tokens > self.daily_limit:
                    raise DailyTokenLimitExceeded(
                        user_id=user_id,
                        daily_limit=self.daily_limit,
                        used_tokens=used_tokens,
                        requested_tokens=tokens,
                    )
                self._set_used_tokens(data, day, user_id, used_tokens + tokens)
                self._write_usage_unlocked(data)
        return TokenReservation(user_id=user_id, day=day, tokens=tokens)

    def adjust(self, reservation: Optional[TokenReservation], actual_tokens: Optional[int]) -> None:
        if reservation is None or actual_tokens is None:
            return

        actual_tokens = max(math.ceil(actual_tokens), 0)
        delta = actual_tokens - reservation.tokens
        if delta == 0:
            return
        self._add_tokens(reservation.day, reservation.user_id, delta)

    def refund(self, reservation: Optional[TokenReservation]) -> None:
        if reservation is None:
            return
        self._add_tokens(reservation.day, reservation.user_id, -reservation.tokens)

    def get_used_tokens(self, user_id: str) -> int:
        if not self.enabled:
            return 0
        data = self._read_usage()
        return self._get_used_tokens(data, self._current_day(), user_id)

    def get_remaining_tokens(self, user_id: str) -> Optional[int]:
        if not self.enabled:
            return None
        return max(self.daily_limit - self.get_used_tokens(user_id), 0)

    def _add_tokens(self, day: str, user_id: str, tokens: int) -> None:
        with self.memory_lock:
            with self.file_lock:
                data = self._read_usage_unlocked()
                used_tokens = self._get_used_tokens(data, day, user_id)
                self._set_used_tokens(data, day, user_id, max(used_tokens + tokens, 0))
                self._write_usage_unlocked(data)

    def _read_usage(self) -> dict[str, dict[str, int]]:
        with self.file_lock:
            return self._read_usage_unlocked()

    def _write_usage(self, data: dict[str, dict[str, int]]) -> None:
        with self.file_lock:
            self._write_usage_unlocked(data)

    def _read_usage_unlocked(self) -> dict[str, dict[str, int]]:
        if not os.path.exists(self.usage_file):
            return {}
        with open(self.usage_file, "r", encoding="utf-8") as file:
            content = file.read().strip()
            if not content:
                return {}
            return json.loads(content)

    def _write_usage_unlocked(self, data: dict[str, dict[str, int]]) -> None:
        usage_directory = os.path.dirname(self.usage_file)
        if usage_directory:
            os.makedirs(usage_directory, exist_ok=True)
        temp_file = f"{self.usage_file}.tmp"
        with open(temp_file, "w", encoding="utf-8") as file:
            json.dump(data, file, ensure_ascii=False, indent=2, sort_keys=True)
        os.replace(temp_file, self.usage_file)

    def _get_used_tokens(self, data: dict[str, Any], day: str, user_id: str) -> int:
        return int(data.get(day, {}).get(user_id, 0))

    def _set_used_tokens(self, data: dict[str, dict[str, int]], day: str, user_id: str, tokens: int) -> None:
        data.setdefault(day, {})[user_id] = tokens

    def _current_day(self) -> str:
        return date.today().isoformat()
