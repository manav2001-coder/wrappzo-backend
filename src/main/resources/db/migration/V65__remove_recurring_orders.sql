-- Recurring ("repeat every month") orders are gone: drop the plans and the scheduled job's run history.
DROP TABLE IF EXISTS recurring_order_plans;
DELETE FROM job_runs WHERE job_name = 'recurring-orders';
