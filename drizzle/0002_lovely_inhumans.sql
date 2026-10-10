CREATE TABLE `order_events` (
	`id` text PRIMARY KEY NOT NULL,
	`order_id` text NOT NULL,
	`kind` text NOT NULL,
	`payload` text NOT NULL,
	`created` text NOT NULL,
	`delivery_status` text DEFAULT 'pending' NOT NULL,
	`attempts` integer DEFAULT 0 NOT NULL,
	`next_attempt` text DEFAULT '' NOT NULL,
	`lease_until` text,
	`lease_token` text,
	`last_error` text,
	FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `events_order_created` ON `order_events` (`order_id`,`created`);--> statement-breakpoint
CREATE INDEX `events_delivery_due` ON `order_events` (`delivery_status`,`next_attempt`);