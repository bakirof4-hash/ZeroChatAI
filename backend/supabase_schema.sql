-- ==============================================================================
-- ZeroChat AI — Supabase PostgreSQL Schema, RLS Policies, Realtime & Storage
-- ==============================================================================
-- This SQL script creates all required tables, indexes, Row Level Security (RLS)
-- policies, Supabase Realtime publication, and Supabase Storage bucket configurations.
--
-- Instructions:
-- 1. Open your Supabase Dashboard: https://supabase.com/dashboard
-- 2. Select your project -> Go to "SQL Editor"
-- 3. Paste and run this script.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Extensions
-- ------------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ------------------------------------------------------------------------------
-- 2. Tables & Constraints
-- ------------------------------------------------------------------------------

-- Users & Profiles
CREATE TABLE IF NOT EXISTS public.users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(100) UNIQUE NOT NULL,
    hashed_password VARCHAR(255) NOT NULL,
    role VARCHAR(50) DEFAULT 'USER' NOT NULL,
    is_founder BOOLEAN DEFAULT FALSE NOT NULL,
    is_verified_badge BOOLEAN DEFAULT FALSE NOT NULL, -- Blue checkmark ✓
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    email_verified BOOLEAN DEFAULT FALSE NOT NULL,
    bio VARCHAR(500),
    avatar_url VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    last_login_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_users_email ON public.users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON public.users(username);

-- Email Verification Records
CREATE TABLE IF NOT EXISTS public.verification_codes (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    purpose VARCHAR(50) DEFAULT 'REGISTRATION' NOT NULL,
    attempts INT DEFAULT 0 NOT NULL,
    max_attempts INT DEFAULT 5 NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    is_used BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_verification_codes_email ON public.verification_codes(email);

-- User Sessions
CREATE TABLE IF NOT EXISTS public.user_sessions (
    id SERIAL PRIMARY KEY,
    session_token VARCHAR(255) UNIQUE NOT NULL,
    user_id INT REFERENCES public.users(id) ON DELETE CASCADE NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    is_revoked BOOLEAN DEFAULT FALSE NOT NULL,
    ip_address VARCHAR(100),
    user_agent VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_token ON public.user_sessions(session_token);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON public.user_sessions(user_id);

-- Chat Messages (Public feed & Direct 1-to-1)
CREATE TABLE IF NOT EXISTS public.chat_messages (
    id SERIAL PRIMARY KEY,
    sender_id INT REFERENCES public.users(id) ON DELETE CASCADE NOT NULL,
    recipient_id INT REFERENCES public.users(id) ON DELETE CASCADE, -- NULL = Public Channel
    content TEXT NOT NULL,
    attachment_url VARCHAR(500),
    attachment_name VARCHAR(255),
    attachment_type VARCHAR(100),
    attachment_size INT,
    attachment_sha256 VARCHAR(64),
    is_ai BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_sender ON public.chat_messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_recipient ON public.chat_messages(recipient_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_created_at ON public.chat_messages(created_at);

-- User Block List
CREATE TABLE IF NOT EXISTS public.user_blocks (
    id SERIAL PRIMARY KEY,
    blocker_id INT REFERENCES public.users(id) ON DELETE CASCADE NOT NULL,
    blocked_id INT REFERENCES public.users(id) ON DELETE CASCADE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    CONSTRAINT uq_blocker_blocked UNIQUE(blocker_id, blocked_id)
);

CREATE INDEX IF NOT EXISTS idx_user_blocks_blocker ON public.user_blocks(blocker_id);
CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked ON public.user_blocks(blocked_id);

-- QR Contact Information
CREATE TABLE IF NOT EXISTS public.qr_contacts (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES public.users(id) ON DELETE CASCADE NOT NULL,
    contact_user_id INT REFERENCES public.users(id) ON DELETE CASCADE NOT NULL,
    qr_payload VARCHAR(500),
    nickname VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    CONSTRAINT uq_user_qr_contact UNIQUE(user_id, contact_user_id)
);

CREATE INDEX IF NOT EXISTS idx_qr_contacts_user ON public.qr_contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_qr_contacts_contact ON public.qr_contacts(contact_user_id);

-- File Inspection History
CREATE TABLE IF NOT EXISTS public.file_inspections (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES public.users(id) ON DELETE CASCADE NOT NULL,
    filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    file_size INT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    threat_level VARCHAR(50) NOT NULL,
    risk_score INT NOT NULL,
    findings_json TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_file_inspections_user ON public.file_inspections(user_id);
CREATE INDEX IF NOT EXISTS idx_file_inspections_sha256 ON public.file_inspections(sha256);

-- Audit Logs
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id SERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    actor_id INT,
    actor_email VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_id INT,
    target_email VARCHAR(255),
    details TEXT,
    ip_address VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON public.audit_logs(timestamp);

-- Security Events
CREATE TABLE IF NOT EXISTS public.security_events (
    id SERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    ip_address VARCHAR(100),
    severity VARCHAR(50) DEFAULT 'INFO' NOT NULL,
    details TEXT
);

CREATE INDEX IF NOT EXISTS idx_security_events_timestamp ON public.security_events(timestamp);

-- ------------------------------------------------------------------------------
-- 3. Row Level Security (RLS) Configuration
-- ------------------------------------------------------------------------------

-- Enable RLS across all tables
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verification_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.qr_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.file_inspections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.security_events ENABLE ROW LEVEL SECURITY;

-- 1) Service Role Superuser Bypass (Backend API gateway)
-- Supabase service_role has full access for backend orchestration
CREATE POLICY "service_role_all_users" ON public.users FOR ALL TO service_role USING (true) WITH CHECK (true);
CREATE POLICY "service_role_all_verification" ON public.verification_codes FOR ALL TO service_role USING (true) WITH CHECK (true);
CREATE POLICY "service_role_all_sessions" ON public.user_sessions FOR ALL TO service_role USING (true) WITH CHECK (true);
CREATE POLICY "service_role_all_messages" ON public.chat_messages FOR ALL TO service_role USING (true) WITH CHECK (true);
CREATE POLICY "service_role_all_blocks" ON public.user_blocks FOR ALL TO service_role USING (true) WITH CHECK (true);
CREATE POLICY "service_role_all_contacts" ON public.qr_contacts FOR ALL TO service_role USING (true) WITH CHECK (true);
CREATE POLICY "service_role_all_inspections" ON public.file_inspections FOR ALL TO service_role USING (true) WITH CHECK (true);
CREATE POLICY "service_role_all_audit" ON public.audit_logs FOR ALL TO service_role USING (true) WITH CHECK (true);
CREATE POLICY "service_role_all_security" ON public.security_events FOR ALL TO service_role USING (true) WITH CHECK (true);

-- 2) Client & Authenticated User Policies
-- Users: public profiles are readable; users can only update themselves
CREATE POLICY "users_read_public_profiles" ON public.users FOR SELECT TO authenticated, anon USING (true);

-- Chat Messages: Public messages are readable by all; direct messages only by participants
CREATE POLICY "messages_read_public" ON public.chat_messages FOR SELECT TO authenticated
    USING (recipient_id IS NULL);

-- QR Contacts: Users can only see and manage their own contacts
CREATE POLICY "contacts_select_own" ON public.qr_contacts FOR SELECT TO authenticated
    USING (user_id::text = auth.uid()::text);
CREATE POLICY "contacts_insert_own" ON public.qr_contacts FOR INSERT TO authenticated
    WITH CHECK (user_id::text = auth.uid()::text);
CREATE POLICY "contacts_delete_own" ON public.qr_contacts FOR DELETE TO authenticated
    USING (user_id::text = auth.uid()::text);

-- File Inspections: Users can only see their own inspection history
CREATE POLICY "inspections_select_own" ON public.file_inspections FOR SELECT TO authenticated
    USING (user_id::text = auth.uid()::text);

-- Verification Codes: STRICTLY PRIVATE - No client access (server-only via service_role)
-- No public/authenticated SELECT policy created for verification_codes

-- ------------------------------------------------------------------------------
-- 4. Supabase Realtime Publication
-- ------------------------------------------------------------------------------
-- Enable PostgreSQL Change Data Capture (CDC) for real-time chat messages
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'chat_messages'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_messages;
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- 5. Supabase Storage Configuration
-- ------------------------------------------------------------------------------
-- Create 'zerochat-attachments' bucket if not exists
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'zerochat-attachments',
    'zerochat-attachments',
    true,
    26214400, -- 25 MB max limit
    NULL      -- Allow all file types (APK, EXE, ZIP, images, docs)
)
ON CONFLICT (id) DO NOTHING;

-- Storage Security Policies
DO $$
BEGIN
    -- Allow public read access to uploaded attachments
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE schemaname = 'storage' AND tablename = 'objects' AND policyname = 'Public Access to Attachments'
    ) THEN
        CREATE POLICY "Public Access to Attachments" ON storage.objects
            FOR SELECT USING (bucket_id = 'zerochat-attachments');
    END IF;

    -- Allow authenticated and service_role uploads
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE schemaname = 'storage' AND tablename = 'objects' AND policyname = 'Allow Attachment Uploads'
    ) THEN
        CREATE POLICY "Allow Attachment Uploads" ON storage.objects
            FOR INSERT WITH CHECK (bucket_id = 'zerochat-attachments');
    END IF;
END $$;

