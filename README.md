# feeyo-hlsserver 介绍

HTTP Live Streaming (HLS) 当前直播服务的主流协议之一，如果您关注流媒体服务、又再了解 HLS、MPEG-TS 的内容，
那个这个由我们内部模块孵化出来的 HLS流媒体服务器，可能会帮助到您。

![img](docs/images/HLS.png)


### 关于转码 
依赖 faac
- brew install faac
- sudo apt-get install libfaac-dev

### 关于流发布协议 
UDP packet

| name                  | byte length     | 
| :-------------------  | :------------   |
| Magic code       		| 89 89 (2byte)   | 
| packetSender      	| 4 byte          |
| packetType            | 1 byte          |
| packetReserved        | 8 byte          |
| packetId				| 4 byte          |
| packetLength          | 4 byte          |
| packetOffset          | 4 byte          |
| packet                | n byte          |
| CRC                   | 8 byte          |


packetType
- PCM , 0x01 
- AAC , 0x02 
- YUV , 0x03 
- H264, 0x04


### 关于 MPEG-TS
为了研究 MPEG-TS 协议 我们还做了一个TS文件的分析器，可能是最好用的之一
![img](docs/images/FF5FCEF1-BADD-435F-8EBE-F86C7505FA1D.png)




