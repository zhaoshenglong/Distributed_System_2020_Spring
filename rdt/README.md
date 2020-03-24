# Lab1 Reliable Data Transport Protocol

- Stutent ID: 515030910241
- Name      : 赵胜龙
- E-mail    : G245078728@sjtu.edu.cn
--------------------------------------------------

## Design & Implementation

本次作业我主要使用了Go-back-N协议进行RDT的设计与实现。(GBN和SR我都实现了，但是因为corrupt的问题，两者都不能很好的运行，导致我换了很多次实现，包括timer的实现、receiver的实现)

### Receiver
receiver的设计比较简单，主要负责从底层接受packet。

每次receiver收到底层的packet时，就检查checksum，如果checksum正确，receiver就检查包的seq。
1. 如果seq是receiver在等待的seq，receiver就将收到的packet进行重组成message。每当一个完整的message组装完成，receiver就会把这个message传给Upper layer。receiver发送回去一个ack=收到的seq+1(循环加)，即发送回去的ack是下一个想收的packet的seq。
2. 如果seq不是receiver在等待的seq，则发送回去一个ack，这个ack是receiver想得到的seq。

如果receiver收到的packet的checksum不对，则丢弃。

Receiver是不需要timer的。因为receiver只有一个ack在发送，如果该ack丢弃了，sender会认为TIMEOUT，并发送给receiver，而receiver总会发送成功ack，只要sender收到了ack，sender就会认为小于ack的包都已经收到了。

### Sender

Sender的设计稍微复杂一点。主要从三个方面来说，从上层接受message的处理、从下层接受packet的处理，timer的处理。

1. 从上层接收message

因为我们的sim是不能阻塞的，因此我们必须为上层发送给sender的message做缓存。我在sender中定义了一个`msg_buf`的全局变量，每当有message的到来，就拆成packet，放入这个`msg_buf`中去。

此时，如果sender的窗口有空间，并且`msg_buf`不为空，那么我们可以发送packet。

2. 从下层接受packet

每一个收到的packet，第一步就是做checksum检查，如果通过了checksum检查，则取出其中的ack。
理想情况是ack在窗口内，当且仅当ack在窗口内时，sender认为ack以下的receiver都接受到了，并移动窗口，删除已经缓存的packet并停止timer。

在移动窗口之后，窗口里可能有新的空间，因此每次接收到packet之后，sender还需要进行发送新的packet，这些packet就是`msg_buf`中的未发送的packet。

3. timer
在GBN中，sender的窗口内的所有的包作为一个整体，只需要一个timer。
- 每当发送一个包时，如果已经有timer启动，则不需要做任何事情，否则启动新的timer。
- 每当收到一个包时，移动窗口，则重启计时器
- 每当TIMEOUT时，重发所有窗口里的包，并重启计时器。

这里甚至不需要`virtual_timer`，但是由于我在做作业的过程中碰到corrupt的包仍然能够通过checksum检验的问题，我大改了很多次代码，选择性重传的virtual_timer的设计也就保留了下来。

