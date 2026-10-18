-- Tabela de músicas
create table public.songs (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  file_url text not null,
  cover_url text,
  created_at timestamptz not null default now()
);

alter table public.songs enable row level security;

-- Qualquer pessoa pode ler (app do usuário)
create policy "songs_read_all" on public.songs
  for select using (true);

-- TEMPORÁRIO: o app adm insere com a chave anônima.
-- Troque por login (Supabase Auth) + política "to authenticated" depois.
create policy "songs_insert_temp" on public.songs
  for insert to anon with check (true);

-- Bucket público para os arquivos de música
insert into storage.buckets (id, name, public)
values ('songs', 'songs', true)
on conflict (id) do nothing;

-- TEMPORÁRIO: upload com chave anônima
create policy "songs_bucket_insert_temp" on storage.objects
  for insert to anon with check (bucket_id = 'songs');
