import logging
import os
import re
import time
from typing import Optional
import aiofiles
import httpx
from app.core.config import settings

logger = logging.getLogger("zerochat.storage")
UPLOAD_DIR = os.path.join(os.getcwd(), "uploads")
os.makedirs(UPLOAD_DIR, exist_ok=True)


class SupabaseStorageService:
    """
    Secure storage service supporting both Supabase Storage and local disk fallback.
    - Uses SUPABASE_SERVICE_ROLE_KEY strictly on the server backend.
    - Automatically creates buckets if allowed, or uploads with x-upsert.
    - Gracefully falls back to local uploads/ in development or when offline.
    """

    @classmethod
    def is_configured(cls) -> bool:
        return bool(settings.SUPABASE_URL and (settings.SUPABASE_SERVICE_ROLE_KEY or settings.SUPABASE_ANON_KEY))

    @classmethod
    async def upload_file(
        cls,
        filename: str,
        content: bytes,
        content_type: str = "application/octet-stream"
    ) -> str:
        safe_name = re.sub(r"[^a-zA-Z0-9_.-]", "_", filename)
        unique_filename = f"{int(time.time())}_{safe_name}"

        # 1. Attempt Supabase Storage upload if credentials are provided
        if cls.is_configured():
            supabase_url = settings.SUPABASE_URL.rstrip("/")
            bucket = settings.SUPABASE_STORAGE_BUCKET or "zerochat-attachments"
            api_key = settings.SUPABASE_SERVICE_ROLE_KEY or settings.SUPABASE_ANON_KEY

            endpoint = f"{supabase_url}/storage/v1/object/{bucket}/{unique_filename}"
            headers = {
                "Authorization": f"Bearer {api_key}",
                "apikey": api_key,
                "Content-Type": content_type,
                "x-upsert": "true"
            }

            try:
                async with httpx.AsyncClient(timeout=30.0) as client:
                    resp = await client.post(endpoint, content=content, headers=headers)
                    if resp.status_code in [200, 201]:
                        public_url = f"{supabase_url}/storage/v1/object/public/{bucket}/{unique_filename}"
                        logger.info("Successfully uploaded file to Supabase Storage: %s", public_url)
                        return public_url
                    else:
                        logger.warning(
                            "Supabase Storage returned status %s: %s. Falling back to local storage.",
                            resp.status_code,
                            resp.text
                        )
            except Exception as e:
                logger.error("Error communicating with Supabase Storage: %s. Falling back to local storage.", str(e))

        # 2. Local disk fallback
        local_path = os.path.join(UPLOAD_DIR, unique_filename)
        async with aiofiles.open(local_path, "wb") as f:
            await f.write(content)

        return f"/uploads/{unique_filename}"

