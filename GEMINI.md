# GEMINI.md — DanPlus | instruções locais para Gemini CLI / Antigravity

> Atualizado em 19/11/2026. **PLANEJAMENTO E GOVERNANÇA: NÃO É AUTORIZAÇÃO PARA CODIFICAR, FAZER BUILD, ALTERAR GIT REMOTO OU PUBLICAR.**
> Este arquivo aponta para o pacote de futuras mudanças aprovado para organização. A autoridade sobre o estado real continua sendo: código/runtime/testes atuais > Git > documentos canônicos > registros históricos.

## Leitura obrigatória antes de qualquer tarefa

1. Ler `D:\APK\AGENTS.md` e as instruções da missão atual.
2. Ler `docs/danplus/gemini-planejamento/INDICE.md` e, para a tarefa proposta, os documentos que ele identifica.
3. Ler inicialmente `DP_CODEX_00_LEIA_PRIMEIRO_20261117.md`, `DP_CODEX_01_MEGALOTES_E_GATES_20261117.md`, `DP_CODEX_02_PRECHECK_ACEITE_E_PROTECOES_20261117.md`, `DP_CODEX_03_MATRIZ_146_E_RS_20261117.md`, `DP_CODEX_04_MUD_R3_PEDIDOS_PENDENTES_20261117.md`, `DP_CODEX_05_RISCOS_CONTINUIDADE_20261117.md` dentro dessa pasta.
4. Para automação, consultar os documentos DP_CODEX_08, DP_CODEX_09, DP_CODEX_10 e DP_AUTOMACAO_GITHUB_SEM_CODEX_ESTUDO.
5. A pasta de planejamento é uma **cópia documental datada** da fonte original `D:\APK\Organizacao DanPlus 2.0`. Em caso de mudanças posteriores, comparar originais, novos pedidos e código antes de decidir. Não sobrescrever os originais nem tratar documentos históricos como status de execução atual. **Se esta branch remota contiver só o `GEMINI.md`, os demais MDs citados NÃO estão disponíveis nela: consultar o pacote local ou solicitar os arquivos, jamais inventar seu conteúdo.**

## Projeto e identidade

- Produto: **DanPlus**, aplicativo Android TV Kotlin/Compose para **NVIDIA Shield Pro 2017** e **Fire TV Stick 4K Max (2ª geração)**. Preservar D-pad, perfis, dados, áudio, player, catálogo, Seekr e experiência visual homologada.
- Linhagem: **Nuvio ARC** (`ysosrs123/NuvioTV-Fork`) e **Nuvio Original** (`NuvioMedia/NuvioTV`) são duas fontes independentes a monitorar. **Reshaped** é fonte adicional opcional, não substitui ARC.
- Não deduzir equivalência por nome de release. O Git encontrado em 18/11 é shallow (7 commits) e tem remotes `fonte-local` e `github-publico`, não dois remotes upstream prontos. **Não executar unshallow/fetch massivo/reescrever histórico** sem análise e autorização.
- Repositório público legado: `danilopagotto82/DANPLUS-Shield-Arc31-Private` (nome não define visibilidade). Repositório futuro `danilopagotto82/DanPlus` é só plano; não criar/renomear/mover automaticamente.
- Versionamento: a ponte OTA **2.0.1** é missão separada, não baseline limpa. Nome visual desejado: **DanPlus**, preservando applicationIds, assinaturas e compatibilidade de atualização.

## Estado verificado nesta organização (não reinterpretar como homologação de outras missões)

- Worktree de origem do pacote de planejamento (OTA, **não a branch de organização**): `D:\APK\Worktrees\DanPlus_PONTE_OTA_MLC1_20261117`, branch `danplus/mlc1-ponte-ota-20261117`, HEAD inicial `99f740cd63e73cb8f5f3b6a51180a4bd001b4d7e`. Havia mudanças e arquivos não rastreados de OTA/avatares: **preservar integralmente**.
- Fonte 2.0 anterior citada nos MDs: `D:\APK\Worktrees\DanPlus_2_0_Unificado_ML01_20261115`. Confirmar existência, HEAD e situação REAL antes de usá-la; não ler/copiar/limpar `.wrangler`.
- Avatares Fire da 2.0.1: PASS visual físico já documentado. Reprodução Fire de filme completo + som na soundbar: **PASS observacional confirmado pelo proprietário em 18/11**. Medições de `MediaSession` e capturas ADB anteriores não representaram de forma confiável a experiência física; não declarar travamento só por essa telemetria.
- OTA online: **NÃO TESTADO**. Nenhuma publicação, atualização remota, merge, rename ou release está autorizada.
- O pacote de planejamento foi consolidado antes do smoke recente e contém históricos desatualizados: usar evidência atual para estado, mas manter as decisões de produto.

## Próximas melhorias — execução futura por megalotes, nunca tudo de uma vez

- **DP-ML00:** inventário, precheck, decisões e baseline sem edição.
- **DP-ML01:** reproduzir/investigar e, se comprovado, corrigir crash Compose na tela Detalhes; causa NÃO demonstrada nos relatórios antigos.
- **DP-ML02:** foco/DPAD/UX, Seekr e recorte inferior **BUG-THUMB-01** (manter o gerador existente, RS-04); **BUG-BACKDROP-01**: Indicações e Descobrir devem mostrar os backdrops rapidamente sem entrar em Detalhes e voltar. **LOGO-PTBR-01 / REQ16-05**: no Focus Dock dos cards informativos do Descobrir e da Biblioteca, buscar logo do TMDB primeiramente em português brasileiro e usar o idioma original da obra somente na ausência do logo PT-BR. Verificar foco, prioridade de idioma, cache, carregamento, estado Compose e RAM Fire; validação específica das Indicações em ML06. Incluir comparações com screenshots reais.
- **DP-ML03:** Continuar assistindo, calendário/séries, históricos, Trakt/Simkl e persistência sem perder dados de perfis.
- **DP-ML04:** recuperação inteligente de fontes por addon até esgotar elegíveis, sem loop infinito; tempo preservado, cancelamento e feedback; depois memória, cache/seek/32-bit Fire em subgate separado.
- **DP-ML05:** capítulos/áudio/legendas; RS-01 VAD opt-in desligado por padrão; RS-02 apenas investigar ASR, sem download automático de modelo. Não prejudicar Dolby/Atmos/DTS/TrueHD.
- **DP-ML06:** validar MUDs e pedidos de 16/11 de UI já existentes, corrigindo só diferenças comprovadas; indicações/Focus Dock, Para Você, estatísticas PT-BR e acabamento.
- **DP-ML07:** backend/RLS/Supabase/credenciais apenas com testes sintéticos e aprovação separada de produção; MUD-011 HOLD por segurança/termos; não copiar credenciais.
- **DP-ML08:** missão **separada** de trailers YouTube PT-BR, dublado/legendado e cache Supabase (ex.: Minority Report); depende de gates de API e backend.
- **DP-ML09:** ponte OTA 2.0.1 e identidade em trilha isolada; validação de assinatura, ABI, in-place e URLs antes de qualquer publicação.
- **DP-ML10:** homologação física Shield/Fire, regressão, screenshots, vídeo/áudio, segurança e rollback.
- **DP-ML11 (opcional, planejamento):** monitor automático **Nuvio Original + Nuvio ARC**, preferencialmente GitHub Actions/metadados sem IA recorrente; ledger durável, diff, deduplicação, revisão de PR e gates humanos. **NÃO IMPLEMENTADO**. Sem auto-merge, auto-release ou builds a cada consulta.

## Melhoria documentada — LOGO-PTBR-01 (REQ16-05, DP-ML02; NÃO IMPLEMENTADA)
- **Situação relatada:** o Focus Dock já consulta logos no TMDB, mas não força preferência PT-BR. Relato do usuário; não foi verificado no código nem em teste físico nesta revisão.
- **Regra de seleção estrita:** procurar primeiro logo português brasileiro (PT-BR). Só quando indisponível, buscar o **idioma original oficial do filme/série**, obtido dos metadados da obra. Não escolher inglês automaticamente se não for o idioma original. Se ambos indisponíveis, mostrar fallback visual neutro.
- **TMDB:** conferir como o idioma é informado nos arquivos de imagem (por exemplo, código `pt` ou `pt-BR` e `include_image_language`), pois o idioma da consulta de metadados não garante o idioma do logo. Usar critério real de disponibilidade.
- **Cache e foco:** manter a prioridade PT-BR em cache frio/quente e troca rápida por DPAD; evitar que resultado tardio ou cache de outra obra substitua logo PT-BR por original. Não mudar os backdrops, pôsteres, sinopses nem layout homologado.
- **Aceite futuro:** títulos com logos em PT-BR e original, somente original e sem logo; verificar Fire e Shield, retorno de Detalhes e troca de perfis. Sem implementar, compilar ou testar nesta atualização documental.
- **Referência canônica:** `docs/danplus/gemini-planejamento/DP_CODEX_04_MUD_R3_PEDIDOS_PENDENTES_20261117.md` (adendo LOGO-PTBR-01) e roteiro `DP_CODEX_01_MEGALOTES_E_GATES_20261117.md`. É complemento do REQ16-05 e não abre novo lote nem duplica BUG-BACKDROP-01.

## Decisões e segurança

- Auditoria Original/ARC: **146 IDs; 36 decisões e 110 pendentes**. Reshaped: **7 IDs; 6 decididos e RS-06 pendente**. B2-06 permanece PENDENTE. A2-35 NÃO RECOMENDAR. `INVESTIGAR` não é permissão de patch; `ADAPTAR` exige diff e gate; `MANTER` não deve ser reimplementado.
- As 14 MUD R3 e pedidos REQ16 são camadas diferentes: verificar implementação antes de repetir. Não duplicar B1-27/B2-07/RS-03, MUD-006/REQ16-06, MUD-008/REQ16-05 nem MUD-013/BUG-THUMB.
- Segurança Git **pendente antes de qualquer push**: `local.properties` e `.env` já são ignorados, mas o `.gitignore` atual não cobre genericamente `*.jks`, `*.keystore` ou `keystore.properties`. Auditar histórico e arquivos rastreados (inclusive recursos de teste, sem presumir que certificados públicos são chaves privadas); propor patch mínimo e revisão, **não publicar** antes do gate.
- Nunca incluir chaves, senhas, PIN, tokens, `.wrangler`, keystores, `local.properties`, arquivos reais de assinatura ou dados Supabase privados nos prompts, logs, snapshots ou Git. GitHub Actions futuro deve usar secrets/permissões mínimas somente após aprovação.
- Proibidos sem autorização: `git clean`, `reset --hard`, stash geral, force-push, alterações de produção, migration remota, mudança de packages ou signing, builds/instalação automática em TVs, criação/renomeação de repositório, merge, release ou OTA.

## Forma de trabalho

Comunicar em PT-BR, de forma direta: **Feito / Resultado PASS-FAIL-NÃO TESTADO / Próximo passo**. Conduzir PRECHECK → PLANO → PATCH mínimo → TESTE FOCADO → BUILD quando autorizado → SMOKE real → revisão do diff. Um escritor Android e um build pesado por vez. Gemini CLI é útil para leitura extensa, inventário e ajustes isolados; Codex só se precisar implementar mudanças Android críticas. Nunca tratar documentação copiada ou compile PASS como evidência física.

**Próxima ação de qualquer IA que abrir este projeto:** ler o índice das mudanças e informar quais megalotes são pertinentes à missão pedida; primeiro confirmar código/Git atuais e não iniciar implementação sem escopo e gate.
