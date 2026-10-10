CREATE TRIGGER order_created_event AFTER INSERT ON orders BEGIN
INSERT INTO order_events (id,order_id,kind,payload,created) VALUES (lower(hex(randomblob(16))),NEW.id,'order.requested',json_object('status',NEW.status),strftime('%Y-%m-%dT%H:%M:%fZ','now'));
END;
--> statement-breakpoint
CREATE TRIGGER order_changed_event AFTER UPDATE OF status,amount,carrier,tracking,payment_ref ON orders WHEN OLD.status IS NOT NEW.status OR OLD.amount IS NOT NEW.amount OR OLD.carrier IS NOT NEW.carrier OR OLD.tracking IS NOT NEW.tracking OR OLD.payment_ref IS NOT NEW.payment_ref BEGIN
INSERT INTO order_events (id,order_id,kind,payload,created) VALUES (lower(hex(randomblob(16))),NEW.id,'order.'||NEW.status,json_object('status',NEW.status,'amountPaise',NEW.amount),strftime('%Y-%m-%dT%H:%M:%fZ','now'));
END;
--> statement-breakpoint
CREATE TRIGGER order_message_event AFTER INSERT ON messages BEGIN
INSERT INTO order_events (id,order_id,kind,payload,created) VALUES (lower(hex(randomblob(16))),NEW.order_id,'message.'||NEW.role,json_object('role',NEW.role,'messageId',NEW.id),strftime('%Y-%m-%dT%H:%M:%fZ','now'));
END;
