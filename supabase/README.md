# DAN+ Shield Private — backend ARC 4

O Android mantém o QR nativo do Arc. A migração deste diretório acrescenta o RPC de Indicações a um backend Nuvio compatível. O projeto Supabase precisa primeiro ter o esquema, Auth, sincronização de perfis/PIN e portal de aprovação Nuvio. A migração depende de `auth.users`, `auth.sessions`, `public.profiles`, `public.watched_items`, `get_sync_owner()` e `verify_profile_pin(integer,text)`. A referência lida foi o [Nuvio self-host em 30f1568](https://github.com/NuvioMedia/self-host/tree/30f156807ae77b2aec80b091b0d513841a25ab91).

## Primeira execução na worktree ARC 4

O laboratório usa a tag de origem `1.1.0-arc.4`, com versionCode 1637. A build isolada usa `-PnuvioAppIdSuffix=.arc4danplus`, com SDK 36, JBR 21 e Gradle 8.13. Rode `.\gradlew.bat :app:assembleFullDebug -PnuvioAppIdSuffix=.arc4danplus` a partir desta pasta. Na Shield, selecione o APK ARM64. Confirme o manifesto antes de instalar.

Para a configuração oficial, mantenha `NUVIO_SUPABASE_URL=https://api.nuvio.tv` e use a chave pública publicada em [Descoberta Nuvio](https://api.nuvio.tv/.well-known/nuvio). A aprovação usa [Nuvio Link](https://nuvio.tv/link). Use a chave pública no Android e mantenha os valores locais fora do Git. Trakt, TMDB e MDBList podem reutilizar os registros DAN+ já validados para laboratório. Contas, tokens de servidores de mídia e chaves de IA são configurados na TV.

Os campos pendentes já existem em `local.example.properties`:

| Campo | Onde obter |
| --- | --- |
| `SIMKL_CLIENT_ID` | Registro Simkl compatível com AUTH V1, pois a tag usa `/oauth/pin`. Um ID AUTH V2 exige migração do cliente para `/oauth2/*`. [Autenticação Simkl](https://api.simkl.org/authentication) |
| `WETRAKR_CLIENT_ID` | Registro do DAN+ no WeTrakr. [WeTrakr](https://wetrakr.com/) |
| `PREMIUMIZE_CLIENT_ID` | Registro OAuth para login por código. A API também aceita a chave pessoal pelo app. [API Premiumize](https://www.premiumize.me/api) |
| `TVDB_API_KEY` | Chave v4 do projeto no painel TheTVDB. [TheTVDB](https://support.thetvdb.com/kb/faq.php?id=81) |

A staging DAN+ contém o backend D12 e foi validada separadamente com o backend Nuvio self-host. Para login oficial, mantenha `DANPLUS_D12_SYNC_ENABLED=false` até existir um contrato do Nuvio para confirmar sessão ativa, owner, perfil UUID, PIN e revogação entre os serviços. As Indicações deste aparelho funcionam pelo armazenamento local do app.

## Configuração do Android

Use `local.example.properties` como modelo e preencha `local.properties` na raiz. Para URL, chave e fallback Supabase, Debug também aceita `local.dev.properties` com prioridade. Mantenha as duas fontes no mesmo ambiente. Ambos os arquivos são ignorados no Git. O Gradle desta tag não lê `dev.properties` nem variáveis de ambiente homônimas para essas propriedades.

| Recurso | Configuração necessária |
| --- | --- |
| Conta e QR | `NUVIO_SUPABASE_URL` HTTPS e `NUVIO_SUPABASE_ANON_KEY` com chave pública. `DEVICE_LOGIN_WEB_BASE_URL` e `TV_LOGIN_WEB_BASE_URL` precisam abrir páginas de aprovação conectadas ao mesmo projeto. Ajuste `AVATAR_PUBLIC_BASE_URL` se usar o bucket de avatares. Use fallback apenas para outra origem comprovada do mesmo ambiente. |
| Rotas do QR | `start_device_login_session` ou fallback `start_tv_login_session`, `poll_tv_login_session`, `tv-logins-exchange` e portal capaz de aprovar o código. Valide de TV e celular e confirme que o QR é de uso único. |
| Conta e perfis | `/auth/v1`, RPCs nativas de owner, registro de dispositivo, perfis, PIN, biblioteca e histórico conforme o pacote Nuvio. Valide se as sessões de aparelhos vinculados resolvem o owner esperado no servidor. |
| D12 local | Disponível no app. A abertura e edição usam o perfil ativo e o armazenamento do aparelho. |
| D12 entre aparelhos | `DANPLUS_D12_SYNC_ENABLED=true` somente para build de ambiente com o RPC `recommendations_v1` validado. Sem UUID do perfil retornado por `sync_pull_profiles`, a indicação fica local. |
| Trakt | `TRAKT_CLIENT_ID` do registro autorizado, com `TRAKT_API_URL` e `TRAKT_REDIRECT_URI` conforme o ambiente. O fluxo de código da TV usa o Client ID público. |
| Simkl, MDBList e WeTrakr | `SIMKL_CLIENT_ID`, `MDBLIST_CLIENT_ID` e `WETRAKR_CLIENT_ID`. Floppy usa servidor/token configurados no app. |
| Metadados | `TMDB_API_KEY`, `TVDB_API_KEY` conforme os recursos habilitados. URLs opcionais de trailers, guias parentais, IntroDB e fontes IMDb estão no exemplo. |
| Scout e IA | `SCOUT_ENABLED=true` e TMDB quando o recurso for escolhido. Ask usa chaves próprias por perfil inseridas no app, conforme o provedor selecionado. |
| Addons, debrid e servidores de mídia | Endpoints/contas do usuário configurados pelo app. Não colocar tokens pessoais em `local.properties`. |
| Relatórios e updates | `PLAYBACK_REPORTS_BASE_URL` e `REPORTS_SIGNING_KEY` somente com serviço aprovado. O código original ainda referencia o GitHub do fork para updates. Isso deve ser resolvido no empacotamento de distribuição. |

O servidor Supabase utiliza credenciais administrativas no ambiente de execução da Edge Function. A TV recebe apenas a chave pública e a sessão do usuário. A função em `functions/tv-logins-exchange/` atende a TV antes do login. A autenticação dessa troca é o par código aprovado e nonce consumido atomicamente pelo banco. A URL do projeto Supabase por si só não é a URL do portal.

O portal em `portal/` implementa `/link` e `/tv-login` com login por e-mail, confirmação explícita e as RPCs nativas de aprovação. Execute `node supabase/portal/server.mjs` com `DANPLUS_SUPABASE_URL`, `DANPLUS_SUPABASE_PUBLIC_KEY` e opcionalmente `DANPLUS_PORTAL_PORT`. O servidor escuta apenas em loopback. Publique atrás de um proxy HTTPS com domínio DAN+ e encaminhe as duas páginas, o script e `/runtime-config`. Remova query strings dos logs do proxy dessas páginas. Configure ambos os links do Android para esse domínio e valide CORS e HTTPS com o projeto alvo. A implementação atende ao fluxo de e-mail e senha.

## Indicações entre aparelhos

- O servidor deriva o owner do JWT e confere a sessão ativa. O remetente e os destinatários são UUIDs de perfis daquele owner. O app nunca envia owner como autorização.
- Um perfil com PIN exige prova curta emitida pelo servidor após o PIN nativo. A prova é vinculada à sessão, ao UUID do perfil e à versão do PIN. Uma troca de conta ou de perfil invalida o acesso à prova anterior no cliente. Reabra o seletor de perfil para renovar a prova após expiração.
- O Android guarda envios pendentes no aparelho e usa o UUID de cada envio para retry. A sincronização roda na tela Indicações e no fluxo de envio. A leitura e a exclusão de indicação usam operações próprias. O ✓ vem do histórico do destinatário e não da leitura. Na caixa Enviadas, a projeção de assistido fica oculta para destinatário com PIN.
- O banco impede acesso direto às tabelas por clientes. O RPC público é invoker e delega a execução à função privada, que verifica sessão, perfil, owner e PIN. Exclusão de perfil remove suas indicações por FK, e o mesmo número de perfil pode voltar com outro UUID.
- A lista usa páginas de 50 e o cliente recupera até 600 por caixa. O cache local tem retenção de até 600 registros e é limpo no logout. O cache remoto depende da sessão e do estado de desbloqueio local. Homologue troca de perfil/PIN e operação offline no aparelho.

## Teste local e implantação

O teste local usa PostgreSQL em memória via PGlite e dados sintéticos. Instale `@electric-sql/pglite@0.5.16` em um diretório temporário e execute `node supabase/tests/d12.mjs <caminho absoluto para @electric-sql/pglite/dist/index.js>`. O fixture simula claims, sessões e resposta do verificador nativo de PIN. Gateway, verificador real, web QR, Auth e aplicativos não fazem parte desse teste.

Aplique primeiro em uma cópia descartável do ambiente alvo após instalar e validar o backend Nuvio, depois valide a migração, RLS, rollback, duas TVs da mesma conta, duas contas distintas e os fluxos de PIN e revogação. O rollback em `supabase/rollback` remove exclusivamente os objetos D12 e descarta os registros D12. Faça backup dos dados D12 se o ambiente contiver dados que precisem ser preservados.

`tests/check-d12-prerequisites.sql` confere a presença das dependências SQL sem ler contas. Confira a Edge Function e o portal de aprovação separadamente.

## Staging DANPLUS-SHIELD-STAGING

O esquema Nuvio fixado em `30f1568`, a D12, a restrição de acesso direto às tabelas de perfil e QR e a função `tv-logins-exchange` foram implantados na staging. O bootstrap hospedado aplicado, hash e resultados estão nas evidências de 13/11/2026. A validação HTTPS passou com duas sessões da mesma conta, uma conta diferente e limpeza dos dados sintéticos.

O self-host cobre QR e D12, com lacunas do Arc em vínculos por código, catálogo de membros, cópia de configuração de perfil e Watch Party. Essas rotas exigem uma implementação ou decisão de produto própria para o DAN+.
