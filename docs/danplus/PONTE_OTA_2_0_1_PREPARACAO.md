# DanPlus 2.0.1 — Ponte OTA / migração GitHub (somente local)

**Data:** 18/11/2026. **Situação:** preparação local. Nada publicado ou instalado.

## Identidade e origem
- Nome no aparelho: **DanPlus**, versão proposta **2.0.1**, código **1643**.
- Repositório antigo: `danilopagotto82/DANPLUS-Shield-Arc31-Private`. Preservar **intacto indefinidamente**.
- Repositório novo planejado: `danilopagotto82/DanPlus`. **Não criar sem aprovação específica**.
- Pacotes imutáveis: Shield `com.danplus.shield`; Fire `com.danexus.tv.fire.debug`.
- Preservar certificados originais, perfis e dados.

## Comportamento da ponte
- Busca primeiro a origem antiga, depois a nova somente nas condições autorizadas de fallback.
- Não mascarar HTTP 403; validar downloads, origem real, arquitetura e versão superior.
- Suportar os nomes antigos e novos dos APKs Fire sem aceitar arquivo ambíguo.

## Ordem de operação futura — cada etapa exige gate próprio
1. Concluir testes locais, builds Shield e Fire, assinaturas, identidade, ABI e integridade.
2. Aprovar separadamente a instalação de teste 2.0.0/1642 → 2.0.1/1643; verificar dados, perfis, reprodução, navegação e OTA.
3. Com aprovação de publicação, disponibilizar primeiro a ponte 2.0.1 no **repositório antigo**. Os clientes 2.0.0 dependem dele.
4. Aprovar separadamente criação do novo GitHub; preparar futuras releases e documentação mantendo o histórico antigo.
5. Validar fallback e downloads com o novo repositório real sem modificar ou retirar a origem antiga.

## Proibições durante esta preparação
- Não instalar APK, publicar release, criar/renomear GitHub, alterar main/tags, executar commit/push/merge nem mexer em produção.
- Não expor nem copiar senhas, keystores ou arquivos de credencial para o repositório.
- Não substituir APKs homologados nem assumir que build local equivale a smoke físico.

## Referências locais
- Branch: `danplus/mlc1-ponte-ota-20261117`, HEAD-base `99f740cd63e73cb8f5f3b6a51180a4bd001b4d7e`.
- Evidências: `D:\APK\Evidencias\MLC1_PONTE_OTA_20261117`.
- Relatório: `D:\APK\Organizacao DanPlus 2.0\RELATORIO_MLC1_PONTE_OTA_20261117.md`.
- Registrar PASS / FAIL / NÃO TESTADO conforme os resultados reais antes de solicitar os gates futuros.

## Nomes dos assets no gate de publicação (compatibilidade retroativa)

- Shield, no canal antigo: `app-full-arm64-v8a-release.apk` (o seletor da versão 2.0.0 exige exatamente esse nome para ARM64/release).
- Fire, no canal antigo: `DaNexus-FireTV-2.0.1-armeabi-v7a.apk` (os clientes 2.0.0 reconhecem o nome legado; renomear o arquivo **não** altera o nome exibido do aplicativo).
- Para futuras releases no repositório novo, a ponte Fire também reconhece `DanPlus-FireTV-<versão>-armeabi-v7a.apk`, mas clientes antigos dependem da origem e nome antigos.
- APK local gerado pelo Gradle pode ter um nome diferente; **não publicar esse nome sem validar o seletor OTA antigo**. Só preparar cópia de distribuição após verificar assinatura, código, pacote, arquitetura e integridade dos binários.
- Não executar criação de release nem upload durante a etapa local.
