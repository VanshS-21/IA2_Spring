CREATE DATABASE IF NOT EXISTS user_db; CREATE DATABASE IF NOT EXISTS food_db; CREATE DATABASE IF NOT EXISTS order_db; CREATE DATABASE IF NOT EXISTS payment_db; CREATE DATABASE IF NOT EXISTS notification_db;
CREATE USER IF NOT EXISTS 'user_user'@'%' IDENTIFIED BY 'user_pass'; GRANT ALL ON user_db.* TO 'user_user'@'%';
CREATE USER IF NOT EXISTS 'food_user'@'%' IDENTIFIED BY 'food_pass'; GRANT ALL ON food_db.* TO 'food_user'@'%';
CREATE USER IF NOT EXISTS 'order_user'@'%' IDENTIFIED BY 'order_pass'; GRANT ALL ON order_db.* TO 'order_user'@'%';
CREATE USER IF NOT EXISTS 'payment_user'@'%' IDENTIFIED BY 'payment_pass'; GRANT ALL ON payment_db.* TO 'payment_user'@'%';
CREATE USER IF NOT EXISTS 'notification_user'@'%' IDENTIFIED BY 'notification_pass'; GRANT ALL ON notification_db.* TO 'notification_user'@'%'; FLUSH PRIVILEGES;
