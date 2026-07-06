-- Rewrite legacy application-scoped report paths in dashboard layouts (pre tree-first V33).
UPDATE object_variables
SET value_json = regexp_replace(
        value_json,
        'root\.platform\.applications\.[^.]+\.reports\.',
        'root.platform.reports.',
        'g'
    )
WHERE name = 'layout'
  AND value_json LIKE '%root.platform.applications.%reports.%';
