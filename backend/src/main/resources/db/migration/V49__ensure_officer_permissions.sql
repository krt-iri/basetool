-- Ensure MISSION_MANAGE permission for Officer and Admin roles
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'MISSION_MANAGE' FROM role WHERE name = 'Officer'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'MISSION_MANAGE' FROM role WHERE name = 'Admin'
ON CONFLICT DO NOTHING;

-- Ensure ROLE_MANAGE permission for Admin role
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ROLE_MANAGE' FROM role WHERE name = 'Admin'
ON CONFLICT DO NOTHING;

-- Ensure USER_MANAGE permission for Officer role
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'USER_MANAGE' FROM role WHERE name = 'Officer'
ON CONFLICT DO NOTHING;
