# DanPlus — Governança Git, testes e releases

## Referências em 17/11/2026

| Referência | Função |
| --- | --- |
| main | Branch histórica padrão; **não é automaticamente a fonte dos APKs 2.0** |
| danplus/2.0-centralizacao-20261117 | Fonte pública associada à integração DanPlus 2.0.0, commit 0ce90a0 |
| danplus/ota-shield-arc5-ml1a-20261114 | Referência antiga do canal de atualização, preservada |
| danplus/ml-c-identidade-documentacao-20261117 | Branch documental publicada; PR #1 aberto para revisão, sem merge |

Em 17/11/2026, a proteção de main e da branch central (PR exigido, exclusão e force-push bloqueados, inclusive administradores) foi conferida pela API do GitHub. Reconfirme antes de cada merge em [Configurações de branches](https://github.com/danilopagotto82/DANPLUS-Shield-Arc31-Private/settings/branches); permissões podem mudar. Isso **não equivale** a builds ou testes automáticos aprovados.

## Gates obrigatórios

1. **PRECHECK:** HEAD, status, versão, integridade do backup e mudanças pré-existentes.
2. **PATCH:** alterações mínimas em branch isolada.
3. **TESTE FOCADO:** registrar comando e resultado, incluindo falhas.
4. **BUILD:** variantes Shield ARM64 e/ou Fire ARMv7, com assinatura verificada.
5. **SMOKE FÍSICO:** reprodução, DPAD e atualização preservando dados conforme o risco.
6. **REVISÃO DO DIFF:** detectar segredos, impactos em package, chaves, player e OTA.
7. **RELEASE:** aprovação separada, SHA de código, versão, APKs, hashes e rollback.

**NÃO TESTADO não é PASS.** Build concluída não demonstra fidelidade visual.

## Release 2.0.0 atual

Os dois APKs existentes foram associados ao commit 0ce90a0, enquanto a tag pública ainda aponta para a referência anterior 135f2108. Os nomes e os hashes SHA-256 foram conferidos tanto no cofre local quanto nos digests públicos da [release 2.0.0](https://github.com/danilopagotto82/DANPLUS-Shield-Arc31-Private/releases/tag/2.0.0):

| APK | SHA-256 verificado |
| --- | --- |
| app-full-arm64-v8a-release.apk (Shield) | 5902093bc071f2b970456a58788d35c7adacad17d840dfcd1454076abafa20a1 |
| DaNexus-FireTV-2.0.0-armeabi-v7a.apk (Fire) | 955f1eef29bbb0e4598d11a2929e2acb4f39ed1894d9fa0eae1b62f54b33b312 |

**Atenção:** esses hashes confirmam a integridade dos arquivos identificados, mas não constituem um manifesto assinado de forma independente. Preservar o histórico; em próximas releases, vincular diretamente commit, binários e assinaturas.

Mudança documental não autoriza atualizar a produção, assinar APK, executar migração Supabase ou renomear URL GitHub.