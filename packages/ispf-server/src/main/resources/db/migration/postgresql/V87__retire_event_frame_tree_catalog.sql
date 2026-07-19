-- Retire decorative event-frame object-tree catalog (BL-208 runtime stays in platform_event_frames).
-- Frames are opened/closed via analytics REST + PG; no Explorer folder or instance-type blueprint.

DELETE FROM object_variables
WHERE object_path = 'root.platform.event-frames'
   OR object_path LIKE 'root.platform.event-frames.%'
   OR object_path = 'root.platform.instance-types.event-frame-v1';

DELETE FROM object_acl_entries
WHERE object_path = 'root.platform.event-frames'
   OR object_path LIKE 'root.platform.event-frames.%'
   OR object_path = 'root.platform.instance-types.event-frame-v1';

DELETE FROM object_nodes
WHERE path = 'root.platform.event-frames'
   OR path LIKE 'root.platform.event-frames.%'
   OR path = 'root.platform.instance-types.event-frame-v1'
   OR type IN ('EVENT_FRAMES', 'EVENT_FRAME')
   OR template_id = 'event-frame-v1';

DELETE FROM blueprint_definitions
WHERE name = 'event-frame-v1';
