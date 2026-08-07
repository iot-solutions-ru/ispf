-- StoreTwin v2: UI parity with app/src/data/mock.ts
ALTER TABLE st_equipment ADD COLUMN IF NOT EXISTS owner VARCHAR(16);
ALTER TABLE st_equipment ADD COLUMN IF NOT EXISTS dim_l DOUBLE PRECISION;
ALTER TABLE st_equipment ADD COLUMN IF NOT EXISTS dim_w DOUBLE PRECISION;
ALTER TABLE st_equipment ADD COLUMN IF NOT EXISTS dim_h DOUBLE PRECISION;
ALTER TABLE st_equipment ADD COLUMN IF NOT EXISTS rotation DOUBLE PRECISION DEFAULT 0;
ALTER TABLE st_planogram ADD COLUMN IF NOT EXISTS export_name VARCHAR(256);
ALTER TABLE st_planogram ADD COLUMN IF NOT EXISTS folder_path VARCHAR(512);
ALTER TABLE st_planogram ADD COLUMN IF NOT EXISTS author VARCHAR(128);
ALTER TABLE st_planogram ADD COLUMN IF NOT EXISTS version_label VARCHAR(32);
ALTER TABLE st_planogram ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
ALTER TABLE st_shelf_space ADD COLUMN IF NOT EXISTS faces INTEGER DEFAULT 0;
ALTER TABLE st_shelf_space ADD COLUMN IF NOT EXISTS equipment_ids VARCHAR(512);
ALTER TABLE st_incident ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
ALTER TABLE st_incident ADD COLUMN IF NOT EXISTS resolution VARCHAR(1024);
ALTER TABLE st_incident ADD COLUMN IF NOT EXISTS linked_task_id VARCHAR(64);
ALTER TABLE st_task ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
ALTER TABLE st_task ADD COLUMN IF NOT EXISTS reporter VARCHAR(128);
ALTER TABLE st_task ADD COLUMN IF NOT EXISTS linked_incident_id VARCHAR(64);
ALTER TABLE st_task ADD COLUMN IF NOT EXISTS result_note VARCHAR(1024);
ALTER TABLE st_integration ADD COLUMN IF NOT EXISTS last_sync_at TIMESTAMPTZ;
CREATE TABLE IF NOT EXISTS st_planogram_shelf (
  id VARCHAR(64) PRIMARY KEY,
  planogram_id VARCHAR(64) NOT NULL,
  level INTEGER NOT NULL,
  height_cm DOUBLE PRECISION,
  capacity_cm DOUBLE PRECISION
);
CREATE TABLE IF NOT EXISTS st_planogram_sku (
  id VARCHAR(64) PRIMARY KEY,
  planogram_id VARCHAR(64) NOT NULL,
  shelf_level INTEGER NOT NULL,
  name VARCHAR(256) NOT NULL,
  brand VARCHAR(128),
  barcode VARCHAR(64),
  faces INTEGER,
  actual_faces INTEGER,
  depth INTEGER,
  width_cm DOUBLE PRECISION,
  price DOUBLE PRECISION,
  color VARCHAR(32),
  status VARCHAR(32)
);
CREATE TABLE IF NOT EXISTS st_planogram_issue (
  id VARCHAR(64) PRIMARY KEY,
  planogram_id VARCHAR(64) NOT NULL,
  shelf_level INTEGER,
  severity VARCHAR(16),
  message VARCHAR(1024)
);
CREATE TABLE IF NOT EXISTS st_lifecycle_event (
  id VARCHAR(64) PRIMARY KEY,
  entity_type VARCHAR(16) NOT NULL,
  entity_id VARCHAR(64) NOT NULL,
  at_ts TIMESTAMPTZ NOT NULL,
  actor VARCHAR(128),
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  comment VARCHAR(1024)
);
CREATE TABLE IF NOT EXISTS st_kpi_point (
  id VARCHAR(64) PRIMARY KEY,
  store_id VARCHAR(64) NOT NULL,
  series VARCHAR(64) NOT NULL,
  label VARCHAR(64) NOT NULL,
  value DOUBLE PRECISION NOT NULL,
  sort_order INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS st_user (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  email VARCHAR(256) NOT NULL,
  role VARCHAR(32) NOT NULL,
  store_id VARCHAR(64)
);


DELETE FROM st_lifecycle_event;
DELETE FROM st_planogram_sku;
DELETE FROM st_planogram_shelf;
DELETE FROM st_planogram_issue;
DELETE FROM st_kpi_point;
DELETE FROM st_user;
DELETE FROM st_shelf_space;
DELETE FROM st_incident;
DELETE FROM st_task;
DELETE FROM st_planogram;
DELETE FROM st_equipment;
DELETE FROM st_zone;
DELETE FROM st_integration;
DELETE FROM st_store;

INSERT INTO st_store (id,name,address,area_m2) VALUES
  ('st-101','ТТ №101 · Центр','ул. Ленина, 12',420),
  ('st-214','ТТ №214 · Юг','пр. Мира, 88',380);
INSERT INTO st_zone (id,store_id,label,x,y,w,h) VALUES
  ('zone-dairy','st-101','Молочная / Бакалея',120,50,100,80),
  ('zone-cold','st-101','Холод / Заморозка',340,50,100,80),
  ('zone-dmp','st-101','ДМП',500,110,100,80),
  ('zone-checkout','st-101','Кассовая зона',500,270,100,80),
  ('zone-bakery','st-101','Пекарня',680,50,100,80);
INSERT INTO st_equipment (id,store_id,code,name,eq_type,status,brand,zone_label,category,shelf_meters,shelves,temperature,pos_x,pos_y,width,height,owner,dim_l,dim_w,dim_h,rotation) VALUES
  ('eq-r01','st-101','КЭШ-5','Стеллаж молочный КЭШ-5','rack','ok','MetalRack','Молочная','Молоко ТП',6.25,5,NULL,80,60,90,36,'own',1.25,0.6,2.1,0),
  ('eq-r02','st-101','СТ-12','Стеллаж бакалея СТ-12','rack','ok','MetalRack','Бакалея','Крупы',15,6,NULL,80,140,140,36,'own',2.5,0.55,2.2,0),
  ('eq-r03','st-101','СТ-18','Стеллаж напитки СТ-18','rack','warning','MetalRack','Напитки','Вода',10,5,NULL,80,220,120,36,'own',2,0.55,2.2,0),
  ('eq-f01','st-101','ХЛ-03','Холодильник открытый ХЛ-03','fridge','ok','ColdLine','Гастрономия','Колбасы',10,4,3.2,280,60,150,48,'own',2.5,0.9,2,0),
  ('eq-f02','st-101','МР-01','Морозильный ларь МР-01','freezer','critical','FrostPro','Заморозка','Мороженое',1.8,1,-12.4,280,160,110,50,'supplier',1.8,0.85,0.9,0),
  ('eq-d01','st-101','ДМП-П1','Паллета промо ДМП-П1','dmp','ok','PromoBase','Промо','Акция недели',2.4,2,NULL,460,120,70,55,'own',1.2,0.8,1.4,0),
  ('eq-d02','st-101','ДМП-С2','Стойка брендированная ДМП-С2','dmp','ok','PepsiCo','Промо','Напитки',2.4,4,NULL,560,120,40,70,'supplier',0.6,0.5,1.8,0),
  ('eq-c01','st-101','КСО-1','Касса самообслуживания 1','selfcheckout','ok','SelfPay','Кассы',NULL,0,0,NULL,480,280,45,45,'own',0.8,0.7,1.5,0),
  ('eq-c02','st-101','КСО-2','Касса самообслуживания 2','selfcheckout','warning','SelfPay','Кассы',NULL,0,0,NULL,540,280,45,45,'own',0.8,0.7,1.5,0),
  ('eq-k01','st-101','КАССА-1','Касса операторская 1','checkout','ok','RetailPOS','Кассы',NULL,0,0,NULL,420,280,50,55,'own',1.6,0.9,1.1,0),
  ('eq-o01','st-101','ПЕЧЬ-1','Конвекционная печь','oven','ok','BakeTech','Пекарня',NULL,0,0,185,640,60,50,50,'own',0.9,0.8,1.7,0),
  ('eq-cf01','st-101','КОФЕ-1','Кофейный аппарат','coffee','ok','BrewMax','Пекарня',NULL,0,0,NULL,710,60,36,40,'own',0.5,0.5,1.6,0),
  ('eq-cam1','st-101','CAM-A1','Камера аналитики A1','camera','ok','VisionAI','Зал',NULL,0,0,NULL,200,20,22,22,'own',0.12,0.12,0.2,0),
  ('eq-cam2','st-101','CAM-B2','Камера аналитики B2','camera','ok','VisionAI','Зал',NULL,0,0,NULL,520,20,22,22,'own',0.12,0.12,0.2,0);
INSERT INTO st_planogram (id,store_id,name,equipment_id,compliance,version,category,export_name,folder_path,author,version_label,updated_at) VALUES
  ('pg-1','st-101','Молоко ТП на КЭШ-5','eq-r01',84,1,'Молоко ТП','PLNG_Milk_TP_KESH5_v12','Планограммы / Молочная / Молоко ТП / КЭШ-5','Отдел ПЛНГ','12.4',TIMESTAMPTZ '2026-08-04T09:20:00+03'),
  ('pg-2','st-101','Колбасы · ХЛ-03','eq-f01',78,1,'Колбасы','PLNG_Sausage_HL03_v08','Планограммы / Гастрономия / Колбасы / ХЛ-03','Отдел ПЛНГ','8.1',TIMESTAMPTZ '2026-08-05T14:10:00+03'),
  ('pg-3','st-101','Промо паллета · ДМП-П1','eq-d01',100,1,'Акция недели','DMP_Promo_Week32_P1','Планограммы / ДМП / Акция недели / Паллета-1','Отдел ДМП','3.0',TIMESTAMPTZ '2026-08-06T08:00:00+03'),
  ('pg-4','st-101','Бакалея крупы · СТ-12','eq-r02',96,1,'Крупы','PLNG_Grocery_ST12_v05','Планограммы / Бакалея / Крупы / СТ-12','Отдел ПЛНГ','5.2',TIMESTAMPTZ '2026-08-03T11:40:00+03');
INSERT INTO st_planogram_shelf (id,planogram_id,level,height_cm,capacity_cm) VALUES
  ('pg-1-s5','pg-1',5,35,125),
  ('pg-1-s4','pg-1',4,32,125),
  ('pg-1-s3','pg-1',3,30,125),
  ('pg-1-s2','pg-1',2,28,125),
  ('pg-1-s1','pg-1',1,26,125),
  ('pg-2-s4','pg-2',4,28,250),
  ('pg-2-s3','pg-2',3,28,250),
  ('pg-2-s2','pg-2',2,26,250),
  ('pg-2-s1','pg-2',1,24,250),
  ('pg-3-s2','pg-3',2,40,120),
  ('pg-3-s1','pg-3',1,45,120),
  ('pg-4-s6','pg-4',6,30,250),
  ('pg-4-s5','pg-4',5,30,250),
  ('pg-4-s4','pg-4',4,28,250),
  ('pg-4-s3','pg-4',3,28,250),
  ('pg-4-s2','pg-4',2,26,250),
  ('pg-4-s1','pg-4',1,24,250);
INSERT INTO st_planogram_sku (id,planogram_id,shelf_level,name,brand,barcode,faces,actual_faces,depth,width_cm,price,color,status) VALUES
  ('sku-m1','pg-1',5,'Молоко 3.2% 1л','Простоквашино','4607025390011',4,4,3,9.5,89.9,'#F2F0E4','ok'),
  ('sku-m2','pg-1',5,'Молоко 2.5% 1л','Домик в деревне','4607025390142',3,2,3,9.5,84.5,'#E8F4FC','missing'),
  ('sku-k1','pg-1',4,'Кефир 1% 0.9л','Простоквашино','4607025391018',3,3,2,9,72,'#FCE8E8','ok'),
  ('sku-k2','pg-1',4,'Ряженка 4% 0.45л','Вкуснотеево','4607025391186',2,2,2,8,54,'#FDF2DC','ok'),
  ('sku-k3','pg-1',4,'Снежок 0.4л','Кубанский','4607025391223',2,2,2,7.5,48,'#EAF8EF','ok'),
  ('sku-s1','pg-1',3,'Сметана 15% 300г','Простоквашино','4607025392015',4,4,2,9,69,'#FFF8E7','ok'),
  ('sku-s2','pg-1',3,'Творог 5% 200г','Домик в деревне','4607025392145',3,1,2,10,78,'#F5F0E8','wrong'),
  ('sku-y1','pg-1',2,'Йогурт питьевой 270г','Активиа','4607025393012',4,4,2,7,52,'#E6F7F2','ok'),
  ('sku-y2','pg-1',2,'Йогурт густой 120г','Чудо','4607025393081',3,3,2,7,39,'#FCE9F2','ok'),
  ('sku-b1','pg-1',1,'Масло сливочное 82% 180г','Вологодское','4607025394019',5,4,2,11,149,'#FFF3C4','low_stock'),
  ('sku-c1','pg-2',4,'Сервелат в/с 300г','Мираторг','4607025400017',3,3,2,14,289,'#F0D6D6','ok'),
  ('sku-c2','pg-2',4,'Салями Финская','Черкизово','4607025400086',2,2,2,12,319,'#E8C4C4','ok'),
  ('sku-c3','pg-2',3,'Докторская ГОСТ','Папа может','4607025401014',4,4,2,13,249,'#F8DEDE','ok'),
  ('sku-c4','pg-2',3,'Молочная варёная','Останкино','4607025401090',3,2,2,13,219,'#F5E6E6','missing'),
  ('sku-c5','pg-2',2,'Сосиски молочные','Мираторг','4607025402011',5,3,2,11,189,'#FFE8D6','missing'),
  ('sku-c6','pg-2',1,'Ветчина нарезка','Дымов','4607025403018',3,3,2,16,279,'#F2D4C8','ok'),
  ('sku-c7','pg-2',1,'Бекон сырокопчёный','Черкизово','4607025403087',2,3,2,14,349,'#E8C8B8','extra'),
  ('sku-p1','pg-3',2,'Чипсы 150г (акция)','Layʼs','4607025410014',8,8,4,12,99,'#FFE566','ok'),
  ('sku-p2','pg-3',1,'Напиток 0.5л (акция)','Pepsi','4607025411011',6,6,3,7,69,'#1B4F9C','ok'),
  ('sku-g1','pg-4',6,'Гречка ядрица 900г','Увелка','4607025420011',4,3,3,12,119,'#C4A574','low_stock'),
  ('sku-g2','pg-4',6,'Рис длиннозёрный 800г','Мистраль','4607025420080',3,3,3,12,129,'#F5F0E0','ok'),
  ('sku-g3','pg-4',5,'Овсянка Геркулес','Nordic','4607025421018',5,5,2,10,89,'#E8D9B8','ok'),
  ('sku-g4','pg-4',4,'Макароны спагетти','Shebekinskie','4607025422015',4,4,2,11,79,'#F0E4C8','ok'),
  ('sku-g5','pg-4',4,'Макароны перья','Макфа','4607025422084',3,3,2,11,72,'#EDE0C0','ok'),
  ('sku-g6','pg-4',3,'Мука пшеничная 2кг','Макфа','4607025423012',3,3,2,14,98,'#FAF6EE','ok'),
  ('sku-g7','pg-4',2,'Сахар песок 1кг','Русский','4607025424019',4,4,2,12,69,'#FFFFFF','ok'),
  ('sku-g8','pg-4',1,'Соль экстра 1кг','Полесье','4607025425016',3,3,2,10,35,'#F7F7F7','ok');
INSERT INTO st_planogram_issue (id,planogram_id,shelf_level,severity,message) VALUES
  ('iss-1','pg-1',5,'medium','Молоко 2.5% — факт 2 лица вместо 3 (видеоаналитика)'),
  ('iss-2','pg-1',3,'high','На месте творога обнаружен чужой SKU'),
  ('iss-3','pg-1',1,'low','Масло: глубина выкладки ниже эталона'),
  ('iss-4','pg-2',2,'high','Сосиски: лиц меньше эталона на 2'),
  ('iss-5','pg-2',4,'medium','Сервелат сдвинут от центрирования'),
  ('iss-6','pg-4',6,'low','Гречка: одно лицо пустое (низкий остаток)');
INSERT INTO st_shelf_space (id,store_id,category,meters,share_pct,faces,equipment_ids) VALUES
  ('ss-1','st-101','Молоко ТП',6.25,12.4,30,'eq-r01'),
  ('ss-2','st-101','Крупы',15,29.8,72,'eq-r02'),
  ('ss-3','st-101','Вода',10,19.9,48,'eq-r03'),
  ('ss-4','st-101','Колбасы',10,19.9,40,'eq-f01'),
  ('ss-5','st-101','Мороженое',1.8,3.6,12,'eq-f02'),
  ('ss-6','st-101','ДМП / Промо',4.8,9.5,28,'eq-d01,eq-d02'),
  ('ss-7','st-101','Прочее',2.4,4.9,10,'');
INSERT INTO st_incident (id,store_id,title,equipment_id,severity,status,assignee,reporter,source,created_at,sla_due_at,updated_at,resolution,linked_task_id) VALUES
  ('inc-1','st-101','Температура морозильника выше нормы (−12.4 °C)','eq-f02','high','in_progress','С. Орлов','Шлюз телеметрии','iot',TIMESTAMPTZ '2026-08-06T16:42:00+03',TIMESTAMPTZ '2026-08-06T18:42:00+03',TIMESTAMPTZ '2026-08-06T17:05:00+03',NULL,'t-3'),
  ('inc-2','st-101','Отклонение выкладки от планограммы · КЭШ-5 (видеоаналитика)','eq-r01','medium','assigned','И. Петров','Движок видеоаналитики','cv',TIMESTAMPTZ '2026-08-06T15:10:00+03',TIMESTAMPTZ '2026-08-06T19:10:00+03',TIMESTAMPTZ '2026-08-06T15:20:00+03',NULL,'t-1'),
  ('inc-3','st-101','КСО-2: ошибка сканера штрихкода','eq-c02','high','waiting','С. Орлов','Шлюз телеметрии','iot',TIMESTAMPTZ '2026-08-06T14:55:00+03',TIMESTAMPTZ '2026-08-06T17:55:00+03',TIMESTAMPTZ '2026-08-06T16:10:00+03',NULL,NULL),
  ('inc-4','st-101','Низкий остаток SKU «Молоко 3.2%» на полке','eq-r01','low','new',NULL,'Касса / остатки','pos',TIMESTAMPTZ '2026-08-06T13:20:00+03',TIMESTAMPTZ '2026-08-06T21:20:00+03',TIMESTAMPTZ '2026-08-06T13:20:00+03',NULL,NULL),
  ('inc-5','st-101','ДМП-01: нет связи со шлюзом электронных ценников','eq-d01','medium','verification','С. Орлов',NULL,'iot',TIMESTAMPTZ '2026-08-06T11:00:00+03',TIMESTAMPTZ '2026-08-06T17:00:00+03',TIMESTAMPTZ '2026-08-06T16:30:00+03',NULL,NULL),
  ('inc-6','st-101','Ложное срабатывание датчика двери холодильника','eq-f01','low','closed','С. Орлов',NULL,'iot',TIMESTAMPTZ '2026-08-05T09:00:00+03',NULL,TIMESTAMPTZ '2026-08-05T11:30:00+03','Калибровка геркона, ложное срабатывание снято',NULL);
INSERT INTO st_task (id,store_id,title,task_type,status,priority,assignee,equipment_id,due_at,created_at,updated_at,reporter,linked_incident_id,result_note) VALUES
  ('t-1','st-101','Восстановить выкладку по планограмме КЭШ-5','planogram','in_progress','high','И. Петров','eq-r01',TIMESTAMPTZ '2026-08-06T18:00:00+03',TIMESTAMPTZ '2026-08-06T15:15:00+03',TIMESTAMPTZ '2026-08-06T15:45:00+03','Автореакция видеоаналитики','inc-2',NULL),
  ('t-2','st-101','Пополнить лица «Вода 5л» на СТ-18','restock','queued','normal','М. Сидорова','eq-r03',TIMESTAMPTZ '2026-08-06T19:00:00+03',TIMESTAMPTZ '2026-08-06T16:00:00+03',TIMESTAMPTZ '2026-08-06T16:05:00+03','Аналитика остатков',NULL,NULL),
  ('t-3','st-101','Сервисный выезд: морозильный ларь МР-01','service','accepted','high','С. Орлов','eq-f02',TIMESTAMPTZ '2026-08-06T20:00:00+03',TIMESTAMPTZ '2026-08-06T16:45:00+03',TIMESTAMPTZ '2026-08-06T17:00:00+03','Диспетчер ТО','inc-1',NULL),
  ('t-4','st-101','Разместить ДМП по акции «Выходные»','promo','done','normal','А. Ковалёва','eq-d01',TIMESTAMPTZ '2026-08-06T12:00:00+03',TIMESTAMPTZ '2026-08-06T09:00:00+03',TIMESTAMPTZ '2026-08-06T11:40:00+03',NULL,NULL,'ДМП размещён, фото в мобильном приложении'),
  ('t-5','st-101','Аудит полочного пространства бакалея','audit','overdue','normal','Отдел ПЛНГ','eq-r02',TIMESTAMPTZ '2026-08-05T17:00:00+03',TIMESTAMPTZ '2026-08-05T10:00:00+03',TIMESTAMPTZ '2026-08-05T17:01:00+03',NULL,NULL,NULL),
  ('t-6','st-101','Переставить промо-паллету у входа','promo','blocked','low','М. Сидорова','eq-d01',TIMESTAMPTZ '2026-08-06T21:00:00+03',TIMESTAMPTZ '2026-08-06T14:00:00+03',TIMESTAMPTZ '2026-08-06T15:30:00+03',NULL,NULL,NULL);
INSERT INTO st_lifecycle_event (id,entity_type,entity_id,at_ts,actor,from_status,to_status,comment) VALUES
  ('h1-1','incident','inc-1',TIMESTAMPTZ '2026-08-06T16:42:00+03','Система телеметрии',NULL,'new','Порог −15 °C превышен 8 мин подряд'),
  ('h1-2','incident','inc-1',TIMESTAMPTZ '2026-08-06T16:44:00+03','Автореакция','new','triage','Критичность → высокая, маршрут: сервис холодильного'),
  ('h1-3','incident','inc-1',TIMESTAMPTZ '2026-08-06T16:50:00+03','Диспетчер ТО','triage','assigned','Назначен С. Орлов · SLA 2 ч'),
  ('h1-4','incident','inc-1',TIMESTAMPTZ '2026-08-06T17:05:00+03','С. Орлов','assigned','in_progress','Диагностика компрессора на объекте'),
  ('h2-1','incident','inc-2',TIMESTAMPTZ '2026-08-06T15:10:00+03','Видеоаналитика',NULL,'new','Соответствие 72% · порог 85%'),
  ('h2-2','incident','inc-2',TIMESTAMPTZ '2026-08-06T15:12:00+03','Автореакция','new','triage',NULL),
  ('h2-3','incident','inc-2',TIMESTAMPTZ '2026-08-06T15:20:00+03','Старший смены','triage','assigned','Создана задача t-1 на выкладку'),
  ('h3-1','incident','inc-3',TIMESTAMPTZ '2026-08-06T14:55:00+03','Система телеметрии',NULL,'new','Ошибки сканера > 12 за 5 мин'),
  ('h3-2','incident','inc-3',TIMESTAMPTZ '2026-08-06T15:00:00+03','Диспетчер ТО','new','triage',NULL),
  ('h3-3','incident','inc-3',TIMESTAMPTZ '2026-08-06T15:10:00+03','Диспетчер ТО','triage','assigned','Назначен С. Орлов'),
  ('h3-4','incident','inc-3',TIMESTAMPTZ '2026-08-06T15:40:00+03','С. Орлов','assigned','in_progress','Замена сканирующей головки'),
  ('h3-5','incident','inc-3',TIMESTAMPTZ '2026-08-06T16:10:00+03','С. Орлов','in_progress','waiting','Ожидание ЗИП со склада РЦ · ориентир 19:00'),
  ('h4-1','incident','inc-4',TIMESTAMPTZ '2026-08-06T13:20:00+03','Коннектор касс',NULL,'new','Остаток на полке < 2 лица'),
  ('h5-1','incident','inc-5',TIMESTAMPTZ '2026-08-06T11:00:00+03','Система телеметрии',NULL,'new',NULL),
  ('h5-2','incident','inc-5',TIMESTAMPTZ '2026-08-06T11:15:00+03','Диспетчер ТО','new','assigned','Пропуск классификации — типовой инцидент электронных ценников'),
  ('h5-3','incident','inc-5',TIMESTAMPTZ '2026-08-06T12:00:00+03','С. Орлов','assigned','in_progress',NULL),
  ('h5-4','incident','inc-5',TIMESTAMPTZ '2026-08-06T16:30:00+03','С. Орлов','in_progress','verification','Перезапуск шлюза · ждём 15 мин стабильной связи'),
  ('h6-1','incident','inc-6',TIMESTAMPTZ '2026-08-05T09:00:00+03','Система телеметрии',NULL,'new',NULL),
  ('h6-2','incident','inc-6',TIMESTAMPTZ '2026-08-05T09:20:00+03','Диспетчер ТО','new','triage',NULL),
  ('h6-3','incident','inc-6',TIMESTAMPTZ '2026-08-05T10:00:00+03','С. Орлов','triage','in_progress',NULL),
  ('h6-4','incident','inc-6',TIMESTAMPTZ '2026-08-05T11:00:00+03','С. Орлов','in_progress','resolved','Калибровка выполнена'),
  ('h6-5','incident','inc-6',TIMESTAMPTZ '2026-08-05T11:30:00+03','Диспетчер ТО','resolved','closed','SLA выполнен'),
  ('th1-1','task','t-1',TIMESTAMPTZ '2026-08-06T15:15:00+03','Автореакция',NULL,'new','По инциденту inc-2'),
  ('th1-2','task','t-1',TIMESTAMPTZ '2026-08-06T15:18:00+03','Старший смены','new','queued',NULL),
  ('th1-3','task','t-1',TIMESTAMPTZ '2026-08-06T15:30:00+03','И. Петров','queued','accepted','Принято в мобильном приложении'),
  ('th1-4','task','t-1',TIMESTAMPTZ '2026-08-06T15:45:00+03','И. Петров','accepted','in_progress','Корректировка полки 2–3'),
  ('th2-1','task','t-2',TIMESTAMPTZ '2026-08-06T16:00:00+03','Аналитика',NULL,'new',NULL),
  ('th2-2','task','t-2',TIMESTAMPTZ '2026-08-06T16:05:00+03','Старший смены','new','queued','В план смены 16:00–20:00'),
  ('th3-1','task','t-3',TIMESTAMPTZ '2026-08-06T16:45:00+03','Диспетчер ТО',NULL,'new','По заявке inc-1'),
  ('th3-2','task','t-3',TIMESTAMPTZ '2026-08-06T16:50:00+03','Диспетчер ТО','new','queued',NULL),
  ('th3-3','task','t-3',TIMESTAMPTZ '2026-08-06T17:00:00+03','С. Орлов','queued','accepted',NULL),
  ('th4-1','task','t-4',TIMESTAMPTZ '2026-08-06T09:00:00+03','Мерчендайзинг',NULL,'new',NULL),
  ('th4-2','task','t-4',TIMESTAMPTZ '2026-08-06T09:10:00+03','Старший смены','new','queued',NULL),
  ('th4-3','task','t-4',TIMESTAMPTZ '2026-08-06T09:30:00+03','А. Ковалёва','queued','accepted',NULL),
  ('th4-4','task','t-4',TIMESTAMPTZ '2026-08-06T10:00:00+03','А. Ковалёва','accepted','in_progress',NULL),
  ('th4-5','task','t-4',TIMESTAMPTZ '2026-08-06T11:20:00+03','А. Ковалёва','in_progress','review','Фото отправлено на проверку'),
  ('th4-6','task','t-4',TIMESTAMPTZ '2026-08-06T11:40:00+03','Старший смены','review','done','Принято'),
  ('th5-1','task','t-5',TIMESTAMPTZ '2026-08-05T10:00:00+03','Аналитик',NULL,'new',NULL),
  ('th5-2','task','t-5',TIMESTAMPTZ '2026-08-05T10:30:00+03','Руководитель сети','new','queued',NULL),
  ('th5-3','task','t-5',TIMESTAMPTZ '2026-08-05T12:00:00+03','Отдел ПЛНГ','queued','accepted',NULL),
  ('th5-4','task','t-5',TIMESTAMPTZ '2026-08-05T17:01:00+03','Система SLA','accepted','overdue','Срок 17:00 истёк без старта работ'),
  ('th6-1','task','t-6',TIMESTAMPTZ '2026-08-06T14:00:00+03','Администратор',NULL,'new',NULL),
  ('th6-2','task','t-6',TIMESTAMPTZ '2026-08-06T14:20:00+03','М. Сидорова','new','queued',NULL),
  ('th6-3','task','t-6',TIMESTAMPTZ '2026-08-06T14:40:00+03','М. Сидорова','queued','accepted',NULL),
  ('th6-4','task','t-6',TIMESTAMPTZ '2026-08-06T15:00:00+03','М. Сидорова','accepted','in_progress',NULL),
  ('th6-5','task','t-6',TIMESTAMPTZ '2026-08-06T15:30:00+03','М. Сидорова','in_progress','blocked','Паллета не прибыла с РЦ');
INSERT INTO st_integration (id,name,protocol,health,latency_ms,endpoint,description,last_sync_at) VALUES
  ('erp','Учётная система','REST / JSON','online',120,'https://erp.retail.local/api/v1','Учёт товаров, заказы, справочник СЭС, статусы заявок',TIMESTAMPTZ '2026-08-06T18:55:00+03'),
  ('pos','Кассы','REST / JSON · XML','online',85,'https://pos.retail.local/stream','Продажи в реальном времени, остатки на кассах',TIMESTAMPTZ '2026-08-06T18:59:40+03'),
  ('sfa','Полевые задачи','REST / JSON','online',140,'https://sfa.retail.local/api','Задачи персонала, маршруты, учёт исполнения',TIMESTAMPTZ '2026-08-06T18:50:00+03'),
  ('iot','Платформа телеметрии','OPC UA','degraded',420,'opc.tcp://iot-gw.retail.local:4840','Телеметрия холодильников, печей, КСО; команды управления',TIMESTAMPTZ '2026-08-06T18:58:10+03'),
  ('scada','SCADA','OPC UA','online',95,'opc.tcp://scada.retail.local:4840','Двусторонний обмен телеметрией и командами',TIMESTAMPTZ '2026-08-06T18:59:00+03'),
  ('cv','Видеоаналитика','RTSP + REST','online',210,'https://cv.retail.local/api/v2','Сверка выкладки с планограммой, зоны интереса',TIMESTAMPTZ '2026-08-06T18:57:30+03'),
  ('bi','Бизнес-аналитика','CSV / REST','online',180,'https://bi.retail.local/export','Агрегированные отчёты и дашборды KPI',TIMESTAMPTZ '2026-08-06T18:00:00+03'),
  ('notify','Оповещения','Push / SMS / Email','online',60,'https://notify.retail.local/gateway','Уведомления сотрудникам и ответственным отделам',TIMESTAMPTZ '2026-08-06T18:59:55+03');
INSERT INTO st_kpi_point (id,store_id,series,label,value,sort_order) VALUES
  ('kpi-1','st-101','sales','Пн',420,1),
  ('kpi-2','st-101','sales','Вт',390,2),
  ('kpi-3','st-101','sales','Ср',455,3),
  ('kpi-4','st-101','sales','Чт',480,4),
  ('kpi-5','st-101','sales','Пт',610,5),
  ('kpi-6','st-101','sales','Сб',720,6),
  ('kpi-7','st-101','sales','Вс',680,7),
  ('kpi-8','st-101','traffic','09',45,8),
  ('kpi-9','st-101','traffic','11',120,9),
  ('kpi-10','st-101','traffic','13',180,10),
  ('kpi-11','st-101','traffic','15',160,11),
  ('kpi-12','st-101','traffic','17',210,12),
  ('kpi-13','st-101','traffic','19',240,13),
  ('kpi-14','st-101','traffic','21',90,14);
INSERT INTO st_user (id,name,email,role,store_id) VALUES
  ('u-1','Анна Ковалёва','a.kovaleva@retail.local','admin','st-101'),
  ('u-2','Иван Петров','i.petrov@retail.local','merchandiser','st-101'),
  ('u-3','Сергей Орлов','s.orlov@retail.local','service','st-101'),
  ('u-4','Мария Сидорова','m.sidorova@retail.local','floor','st-101'),
  ('u-5','Ольга Аналитик','o.analyst@retail.local','analyst','st-101'),
  ('u-6','Директор сети','director@retail.local','management','st-101');