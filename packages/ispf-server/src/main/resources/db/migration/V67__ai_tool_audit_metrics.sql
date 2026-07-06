ALTER TABLE ai_tool_audit ADD COLUMN IF NOT EXISTS latency_ms BIGINT;
ALTER TABLE ai_tool_audit ADD COLUMN IF NOT EXISTS prompt_tokens INT;
ALTER TABLE ai_tool_audit ADD COLUMN IF NOT EXISTS completion_tokens INT;
ALTER TABLE ai_tool_audit ADD COLUMN IF NOT EXISTS turn_id VARCHAR(64);
ALTER TABLE ai_tool_audit ADD COLUMN IF NOT EXISTS step_no INT;
ALTER TABLE ai_tool_audit ADD COLUMN IF NOT EXISTS interaction_mode VARCHAR(16);
ALTER TABLE ai_tool_audit ADD COLUMN IF NOT EXISTS prompt_profile VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_ai_tool_audit_turn ON ai_tool_audit (app_id, turn_id, step_no);
