-- V2: Datos semilla — roles, planes, restaurante y usuario demo

INSERT INTO roles (name, description) VALUES
    ('SUPER_ADMIN',       'Administrador global de la plataforma'),
    ('RESTAURANT_ADMIN',  'Administrador de un restaurante'),
    ('RESTAURANT_USER',   'Usuario operativo de un restaurante');

INSERT INTO plans (code, name, description, price_monthly, active) VALUES
    ('NEGOCODE', 'Plan NegoCode', 'Acceso total a la plataforma: menú digital ilimitado, código QR, recepción de pedidos en tiempo real y administración completa.', 49900, TRUE);

INSERT INTO restaurants (name, slug, description, phone, address, whatsapp, instagram, facebook, active) VALUES
    ('Frito Mix', 'fritomix', 'Hamburguesas, combos y bebidas en el corazón de la ciudad',
     '+57 300 000 0000', 'Calle 10 # 4-25, Bogotá', '+573000000000',
     'fritomix', 'fritomix', TRUE);

INSERT INTO users (restaurant_id, name, email, password, role_id, active) VALUES
    (NULL, 'Super Admin', 'superadmin@demo.com',
     '$2a$10$eRVFVgLel5VUEaiUKAMDBO9DyS5KV5UflBp0gXShEzkMLtMz8lztu',
     (SELECT id FROM roles WHERE name = 'SUPER_ADMIN'), TRUE),
    (1, 'Admin Frito Mix', 'admin@demo.com',
     '$2a$10$ZDAHmAnA88s3mny/r1Nrpemv0d8m1CPksrJDOQf.Fo7DMePZFJMou',
     (SELECT id FROM roles WHERE name = 'RESTAURANT_ADMIN'), TRUE);

INSERT INTO subscriptions (restaurant_id, plan_id, status) VALUES
    (1, (SELECT id FROM plans WHERE code = 'NEGOCODE'), 'ACTIVE');

INSERT INTO categories (restaurant_id, name, description, position) VALUES
    (1, 'Hamburguesas', 'Nuestras clásicas', 1),
    (1, 'Combos',       'Hamburguesa + papas + gaseosa', 2),
    (1, 'Bebidas',      'Refrescos y jugos', 3),
    (1, 'Postres',      'Para el antojo final', 4);

INSERT INTO products (restaurant_id, category_id, name, description, price, available, position) VALUES
    (1, 1, 'Hamburguesa Especial', 'Carne 180g, queso, lechuga, tomate y salsa de la casa', 18000, TRUE, 1),
    (1, 1, 'Hamburguesa Doble',    'Doble carne, doble queso y tocineta',                   24000, TRUE, 2),
    (1, 1, 'Hamburguesa BBQ',      'Carne, cebolla caramelizada y salsa BBQ ahumada',      21000, TRUE, 3),
    (1, 2, 'Combo Clásico',        'Hamburguesa Especial + papas + gaseosa 400ml',          26000, TRUE, 1),
    (1, 2, 'Combo Familiar',       '4 hamburguesas, papas grandes y 4 gaseosas',           72000, TRUE, 2),
    (1, 3, 'Gaseosa 400ml',        'Coca-Cola, Pepsi o Colombiana',                          4000, TRUE, 1),
    (1, 3, 'Jugo Natural',         'Mango, mora o lulo',                                     6000, TRUE, 2),
    (1, 4, 'Brownie con helado',   'Brownie tibio con helado de vainilla',                  9000, TRUE, 1);