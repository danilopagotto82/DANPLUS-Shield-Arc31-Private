# DanPlus

> **DanPlus 2.0.2/1644: candidata local pré-homologação, compilada e assinada, mas ainda NÃO instalada nem publicada.** A versão pública permanece **2.0.0**. [Checklist para homologação e migração](docs/danplus/PONTE_OTA_2_0_2_PRE_HOMOLOGACAO.md).

**Filmes, séries e bibliotecas na sua TV.** Projeto de aplicativo para Android TV e Fire TV, com navegação por controle remoto, perfis e reprodução a partir de fontes configuradas pelo próprio usuário.

> **DanPlus** é a marca do produto. O nome técnico do repositório GitHub ainda contém referências legadas porque o aplicativo já instalado consulta esse endereço para atualizações. Uma mudança de URL exige teste e migração.

## Versão 2.0.0 — downloads

| Plataforma | Arquivo da release | Arquitetura |
| --- | --- | --- |
| NVIDIA Shield / Android TV compatível | app-full-arm64-v8a-release.apk | ARM64 |
| Fire TV compatível | DaNexus-FireTV-2.0.0-armeabi-v7a.apk | ARMv7 |

**[Baixar na release pública DanPlus 2.0.0](https://github.com/danilopagotto82/DANPLUS-Shield-Arc31-Private/releases/tag/2.0.0)**

Os nomes dos arquivos refletem os APKs já publicados. Confirme a arquitetura, a assinatura, o identificador e a versão do app antes de atualizar um dispositivo. **Smoke físico completo da integração 2.0: não testado nesta auditoria.**

## Recursos e limites

- Navegação por controle remoto, organização de catálogos, perfis e reprodução.
- Compatibilidade e recursos de áudio/vídeo dependem de hardware, variante, configuração e testes reais.
- O DanPlus **não hospeda nem fornece filmes ou séries**: cada pessoa configura fontes próprias e deve possuir autorização para acessar seu conteúdo.
- A lista de mudanças planejadas não é uma lista de recursos já homologados.

## Código-fonte e rastreabilidade

O código associado aos APKs 2.0.0 está no commit [0ce90a0](https://github.com/danilopagotto82/DANPLUS-Shield-Arc31-Private/commit/0ce90a0740656c903b9780d2d423966b15119697), preservado na [branch central DanPlus 2.0](https://github.com/danilopagotto82/DANPLUS-Shield-Arc31-Private/tree/danplus/2.0-centralizacao-20261117).

A tag da release pública 2.0.0 ainda aponta para uma referência anterior do canal OTA. Esse descompasso histórico está documentado; **não mudar tag ou release retroativamente sem gate**.

## Documentação

- [Índice das 14 mudanças oficiais](docs/danplus/INDICE_MUD.md)
- [Regras de branches, testes e releases](docs/danplus/GOVERNANCA_E_GATES.md)
- [Plano para renomear o repositório sem quebrar OTA](docs/danplus/RENOMEACAO_E_OTA.md)
- [Proveniência, licenças e créditos](docs/danplus/PROVENIENCIA_E_LICENCAS.md)
- [Contribuição](CONTRIBUTING.md)

Relate problemas e sugestões em [Issues do DanPlus](https://github.com/danilopagotto82/DANPLUS-Shield-Arc31-Private/issues) **sem incluir senhas, tokens, dados pessoais ou links de fontes privadas**.

## Licença

O código do repositório preserva a [GNU GPL-3.0](LICENSE) e atribuições de software livre anterior. [Leia as informações de origem e terceiros](docs/danplus/PROVENIENCIA_E_LICENCAS.md).