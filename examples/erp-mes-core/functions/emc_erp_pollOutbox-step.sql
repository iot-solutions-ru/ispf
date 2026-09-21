UPDATE emc_erp_outbox SET status = 'ACKED', ack_code = 'ACCEPTED' WHERE status = 'IN_FLIGHT'
