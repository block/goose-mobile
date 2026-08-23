# Goose Mobile

## Install (SHA-256)

Pin GitHub Release **v0.6.0** and verify `SHA256SUMS`. Website `install.sh` / `install.ps1` abort on mismatch.

https://github.com/LinespottingOrg/GrokBuildRemote-Agents/releases/tag/v0.6.0
https://github.com/LinespottingOrg/GrokBuildRemote-Agents/blob/main/docs/PINNED-INSTALL.md

```
96cef605d3e030ccef99d27ea6240e0d3b668dd045e6b5b9e585c9fd03c6ef23  gbr-agent-darwin-amd64
de7e065ef2cf6877b3b2cd04679a67b627f876337f529247e236204543e4062c  gbr-agent-darwin-arm64
a50a5c41993e6531a3b477eb409ccc845212bf541384dc803061c80657f86719  gbr-agent-linux-amd64
5bfd22c7110234942c4c02ff8154b836d0af45a9422c178a4f52010187d40061  gbr-agent-linux-arm64
f773b89fd31310172b756e0593e0f3b2382b0a3440af2a7d0a8b3073b0c23e27  gbr-agent-windows-amd64.exe
8fb9efcbc7e2ac91c11964944bf0f45e31bb23f4356d9dcb4b305d7cb9b0fe8c  gbr-agent-windows-arm64.exe
```

```bash
VER=v0.6.0
BASE=https://github.com/LinespottingOrg/GrokBuildRemote-Agents/releases/download/$VER
# swap darwin-arm64 for your OS/arch
curl -fsSL -o gbr-agent-darwin-arm64 "$BASE/gbr-agent-darwin-arm64"
curl -fsSL -o SHA256SUMS "$BASE/SHA256SUMS"
shasum -a 256 -c SHA256SUMS --ignore-missing
gbr-agent pair && gbr-agent run
```


This project contains goose mobile implementations. 

## Goose for ios
The `goose-ios` dir is an ios client (which is an implementation of a remote protocol to access the goose agent) which connects back to your goose agent from anywhere, via a tunnel. This is available to use via the apple app store here: https://apps.apple.com/au/app/goose-ai/id6752889295 


<img width="230" height="498" alt="image" src="https://github.com/user-attachments/assets/cdb57d53-bb7d-4ca4-9f89-a2fef38fef87" />


## Goose android agent
The `goose-android-agent` dir contains a PoC implementaion of a full agent that runs on your android device, automating the whole device. This requires deep access to your android device and is best considered experimental.

![Screenshot_20250708_124558](https://github.com/user-attachments/assets/af9d7d83-54f4-4ace-ad66-9e19f86c8fb9)

## Roadmap and help wanted

### Android client 

A `goose-android` client is planned to effectively be a port of the `goose-ios` project, a client to a remote goose, and will be available on google play store eventually. A very early start on this project is here: https://github.com/michaelneale/goose-android if you are looking for an android client (a port from goose-ios) - help wanted

### ACP support

We plan to migrate the client to use a http/remote version of the ACP (agent client protocol) which will allow the goose ios client to work with a variety of agents.

### Push messaging

Push messaging would be very useful for long running agentic workloads, this is an area to explore for how this can work for a variety of (open source) backends.

## Relevant links

* https://github.com/michaelneale/goose-ios for original source for ios client
* https://github.com/michaelneale/lapstone-tunnel for code that helps the goose agent be accessed remotely

## Spectator phone (Build Remote Agent)

goose-ios remains the first-party remote client (orchestrator via tunnel). If you want a **spectator** phone on the **desktop goose host** instead, pair [Build Remote Agent](https://grokbuildremote.com/) through the free MIT [`gbr-agent`](https://github.com/LinespottingOrg/GrokBuildRemote-Agents). Protocol `gbr/1`. Phone is spectator + veto, not orchestrator. Independent product — not affiliated with xAI or SpaceX. Do not merge this with the goose-ios tunnel.


Attach only `http://127.0.0.1:8788` (Bot API) or stdio `gbr-mcp`. Unpair in the phone app before switching PCs. Never commit mailbox keys.

## What the phone sees

**Terminal windows** on this PC (machine-wide mailbox). Not headless OpenCode / CodeNomad sidecar / Electron. `:8788` in a sidecar is Bot API JSON, not a transcript.

https://github.com/LinespottingOrg/GrokBuildRemote-Agents/blob/main/docs/WHAT-THE-PHONE-SEES.md
https://grokbuildremote.com/integrations.html
