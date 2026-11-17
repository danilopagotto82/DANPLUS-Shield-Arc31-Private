# DanPlus — Governança Git, testes e releases

## Referências em 17/11/2026

| Referência | Função |
| --- | --- |
| main | Branch histórica padrão; **não é automaticamente a fonte dos APKs 2.0** |
| danplus/2.0-centralizacao-20261117 | Fonte pública associada à integração DanPlus 2.0.0, commit 0ce90a0 |
| danplus/ota-shield-arc5-ml1a-20261114 | Referência antiga do canal de atualização, preservada |
| danplus/ml-c-identidade-documentacao-20261117 | Branch **local** de preparação; sem publicação ou merge nesta etapa |

As branches main e de centralização estão protegidas contra force-push/exclusão e exigem PR. Isso **não equivale** a builds ou testes automáticos aprovados.

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

Os dois APKs existentes foram associados ao commit 0ce90a0, enquanto a tag pública ainda aponta para a referência anterior 135f2108. Preservar o histórico; na próxima release aprovada, vincular inequivocamente commit e binários.

Mudança documental não autoriza atualizar a produção, assinar APK, executar migração Supabase ou renomear URL GitHub.