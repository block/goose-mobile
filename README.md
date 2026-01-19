# Goose Mobile

This project contains goose mobile implementations. 

## Goose for ios
The `goose-ios` dir is an ios client (which is an implementation of a remote protocol to access the goose agent) which connects back to your goose agent from anywhere, via a tunnel. This is available to use via the apple app store here: https://apps.apple.com/au/app/goose-ai/id6752889295 


## Goose android agent
The `goose-android-agent` dir contains a PoC implementaion of a full agent that runs on your android device, automating the whole device. This requires deep access to your android device and is best considered experimental

![Screenshot_20250708_124558](https://github.com/user-attachments/assets/af9d7d83-54f4-4ace-ad66-9e19f86c8fb9)

## Future directions

A `goose-android` client is planned to effectively be a port of the `goose-ios` project, a client to a remote goose, and will be available on google play store.

## Relevant links

* https://github.com/michaelneale/goose-ios for original source for ios client
* https://github.com/michaelneale/lapstone-tunnel for code that helps the goose agent be accessed remotely
