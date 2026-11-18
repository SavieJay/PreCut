-- Paystack goes live (Phase 31, 18 November 2026): the function now has the live key
-- (PAYSTACK_MODE=live). Test payments made while testing never count in live.
update public.cloud_config set paystack_mode = 'live';
