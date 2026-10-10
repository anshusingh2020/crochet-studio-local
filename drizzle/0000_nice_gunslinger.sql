CREATE TABLE `messages` (
	`id` text PRIMARY KEY NOT NULL,
	`order_id` text NOT NULL,
	`user_id` text NOT NULL,
	`role` text NOT NULL,
	`body` text NOT NULL,
	`created` text NOT NULL,
	FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `messages_order_created` ON `messages` (`order_id`,`created`);--> statement-breakpoint
CREATE TABLE `orders` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`email` text NOT NULL,
	`title` text NOT NULL,
	`details` text NOT NULL,
	`status` text DEFAULT 'requested' NOT NULL,
	`amount` integer,
	`file` text,
	`created` text NOT NULL
);
--> statement-breakpoint
CREATE INDEX `orders_user_created` ON `orders` (`user_id`,`created`);