-- Update only platform-provided names; keep customer content and historical records intact.
UPDATE venue SET location = REPLACE(location, '拾光工作室', '见微工作室')
WHERE location IN ('拾光工作室 · 1号拍摄区', '拾光工作室 · 2号拍摄区', '拾光工作室 · 3号拍摄区');
UPDATE sys_user SET display_name = REPLACE(display_name, '拾光', '见微')
WHERE display_name IN ('拾光接待', '拾光摄影师');
UPDATE employee SET name = REPLACE(name, '拾光', '见微')
WHERE name IN ('拾光接待', '拾光摄影师');
