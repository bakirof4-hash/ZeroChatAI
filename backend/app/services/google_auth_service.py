import logging
from typing import Any, Dict, Optional
from fastapi import HTTPException, status
import jwt
from jwt.exceptions import ExpiredSignatureError, InvalidTokenError, PyJWKClientError
from app.core.config import settings

logger = logging.getLogger("zerochat.google_auth")

GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
GOOGLE_VALID_ISSUERS = ["accounts.google.com", "https://accounts.google.com"]

# Persistent JWKS client with internal key cache
_jwks_client: Optional[jwt.PyJWKClient] = None


def get_jwks_client() -> jwt.PyJWKClient:
    global _jwks_client
    if _jwks_client is None:
        _jwks_client = jwt.PyJWKClient(GOOGLE_JWKS_URL, cache_keys=True, max_cached_keys=10)
    return _jwks_client


class GoogleAuthService:
    """
    Secure Google Identity & OAuth 2.0 OpenID Connect verification service.
    
    Verifies:
    1. Cryptographic RSA-256 signature against official Google public JWKs.
    2. Token expiration (exp).
    3. Token issuer (iss in ['accounts.google.com', 'https://accounts.google.com']).
    4. Token audience (aud matches GOOGLE_CLIENT_ID if configured).
    5. Required identity claims: sub (Google ID), email, email_verified.
    """

    @classmethod
    def verify_id_token(cls, id_token: str) -> Dict[str, Any]:
        if not id_token or not id_token.strip():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Google ID token is required."
            )

        token = id_token.strip()

        # 1. Fetch Google signing key from JWKS
        try:
            client = get_jwks_client()
            signing_key = client.get_signing_key_from_jwt(token)
        except (PyJWKClientError, Exception) as e:
            logger.warning("Failed to retrieve Google JWK signing key: %s", e)
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid Google ID token: unable to verify cryptographic signature."
            )

        # 2. Decode and verify signature, issuer, and expiration
        expected_audience = settings.GOOGLE_CLIENT_ID.strip() if settings.GOOGLE_CLIENT_ID else None
        decode_options = {
            "verify_signature": True,
            "verify_exp": True,
            "verify_iss": True,
            "verify_aud": bool(expected_audience),
        }

        try:
            payload: Dict[str, Any] = jwt.decode(
                token,
                signing_key.key,
                algorithms=["RS256"],
                issuer=GOOGLE_VALID_ISSUERS,
                audience=expected_audience,
                options=decode_options
            )
        except ExpiredSignatureError:
            logger.warning("Google ID token has expired.")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Google authentication has expired. Please sign in again."
            )
        except InvalidTokenError as e:
            logger.warning("Google ID token validation failed: %s", e)
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Google token verification failed. Untrusted or invalid credentials."
            )
        except Exception as e:
            logger.error("Unexpected error verifying Google ID token: %s", e)
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Failed to authenticate with Google."
            )

        # 3. Validate essential claims
        google_id = payload.get("sub")
        email = payload.get("email")
        email_verified = payload.get("email_verified", False)

        if not google_id or not email:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Google ID token is missing required identity claims."
            )

        if not email_verified:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Google account email is unverified. Please verify your Google email address."
            )

        return {
            "google_id": str(google_id),
            "email": str(email).strip().lower(),
            "email_verified": bool(email_verified),
            "name": payload.get("name"),
            "picture": payload.get("picture"),
            "given_name": payload.get("given_name"),
            "family_name": payload.get("family_name")
        }
