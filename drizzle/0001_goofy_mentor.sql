CREATE TABLE `request_limits` (
	`key` text PRIMARY KEY NOT NULL,
	`count` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `products` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`description` text NOT NULL,
	`category` text NOT NULL,
	`price` integer NOT NULL,
	`stock` integer NOT NULL,
	`image` text,
	`colour` text NOT NULL,
	`published` integer DEFAULT 0 NOT NULL,
	`created` text NOT NULL
);
--> statement-breakpoint
CREATE INDEX `products_published` ON `products` (`published`,`created`);--> statement-breakpoint
CREATE TABLE `profiles` (
	`user_id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`phone` text NOT NULL,
	`line1` text NOT NULL,
	`line2` text NOT NULL,
	`city` text NOT NULL,
	`state` text NOT NULL,
	`pincode` text NOT NULL,
	`updated` text NOT NULL
);
--> statement-breakpoint
ALTER TABLE `orders` ADD `shipping` text;--> statement-breakpoint
ALTER TABLE `orders` ADD `payment_ref` text;--> statement-breakpoint
ALTER TABLE `orders` ADD `carrier` text;--> statement-breakpoint
ALTER TABLE `orders` ADD `tracking` text;