-- Enable historian for SNMP rate variables derived from Counter32 bindings.
UPDATE object_variables
SET history_enabled = TRUE
WHERE name IN ('ifInOctetsRate', 'ifOutOctetsRate');
