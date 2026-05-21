update users u
set name = null
from youth_welfare_pii.user_pii p
where u.user_key = p.user_key
  and u.name is not null
  and p.name_enc is not null
  and p.name_enc <> '';

update users u
set birth_date = null
from youth_welfare_pii.user_pii p
where u.user_key = p.user_key
  and u.birth_date is not null
  and p.birth_date_enc is not null
  and p.birth_date_enc <> '';
