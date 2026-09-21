SELECT verb, noun, direction, COALESCE(description, '') AS description FROM emc_erp_transaction_profile ORDER BY direction, verb, noun
