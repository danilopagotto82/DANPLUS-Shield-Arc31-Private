# DanPlus 2.0.2 / 1644 — Ponte OTA, candidata local

**Data da conferência:** 19/11/2026. **Status:** PRE-HOMOLOGAÇÃO. Não instalada, publicada ou aprovada para OTA online.

## Artefatos finais existentes — não recompilar sem defeito comprovado

Pasta protegida: `D:\APK\Evidencias\MLC1_PONTE_OTA_20261117\PACOTE_DANPLUS_202_PRE_HOMOLOGACAO`.

| Dispositivo | APK | Pacote | ABI | SHA-256 |
|---|---|---|---|---|
| Shield | `app-full-arm64-v8a-release.apk` | `com.danplus.shield` | `arm64-v8a` | `964152DA6DACA8088C7DC67620F2D69AC1CCCD38CE5CF7DEBE2702CAD779328A` |
| Fire TV | `DaNexus-FireTV-2.0.2-armeabi-v7a.apk` | `com.danexus.tv.fire.debug` | `armeabi-v7a` | `A34AA70C321AB9B5F544CDAB7E74E8BB0442AB93800A2D84CB496A9FA1B2B4B0` |

O nome exibido nos dois aplicativos é **DanPlus**; a nomenclatura legada do APK Fire permanece propositalmente para compatibilidade da OTA. Os dois arquivos têm `versionName=2.0.2`, `versionCode=1644`. Assinatura v2 verificada com `apksigner` e certificados idênticos aos backups 2.0.1 (PASS em ambos). Não publicar outro arquivo com mesmo nome e bytes diferentes.

## Testes já executados

- **PASS:** 18/18 testes do `ForkUpdateRepositoryTest`, incluindo o defeito do repositório futuro GitHub ainda não criado (404) e manutenção de erros reais. XML: `D:\APK\Worktrees\DanPlus_OTA_404FIX_20261119\app\build\test-results\testFullDebugUnitTest\TEST-com.nuvio.tv.updater.ForkUpdateRepositoryTest.xml`.
- **PASS:** build final das variantes `assembleFullRelease` e `assembleFullFireFireOta`, registrado em `D:\APK\Evidencias\MLC1_PONTE_OTA_20261117\BUILD_FINAL_202_SHIELD_FIRE_20261119.log` (BUILD SUCCESSFUL).
- **PASS:** conferência real com `aapt` e `apksigner` dos APKs finais e backups 2.0.0/2.0.1.
- **NÃO TESTADO:** instalação 2.0.1 → 2.0.2 sem desinstalação, perfis/dados, reprodução, screenshots, reversibilidade e OTA online Fire/Shield. Não transformar build PASS em smoke PASS.

## Situação dos aparelhos — consulta somente leitura

Shield: ADB `192.168.0.4:5555`, modelo `darcy`, instalada `com.danplus.shield` 2.0.1/1643, repousando; sem acordar ou instalar. Fire: não apareceu no ADB; endereço antigo `192.168.1.20:5555` indisponível, PC atualmente em `192.168.0.x`. Reconectar antes de testar.

## GitHub e segurança — gates separados

- Origem pública atual: `danilopagotto82/DANPLUS-Shield-Arc31-Private`, `main`, release pública 2.0.0. Não mexer na release ou tags existentes.
- Destino planejado: `danilopagotto82/DanPlus`, ainda não criado. O código 2.0.2 consulta primeiro a origem antiga e tolera 404 da futura após consulta válida.
- GitHub Secret Scanning: **dois alertas abertos** de tipo Google API Key. #1 é chave pública conhecida do cliente YouTube Web no código `InAppYouTubeExtractor.kt` (igualdade exata verificada). #2 aparece somente em uma biblioteca binária histórica x86_64 `libtorrserver.so`, removida da árvore atual e ausente dos APKs 2.0.2; validade desconhecida. Não imprimir valores, não dismiss sem decisão e evidência.
- Planejamento Gemini e LOGO-PTBR-01 estão na branch documental isolada, HEAD `a6f9db9`; não misturar mudanças futuras de player/UI na ponte.
- **Não executar push, merge, criação/renomeação de repositório, instalação ou publicação sem aprovação específica.** Em especial, preservar o endereço antigo para clientes anteriores.

## Próximos gates

1. Reconectar Fire; verificar na Shield e na Fire os pacotes/códigos instalados e capturar dados antes/depois.
2. Com autorização de teste físico, instalar os dois APKs assinados **in-place** (`adb install -r`, jamais desinstalar/limpar dados), validar perfis, playback e mensagem de versão atual.
3. Com smoke PASS, autorizar separadamente a publicação 2.0.2 **primeiro no GitHub antigo** para o canal existente. Confirmar URLs e assets permitidos, tag/versionCode, download e retorno de OTA.
4. Aprovar separadamente a criação/transferência do repositório DanPlus e documentação, mantendo a origem antiga e histórico. Sem `force-push`, `reset --hard` ou alteração retroativa de tags.

## Atualização operacional — 19/11/2026 (prevalece sobre os registros anteriores)
- **Homologação física local: PASS MANUAL INFORMADO PELO PROPRIETÁRIO** na Fire TV e na NVIDIA Shield para perfis, avatares, navegação, reprodução e OTA manual. Não foram repetidos testes físicos automatizados nesta etapa.
- **ADB verificado**: Fire e Shield em versionName 2.0.2 e versionCode 1644, tela Sobre confirma, processos ativos e registros Android de instalação anterior preservados. Shield atualizada às 14:35:33 e Fire às 14:13:56 do dia 19/11. Nenhum adb install na retomada.
- **RELEASE NÃO PUBLICADA**: GitHub legado ainda apresenta somente até a versão 2.0.0. Atualização OTA de um aparelho antigo 2.0.0 até 2.0.2 via publicação real AINDA NÃO TESTADA.
- **Segurança PENDENTE**: alertas GitHub #1 (fallback de trailer) e #2 (biblioteca nativa histórica) OPEN, validade UNKNOWN. Fixture PKCS#12 histórica segue rastreada; classificar antes de exposição pública em nova migração.
- **Proveniência**: código da ponte foi isolado em branch local específica; oito arquivos Kotlin/Gradle conferidos com a fonte do build (diferenças LF/CRLF normalizadas), sem novo build. SHA256 dos APKs permanece no manifesto acima.
- **Nenhum push, merge, tag, release ou rename** autorizado por esta homologação.
