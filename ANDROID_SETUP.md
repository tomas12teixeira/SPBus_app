# SPBus Android

O módulo `app` é a aplicação Android nativa (Kotlin/XML). Abra a raiz `SPBus_app` no Android Studio e sincronize o projeto Gradle.

## Credenciais locais

Copie `secrets.properties.example` para `secrets.properties` na raiz e preencha os serviços que pretende ativar. O arquivo local é ignorado pelo Git. Não coloque chaves em arquivos Kotlin ou XML versionados.

- Supabase: use a URL do projeto e a chave `anon`; configure as políticas RLS da migração em `supabase/migrations/01_schema.sql`.
- Gemini: `GEMINI_API_KEY` habilita o assistente. Em produção, prefira um backend intermediário: qualquer chave embutida num APK pode ser extraída.
- ThingSpeak: configure o ID do canal e uma chave de leitura, caso o canal seja privado.

Sem credenciais, o app mantém os fluxos locais e sinaliza que os dados remotos não estão disponíveis. Para rotas oficiais de ônibus, carregue paradas, linhas e viagens GTFS da operadora; OSRM sozinho só calcula geometrias rodoviárias, não itinerários de transporte público.