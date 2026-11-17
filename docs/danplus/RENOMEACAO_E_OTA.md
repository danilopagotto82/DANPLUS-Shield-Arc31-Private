# DanPlus — Renomeação segura sem quebra de OTA

**Decisão oficial:** identidade pública **DanPlus**. README, pastas, descrições e modelos devem utilizar essa marca. Renomeação remota do repositório GitHub **fica para gate específico**.

| Superfície | Risco de renomeação indiscriminada | Ação |
| --- | --- | --- |
| URL/nome do GitHub | APKs instalados consultam endereço para updates | Mapear BuildConfig, GitHubReleaseApi e testar ponte |
| Tags, releases e nomes de APKs | Links antigos ou ABI podem falhar | Preservar histórico e validar downloads |
| applicationId, namespace e preferências | Perda de dados ou bloqueio de atualização in-place | Não alterar sem migration e rollback |
| Bibliotecas e arquivos upstream | Build quebrado ou violação de licença | Preservar atribuição e referências técnicas |
| CI/webhooks/automações | Quebra de integrações | Inventário e smoke antes da migração |

## Critérios de autorização da mudança de URL

1. Listar clientes e versões instaladas que dependem da URL atual.
2. Verificar downloads, assinatura, identificador e versionCode de Shield e Fire.
3. Validar redirecionamentos ou uma ponte compatível sem **presumir** que todos os clientes Android a seguem.
4. Manter fonte, tags, assets, backups e plano de restauração.
5. Fazer smoke de atualização in-place nos dois aparelhos.
6. Só com aprovação expressa, renomear o repositório e checar cada consumidor da OTA.

Nuvio/Arc permanecem somente onde são origem histórica, obrigação legal ou referência técnica necessária; não compõem a marca de produto DanPlus.