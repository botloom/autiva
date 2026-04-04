// MongoDB 初始化脚本
db = db.getSiblingDB('autiva');

// 创建用户服务集合
db.createCollection('user_services');
db.user_services.createIndex({ "clientId": 1 });
db.user_services.createIndex({ "subdomain": 1 }, { unique: true });
db.user_services.createIndex({ "status": 1 });

// 创建服务配置集合
db.createCollection('service_configs');
db.service_configs.createIndex({ "serviceId": 1 });

// 创建运行时日志集合
db.createCollection('runtime_logs');
db.runtime_logs.createIndex({ "serviceId": 1, "timestamp": -1 });

// 创建文件存储元数据集合
db.createCollection('file_metadata');
db.file_metadata.createIndex({ "serviceId": 1, "path": 1 });
