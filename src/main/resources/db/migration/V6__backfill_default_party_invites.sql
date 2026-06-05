insert into party_invite (
    created_at,
    updated_at,
    party_id,
    token,
    expires_at
)
select
    current_timestamp(6),
    current_timestamp(6),
    p.id,
    left(replace(uuid(), '-', ''), 16),
    date_add(p.started_at, interval 7 day)
from party p
where date_add(p.started_at, interval 7 day) > current_timestamp(6)
  and not exists (
      select 1
      from party_invite pi
      where pi.party_id = p.id
        and pi.expires_at > current_timestamp(6)
  );
