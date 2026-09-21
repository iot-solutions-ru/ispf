SELECT domain, activity, status, COALESCE(note, '') AS note, COALESCE(ui_link, '') AS ui_link FROM emc_mom_activity ORDER BY domain, activity
