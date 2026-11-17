# DanPlus — Renomeação segura sem quebra de OTA

**Decisão oficial:** identidade pública **DanPlus**. README, pastas, descrições e modelos devem utilizar essa marca. Renomeação remota do repositório GitHub **fica para gate específico**.

| Superfície | Risco de renomeação indiscriminada | Ação |
| --- | --- | --- |
| URL/nome do GitHub | APKs instalados consultam endereço para updates e verificam o link aceito | Mapear BuildConfig, GitHubReleaseApi e o filtro UpdateRepository.downloadPrefix antes de testar a ponte |
| Tags, releases e nomes de APKs | Links antigos ou ABI podem falhar | Preservar histórico e validar downloads |
| applicationId, namespace e preferências | Perda de dados ou bloqueio de atualização in-place | Não alterar sem migration e rollback |
| Bibliotecas e arquivos upstream | Build quebrado ou violação de licença | Preservar atribuição e referências técnicas |
| CI/webhooks/automações | Quebra de integrações | Inventário e smoke antes da migração |

**Bloqueio real no código atual:** em app/src/full/java/com/nuvio/tv/updater/UpdateRepository.kt, o app monta UpdateRepository.downloadPrefix com o proprietário e nome atuais do GitHub e só aceita um APK cujo browserDownloadUrl comece com esse prefixo. A mudança de nome pode falhar mesmo com redirecionamento do GitHub. A Fire usa uma variante própria e também deve ser testada. Antes de renomear, será necessária uma versão-ponte com compatibilidade demonstrada ou outro mecanismo validado — sem trocar o app instalado às cegas.

## Critérios de autorização da mudança de URL

1. Listar clientes e versões instaladas que dependem da URL atual.
2. Verificar downloads, assinatura, identificador e versionCode de Shield e Fire.
3. **Antes da renomeação**, validar no aparelho o filtro UpdateRepository.downloadPrefix, os links da API/releases e uma ponte compatível. Não presumir que redirecionamentos funcionem em todos os clientes.
4. Manter fonte, tags, assets, backups e plano de restauração.
5. **Antes de mudar a URL**, fazer smoke de busca, download e atualização in-place na Shield e Fire, preservando perfis e dados; manter a versão anterior recuperável.
6. Somente com teste prévio PASS e aprovação expressa, renomear o repositório. Depois, repetir testes nos clientes antigos e novos, sem mexer em tags/releases até gate próprio.

Nuvio/Arc permanecem somente onde são origem histórica, obrigação legal ou referência técnica necessária; não compõem a marca de produto DanPlus.