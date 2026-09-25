-- GLIDY — schéma Supabase du carnet de vols (S12). À exécuter une fois dans SQL Editor du projet.
-- Principe : chaque pilote ne voit, n'écrit et ne supprime QUE ses propres lignes et fichiers (RLS).
-- L'application n'utilise que la clé publique « anon » + le jeton de session du pilote ; jamais la clé service_role.

-- 1. Profils pilotes (un par compte) -------------------------------------------------------------
create table if not exists public.profiles (
    id            uuid primary key references auth.users (id) on delete cascade,
    display_name  text,
    registration  text,                -- immatriculation du planeur habituel, ex. F-CPHI
    club          text,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

alter table public.profiles enable row level security;

create policy "profil : lecture par son propriétaire" on public.profiles
    for select using (auth.uid() = id);
create policy "profil : création par son propriétaire" on public.profiles
    for insert with check (auth.uid() = id);
create policy "profil : mise à jour par son propriétaire" on public.profiles
    for update using (auth.uid() = id) with check (auth.uid() = id);

-- profil créé automatiquement à l'inscription
create or replace function public.handle_new_user() returns trigger
    language plpgsql security definer set search_path = public as $$
begin
    insert into public.profiles (id) values (new.id) on conflict do nothing;
    return new;
end;
$$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
    for each row execute function public.handle_new_user();

-- 2. Vols (métadonnées ; le fichier IGC compressé est dans Storage) ------------------------------
create table if not exists public.flights (
    id               uuid primary key,                       -- identifiant local GLIDY (FlightId)
    user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
    sha256           text not null,                          -- empreinte du fichier IGC d'origine
    file_name        text not null,
    size_bytes       bigint not null,
    storage_path     text not null,                          -- <user_id>/<id>.igc.gz dans le bucket « igc »
    started_at       timestamptz,
    ended_at         timestamptz,
    duration_s       integer,
    distance_m       bigint,
    altitude_min_m   integer,
    altitude_max_m   integer,
    gain_m           integer,
    pilot            text,
    glider_type      text,
    glider_id        text,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint flights_user_sha_unique unique (user_id, sha256)
);
create index if not exists flights_user_started_idx on public.flights (user_id, started_at desc);

alter table public.flights enable row level security;

create policy "vols : lecture par leur pilote" on public.flights
    for select using (auth.uid() = user_id);
create policy "vols : ajout par leur pilote" on public.flights
    for insert with check (auth.uid() = user_id);
create policy "vols : mise à jour par leur pilote" on public.flights
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "vols : suppression par leur pilote" on public.flights
    for delete using (auth.uid() = user_id);

-- 3. Fichiers IGC (bucket privé « igc », un dossier par pilote) ----------------------------------
insert into storage.buckets (id, name, public, file_size_limit)
values ('igc', 'igc', false, 20971520)          -- 20 Mo max par fichier (un vol de 10 h compressé ≈ 0,5 Mo)
on conflict (id) do nothing;

create policy "igc : lecture de son dossier" on storage.objects
    for select using (bucket_id = 'igc' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "igc : dépôt dans son dossier" on storage.objects
    for insert with check (bucket_id = 'igc' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "igc : remplacement dans son dossier" on storage.objects
    for update using (bucket_id = 'igc' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "igc : suppression dans son dossier" on storage.objects
    for delete using (bucket_id = 'igc' and (storage.foldername(name))[1] = auth.uid()::text);

-- 4. Suppression de compte (exigence Google Play) ------------------------------------------------
-- L'app supprime d'abord les fichiers du dossier via l'API Storage, puis appelle cette fonction,
-- qui efface le compte ; les lignes profiles/flights partent en cascade.
create or replace function public.delete_my_account() returns void
    language plpgsql security definer set search_path = public as $$
begin
    if auth.uid() is null then
        raise exception 'non connecté';
    end if;
    delete from auth.users where id = auth.uid();
end;
$$;
revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
