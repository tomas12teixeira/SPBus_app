create extension if not exists postgis;

create table if not exists public.buses (
  id text primary key,
  line_code text not null,
  lat double precision not null check (lat between -90 and 90),
  lng double precision not null check (lng between -180 and 180),
  speed double precision not null default 0 check (speed >= 0),
  location geography(Point, 4326),
  updated_at timestamptz not null default now()
);

create table if not exists public.bus_stops (
  id text primary key,
  name text not null,
  location geography(Point, 4326) not null,
  lat double precision not null check (lat between -90 and 90),
  lng double precision not null check (lng between -180 and 180),
  lines text[] not null default '{}'
);

create table if not exists public.feedbacks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete set null,
  line_code text not null,
  comment text not null check (char_length(comment) between 1 and 1000),
  status text not null check (status in ('Lotado', 'Atrasado', 'Normal', 'Interrupção', 'Outro')),
  created_at timestamptz not null default now()
);

create index if not exists buses_location_gix on public.buses using gist (location);
create index if not exists bus_stops_location_gix on public.bus_stops using gist (location);
create index if not exists feedbacks_line_created_idx on public.feedbacks (line_code, created_at desc);

create or replace function public.sync_bus_location()
returns trigger language plpgsql set search_path = public, extensions as $$
begin
  new.location := st_setsrid(st_makepoint(new.lng, new.lat), 4326)::geography;
  new.updated_at := now();
  return new;
end;
$$;

create or replace function public.sync_stop_location()
returns trigger language plpgsql set search_path = public, extensions as $$
begin
  new.location := st_setsrid(st_makepoint(new.lng, new.lat), 4326)::geography;
  return new;
end;
$$;

drop trigger if exists buses_sync_location on public.buses;
create trigger buses_sync_location before insert or update on public.buses for each row execute function public.sync_bus_location();
drop trigger if exists bus_stops_sync_location on public.bus_stops;
create trigger bus_stops_sync_location before insert or update of lat, lng on public.bus_stops for each row execute function public.sync_stop_location();

alter table public.buses enable row level security;
alter table public.bus_stops enable row level security;
alter table public.feedbacks enable row level security;

drop policy if exists "Public can read buses" on public.buses;
create policy "Public can read buses" on public.buses for select to anon, authenticated using (true);
drop policy if exists "Operator claim can manage buses" on public.buses;
create policy "Operator claim can manage buses" on public.buses for all to authenticated
using ((select auth.jwt() -> 'app_metadata' ->> 'role') = 'operator')
with check ((select auth.jwt() -> 'app_metadata' ->> 'role') = 'operator');
drop policy if exists "Public can read bus stops" on public.bus_stops;
create policy "Public can read bus stops" on public.bus_stops for select to anon, authenticated using (true);
drop policy if exists "Operator claim can manage bus stops" on public.bus_stops;
create policy "Operator claim can manage bus stops" on public.bus_stops for all to authenticated
using ((select auth.jwt() -> 'app_metadata' ->> 'role') = 'operator')
with check ((select auth.jwt() -> 'app_metadata' ->> 'role') = 'operator');
drop policy if exists "Public can read feedbacks" on public.feedbacks;
create policy "Public can read feedbacks" on public.feedbacks for select to anon, authenticated using (true);
drop policy if exists "Passengers can submit feedback" on public.feedbacks;
create policy "Passengers can submit feedback" on public.feedbacks for insert to anon, authenticated
with check (user_id is null or user_id = (select auth.uid()));
drop policy if exists "Authors can update own feedback" on public.feedbacks;
create policy "Authors can update own feedback" on public.feedbacks for update to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'buses') then
      alter publication supabase_realtime add table public.buses;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'feedbacks') then
      alter publication supabase_realtime add table public.feedbacks;
    end if;
  else
    raise notice 'Enable supabase_realtime on buses and feedbacks';
  end if;
end
$$;