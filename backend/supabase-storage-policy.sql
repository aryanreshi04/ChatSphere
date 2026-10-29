-- MANUAL REVIEW/APPLICATION ONLY. No bucket, policy or project is deployed by this file.
-- First create a PRIVATE chat-media bucket in the dashboard.
-- This restrictive policy denies direct anon/authenticated client access to this bucket
-- even if another permissive policy already exists. Other buckets retain their policies.
-- The backend's service_role bypasses RLS: guard it with Firebase verification/membership.
-- Change the literal bucket name only if SUPABASE_MEDIA_BUCKET was also changed.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND policyname='chatsphere_backend_only') THEN
    CREATE POLICY chatsphere_backend_only ON storage.objects AS RESTRICTIVE
      FOR ALL TO anon, authenticated
      USING (bucket_id <> 'chat-media') WITH CHECK (bucket_id <> 'chat-media');
  END IF;
END
$$;
