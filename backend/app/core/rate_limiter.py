import time
from collections import defaultdict
from typing import Dict, List, Tuple
from fastapi import HTTPException, status


class InMemoryRateLimiter:
    """Sliding-window rate limiter and cooldown tracker for auth endpoints."""

    def __init__(self):
        # key -> list of unix timestamps
        self._request_history: Dict[str, List[float]] = defaultdict(list)
        # key -> last request timestamp
        self._last_request_time: Dict[str, float] = {}

    def check_resend_cooldown(self, key: str, cooldown_seconds: int) -> Tuple[bool, int]:
        """
        Checks if the request is within cooldown period.
        Returns (is_allowed, remaining_seconds).
        """
        now = time.time()
        last_time = self._last_request_time.get(key, 0.0)
        elapsed = now - last_time
        if elapsed < cooldown_seconds:
            remaining = int(cooldown_seconds - elapsed) + 1
            return False, remaining
        return True, 0

    def record_request(self, key: str):
        """Records a timestamp for cooldown and rate limit calculation."""
        now = time.time()
        self._last_request_time[key] = now
        self._request_history[key].append(now)

    def check_rate_limit(self, key: str, max_requests: int, window_seconds: int) -> Tuple[bool, int]:
        """
        Sliding-window rate limiter.
        Returns (is_allowed, remaining_quota).
        """
        now = time.time()
        cutoff = now - window_seconds
        # Clean older entries
        self._request_history[key] = [t for t in self._request_history[key] if t > cutoff]
        count = len(self._request_history[key])

        if count >= max_requests:
            return False, 0
        return True, max_requests - count

    def reset(self, key: str):
        """Resets rate limiting state for a key (e.g. after successful auth)."""
        self._request_history.pop(key, None)
        self._last_request_time.pop(key, None)


# Global rate limiter instances
email_code_limiter = InMemoryRateLimiter()
login_attempt_limiter = InMemoryRateLimiter()

