<h1 align="center">分布式 Key-Value 存储系统实验报告</h1>

<div align="center">2020.07</div>



## 1. 简介

这篇报告主要介绍的是 **SE347-分布式系统** 课程作业 —— Key-value 存储系统。

Key-value 存储系统提供一个分布式的存储服务，用户可以使用该系统进行 *PUT*、*GET*、*DELETE* 操作数据对象。该系统主要具有以下的一些功能特性

* 可扩展性

  系统在运行期间可以根据需要增加减少计算资源，主要是数据节点的动态增加/缩减。

* 容错性

  系统使用多个备份节点来降低系统的不可用性，每个数据节点可以指定多个 *shadow* 数据节点，以防止数据节点称为单点故障。

* 持久化数据

  对于一个内存型数据存储系统，数据持久化可能会影响性能，但是持久化数据提供了崩溃一致性的保证，即使在数据节点及其备份全部崩溃，仍然能够保存一部分数据。

* 分布式的同步原语

  系统中大部分时间都不需要分布式的同步原语，但是在某些时刻可能需要分布式的锁。

### 1.1 系统环境

Key-value 系统采用 *Java* ，开发工具是 *Intellij IDEA Ultimate v2020.1.2*，开发平台为 *Windows 10.0* 。

Key-value 系统开发采用 *Maven* 进行依赖管理，以来的配置在 *pom.xml* 中，主要依赖了以下的库、框架：

* zkclient 			  用来简化连接 *zookeeper* ，封装了心跳检测、事件订阅等 *zookeeper* 相关的操作

* fastjson              主要用来实现系统中的持久化相关的一些操作

* snakyaml		   用来处理系统中的配置文件的序列化和反序列化

* sofa-rpc-all		用来实现系统中的进程间通信，包括系统内部的通信以及 *Client* 与系统之间的通信	



## 2. 设计

### 2.1 架构设计

如 *图 1* 所示，Key-Value 存储系统使用 Master/Slave 架构。系统中有一个单一的 *Master*，以及多个 *Nodes*， *Nodes* 根据配置形成不同的 *Groups*。

*Nodes* 负责存储系统中的数据。数据由 (*Key*，*Value*) 构成，每个数据根据 *Key* 的 hash 值存储在不同的 *Groups*。为了提高系统的可靠性及可用性，一个 *Group* 包含若干个 *Nodes* 副本。

*Master* 负责维护系统的 metadata 信息。这些 metadata 包含 namespace、Key 对应的 *Nodes*、*Nodes* 的心跳信息。除此以外，*Master* 还需要负责控制系统级别的事件，比如 *Nodes* 之间数据的迁移。*Master* 通过系统中的 Zookeeper 获取 *Nodes* 的心跳信息，以此来收集系统中所有 *Nodes* 的状态。

*Client* 在系统中操作数据时，首先向 *Master* 请求 Key 对应的 *Nodes* 信息，在获得 *Nodes* 信息后，*Client* 直接与 *Nodes* 进行交互。



![架构](D:\courses_2020Spring\Distributed System\labs\labs\kvstore\assets\imgs\基本架构.png)

<div align="center"><small>图 1：Key-Value 系统架构</small></div>

### 2.2 Single Master

Key-Value 存储系统中只有一个 *Master*。*Master* 具有全局的信息，可以根据这些全局信息做出响应的决策。为了防止 *Master* 成为 single point of failure，*Master* 应该尽可能少的进行读写，保持尽可能少的状态，并且，需要避免 *Master* 称为系统瓶颈。

为此，*Client* 只有在需要获取 *Nodes* 位置时，和 *Master* 进行交互，在获取到 *Nodes* 位置后， *Client* 可以在之后的操作中直接与 *Nodes* 交互。

以 *图 1* 中的 GET 操作为例

1. Client 将数据的 Key 发送给 *Master*
2. *Master* 回复给 *Client* 对应的 *Nodes* 列表，列表中第一个 *Node* 为 *Primary Node*
3. *Client* 用收到的 *Nodes* 信息发送请求给 *Nodes*
4. *Nodes* 回复相应的数据

### 2.3 Node Location

*Nodes* 的位置存储在 *Master* 中。*Master* 在启动的时候询问 Zookeeper，获取所有已经注册的 *Nodes* 信息。在 *Master* 成功启动后，通过监听 Zookeeper 来获取 *Nodes* 的状态变化，包括 新增、失联（宕机）等等。在这样的设计下，*Master* 不需要自己持久化保存 *Nodes* 信息，信息的可靠性取决于系统 Zookeeper，对于一个简易的 Key-Value 存储系统，这样的可靠性可以完全信任。

在 *Master* 中，*Nodes* 将会按照 一致性hash协议 进行组织，每个 *Nodes* 隶属于一个 *Group*。*Master* 将不同的 *Group* 通过指定的 hash 函数 hash 到 0 - 2<sup>31</sup> 环上。

当客户端发送请求给 *Master* 时，*Master* 通过同样的 hash函数 对 key 进行 hash，从而确定 key 对应的 *Group*, 随后，*Master* 将 *Group* 对应的 *Nodes* 信息回复给 *Client*。

#### 2.3.1 Load Balance

当系统中的 *Groups* 较少时，可能会造成 hash 环上节点倾斜的问题。

比如，*图 2* 中的 *Groups* 情况是很有可能发生的，为了避免这种情况的发生，*Master* 在组织 *Groups* 时使用 *VirtualGroup* 进行组织。一个 *Group* 映射到几个 *VirtualGroup* 在 Node 启动时进行指定。经过 *VirtualGroup* 到 *Group* 的映射后，hash 环的节点分布可能如 *图 3*所示，对数据倾斜问题能够起到一定的平衡作用。

<div style="display:flex">
	<div style="flex:1">
    	<div align="center">
            <img src="D:\courses_2020Spring\Distributed System\labs\labs\kvstore\assets\imgs\数据倾斜.png" alt="数据倾斜" style="zoom:25%;" />
        </div>
        <div style="margin-top: 20px; margin-left:35%">
        	<small>图 2：数据倾斜</small>
        </div>
	</div>
    <div style="flex:1">
        <div align="center">
            <img src="D:\courses_2020Spring\Distributed System\labs\labs\kvstore\assets\imgs\虚拟Group.png" alt="虚拟Group" style="zoom:25%;" />
        </div>
        <div style="margin-left:30%">
        	<small>图 3：VirtualGroup</small>
        </div>
	</div>
</div>

#### 2.3.2 Group Creation

当有新的 *Group* 添加到系统中时，*Master* 需要更新 hash 环。一个 *Group* 可能映射到多个 *VirtualGroup* ，在添加新的 *Group* 时，可能会多次更新 hash 环，添加多个 *VirtualGroup*。每添加一个 *VirtualGroup* ，需要做一次数据迁移，将新添加的节点到该节点前的节点之间的数据迁移到新加入的节点中。

每添加一个 *Group*，可能会进行多次的数据迁移。在此过程中，为了防止有 *Client* 与系统进行交互，产生一些错误的回复，需要使用全局的分布式锁。

### 2.4 Fault Tolerance

在 Key-Value 存储系统中，最具挑战性的一点就是如何应对组件的错误。我们假设组件的错误是正常状态，而不是异常。为此，Key-Value 存储系统通过冗余的方式来提高系统的可靠性及可用性。

Key-Value 存储系统中最主要的冗余组件是 *Group*。*Group* 由一个或多个多个 *Nodes* 副本构成。多个 *Node* 副本可以有效避免单点错误。但是多个 *Nodes* 副本同时也会带来一致性的问题，如何保证多个 *Nodes* 副本之间的一致性，也是整个系统中比较重要的设计。

#### 2.4.1 Node Replication

每个 *Node* 在启动时可以指定自己隶属的 *Group*，属于同一个 *Group* 的 *Node* 存储相同的数据。不同的 *Node* 可以分布在不同的机器上，机柜中，以提高可靠性。同一个 *Group* 的 *Node* 有一个选为 *Primary*，其它则为 *Backup*，*Primary* 的选举在 *Nodes* 启动时由其向 *zookeeper* 注册的先后顺序决定。

#### 2.4.2 Node Consistency

由于 *Group* 有多个 *Nodes* 备份，在更新一个 *Node* 的数据时，需要保持 *Nodes* 之间的数据一致性，这一点的设计也是系统中比较有挑战性的一点。

##### 2.4.2.1 Best case

理想情况指没有 *Node Creation*，也没有出现 *Node Failure* 的情况。 *Client* 请求 *Master* 后得到对应的 *Nodes* 位置，由于此过程中没有对 *Master* 的状态进行更新，因此多个 *Client* 可以并发执行。*Client* 得到 *Nodes* 位置后，通过 *RPC* 与 *Node* 交互。如果一切正常，请求会发送至 *Primary Node*，*Primary Node* 需要与同一个 *Group* 的其它 *Nodes* 进行同步，当且仅当 *Primary Node* 收到所有的 *Nodes* 的回复后才告诉 *Client* 操作成功，否则告诉 *Client* 操作失败。操作失败很可能是由于这个 *Group* 的某一个 Node 出现了错误。

##### 2.4.2.2 Node Creation

向系统中创建一个新的 *Node* 需要将系统中同一个 *Group* 的数据同步到这个 *Node* 中。在此过程中，需要获取全局锁，以防止 *Client* 与该 *Group* 进行交互时，导致 *Nodes* 之间的不一致性。同步数据时 *Master* 会通过 *RPC* 告诉该 *Group* 的 *Primary Node*，让其与新加入的 *Node* 同步数据。

##### 2.4.2.3 Node Failure

单个 *Node* 的错误对整个系统的影响不是很大，此时 *Client* 请求时一定会得到异常。而 *Master* 在发现 *Node Failure* 之后也会获取全局锁，更新 *Node* 状态，下次 *Client* 请求时就会获取到最新的 *Nodes* 状态。

当一个 *Group* 中的 *Nodes* 都出现 Failure 之后，一个 *Group* 就会出现 Failure。这种情况下，*Master* 需要维护一致性hash，移除已经错误的 VirtualGroup。

当系统管理员将出错的 Group 重启后，Node 会首先查看自己的本地 log，根据 log 恢复大部分数据。当有多个 Node 同时启动时，首先注册到 *zookeeper* 上的 *Node* 将会被 Master 选为 *Primary Node*，所以其它 *Node* 的数据以被选举的 *Primary Node* 为准，即使 *Primary Node* 的数据更旧。

### 2.5 Durability

考虑到 Group 可能会出现错误，导致数据丢失，为了尽可能的减少丢失，Key-value 存储系统在每个 *Node* 的每个写操作时，都进行了log。理想情况下，一个 *Group* 的 *Nodes* 应当数据一致。当一个 Node 出现错误并重启后，会首先读取本地的 log，恢复自己的数据。在该 *Node* 注册到 *zookeeper* 后，*Master* 会通过 RPC 让这个 *Group* 的 *Primary Node* 与其同步数据，数据以 *Primary Node* 的为准。如果该 *Node* 启动后，*Master* 发现该 *Node* 隶属的 *Group* 为空，则会委派其作为 *Primary Node*， 随后注册的 *Node* 将会与这个 *Node* 的数据进行同步。

### 2.6 Distributed Lock

#### 2.6.1 Mutex

分布式互斥锁保证系统中对同一个临界区的互斥访问。锁的设计主要依赖了 *zookeeper* 的一些协调机制，主要的过程可以分为下面几步：

* 锁的竞争者向 zookeeper 的节点创建有序节点
* 该竞争者检查自己是否是所有有序节点中最小的那个节点，如果是，则获的了锁
* 否则，该竞争者需要监听前一个最小节点，当前一个节点被删除时，就是该竞争者获得锁的机会（排队锁的思想）

释放锁的过程很简单，所得竞争者只需要将自己创建的有序节点删除，那么后面的节点就会接收到对应的事件，就可以拿锁了。

#### 2.6.2 ReaderWriter Lock

Key-value 系统中读写锁的设计采用的是偏向读锁的思想，虽然简单，但是可能会导致 *Writer* 饿死。读写锁中读者可以允许并行，而写者与写者、写者与读者之间是互斥的。

写锁的设计主要分为以下几个步骤：

* 锁的竞争者创建有序节点，如果自己是最小的节点，则成功获得写锁
* 否则需要等待前一个节点被删除，才能获得写锁
* 获得锁之后，锁的竞争者需要在 zookeeper 中**记录该有序节点的路径**，随后才能返回

这里和 互斥锁不一样的一点是，互斥锁是拿锁的人放锁，而读写锁中，拿写锁的人和放写锁的人不一定是同一个人，因此必须通过全局共享的 zookeeper 来保存锁的信息。

读锁的设计基于互斥锁，读锁主要用来防止在有读者的时候，写者介入。读锁的设计主要分为以下几个步骤：

* 读者首先获得一个读者互斥锁（防止其它读者进入）
* 随后读者需要检查自己是不是系统中第一个读者，如果是，读者请求获得写锁，以防止写者的接介入
* 将读者数量相应增加，并且记录在 *zookeeper* 中
* 释放读者互斥锁

读锁的释放过程类似读锁的获得过程，不同的是，释放读锁时需要检查自己是不是系统中最后一个读者，如果是，则需要释放写锁。



## 3. 部署

我实现的 Key-Value 存储系统部署在 2.20GHz 8 核处理器、32.0 GB 内存的 Windows 10.0机器上。使用 Process 模拟不同的机器分别部署 3个 Zookeeper 组成的 Cluster、一个 *Master*、4 个*Data Node*（2个 *Node* 组成一个 *Group*）。

### 3.1 Zookeeper 集群部署

#### 3.1.1 安装

安装步骤主要如下：

1. 从 [Zookeeper 官网](https://zookeeper.apache.org/)上下载并进行解压缩，得到 Zookeeper 的二进制文件包。目录结构如*图 4* 所示

   <img src="D:\courses_2020Spring\Distributed System\labs\labs\kvstore\assets\imgs\zk-目录结构.png" alt="image-20200624234220224" style="zoom: 67%;" />

   <div align="center"><small>图 4：Zookeeper 解压后目录结构</small></div>

#### 3.1.2 Zookeeper Cluster

由于我们需要部署 Zookeeper 集群，所以我们需要在不同的机器上都下载安装 Zookeeper。由于资源限制，只有一个主机，我们使用不同的 Zookeeper 进程来模拟 Zookeeper Cluster。

下面介绍我在主机上部署三个 Zookeeper 进程的过程

1. 新建一个空的文件夹 *zk_cluster*， 用来部署集群。

2. 将下载安装的 Zookeeper 文件包复制三份到 *zk_cluter* 中，分别命名为 *zk1*、*zk2*、*zk3* 。

3. 在 *zk_cluster* 下新建三个空的文件夹，分别命名为 *zk1_data*、*zk2_data*、*zk3_data*。

4. 分别在 *zk1*、*zk2*、*zk3* 文件夹下的 *conf* 文件夹下复制 *zoo_sample.cfg* 到 *zoo.cfg*。在 *zoo.cfg* 中分别填写以下参数配置（高亮部分是需要修改配置的部分）
   
   - dataDir = ==xxxx/zk1_data== （*zk2*、*zk3* 分别对应创建的 *zk2_data*、*zk3_data*）
   
   - clientPort= ==2181== （zk2、zk3 分别对应 2182、2183，可以自行指定）
   
   - server.==1== = ==localhost==:==2887==:==3887==
     server.2 = localhost:2888:3888
     server.3 = localhost:2889:3889
   
     此配置形式为 server.A = B:C:D，其中 `A` 为每个 zookeeper 的 id，`B` 为部署的机器的 hostname，`C` 为集群中用来选举 Leader 的端口，`D` 为用来选举 Leader 的备选端口
   
5. 在 *zk1_data*、*zk2_data*、*zk3_data* 下分别创建 myid 文件，对应填写 1、2、3 ，这里时每个 zookeeper 节点的 id，zookeeper启动后将会读取该文件设为自己的 id，这里需要和 *zoo.cfg* 中的 `A` 一致。

完成以上的配置后，就可以依次启动三个 zookeeper 节点。启动初期可能会报错，但是随着三个节点启动成功后，将会成功选举 Leader，之后就会正常运行。

为了方便部署 Zookeeper，可以写一个简单的启动脚本

```bash
start zk1\bin\zkServer.cmd
start zk2\bin\zkServer.cmd
start zk3\bin\zkServer.cmd
```

### 3.2 Master 部署

*Master* 部署需要提供一份配置文件，配置文件的格式为 *yaml*，放在项目的根目录下即可。Key-value 存储系统使用 *Intellij IDEA Ultimate* 进行开发，运行。在本次开发中未将其打包成 *jar* 。这里主要是由于我在尝试用 *Maven* 进行打包时会遇到 *Slf4j* 的报错问题，经排查应该是版本冲突。但是我尝试解决了很久，最终以失败告终，于是就暂时使用 *Intellij IDEA Ultimate* 进行部署运行。需要注意的是，系统中使用了 *lombok* 的注解，因此 *Intellij IDEA Ultimate* 需要安装 *lombok* 的插件。

Master 的配置文件内容如下：

```yaml
address: "localhost"	# Master运行的主机hostname
port: 1211				# Master运行的端口
zookeeper: "localhost:2181,localhost:2182,localhost:2183" # zookeeper的集群位置信息
```



### 3.3 Nodes 部署

*Nodes* 部署时也需要提供一份配置文件，配置文件的格式同样是 *yaml*。和 *Master* 的部署方式一样，也是通过 *Intellij IDEA Ultimate* 部署运行。

*Node* 的配置文件内容如下：

```yaml
address: localhost		# Node运行的主机hostname
port: 12111				# Node运行的端口
name: datanode-1		# Node的名字，必须全局唯一
group: group-1			# Node隶属的Group名，相同的Group名会被当作同一个Group的互为备份节点
weight: 3				# Group对应的VirtualGroup数量，一个 Group会被映射成多少个VirtualGroup在这里制定
zookeeper: localhost:2181,localhost:2182,localhost:2183
```



## 4. 系统演示

### 4.1 单元测试

#### 4.1.1 分布式锁的单元测试

##### 4.1.1.1 互斥锁

互斥锁的单元测试主要通过 多个 thread 合作计数来检查。测试中创建了 *nThreads* 个 `Thread` 对象，每个 `Thread` 循环增加共享变量。 经过多次测试，使用互斥锁保护的临界区最终计算的结果和理想的结果一致，可以认为在没有异常的情况下，分布式互斥锁的逻辑是正确的。

##### 4.1.1.2 读写锁

读写锁的单元测试主要需要证明三点：

* 读者与读者可以并行
* 读者与写者互斥
* 写者与写者互斥

为此，在测试中，我分别为读者和写者创建了5个线程，并模拟执行了读者任务和写者任务，为了保证读者与读者之间可以并行，读者任务和写者任务都进行了 Thread.sleep 操作，如下所示：

```java
System.out.println("Reader do job - step 1");
Thread.sleep(300);
System.out.println("Reader do job - step 2");
Thread.sleep(200);
System.out.println("Reader do job - completed");
```



<div style="display:flex">
    <p style="flex: 3">
        那么，理想情况下，读者打印的信息应该会交织在一起，而读者与写者之间不会交织，写者与写者之间也不会交织。测试的输出信息如右图所示
    </p>
    <div>
        <img src="D:\courses_2020Spring\Distributed System\labs\labs\kvstore\assets\imgs\读写锁测试png" alt="image-20200702214607556" style="zoom: 50%; flex:1" />
        <div align="center">
            <small>图5：读写锁测试结果</small>
        </div>
    </div>
</div>

#### 4.1.2 Master 的单元测试

*Master* 的单元测试包含两部分，第一部分是一致性 hash 的单元测试，第二部分是模拟 Key-value 系统运行过程，向 *Master* 中添加 *Node*，删除 *Node* 。

##### 4.1.2.1 Chord 单元测试

*Chord* 单元测试主要测试其主要的几个 api，包含 `addGroup`、`mapKey`、`getPrevGroup`、`getNextGroup`、`removeGroup` 。整个测试过程比较简单，主要的思想就是 Chord 在经过以上的操作后，应该维持一些不变性。

##### 4.1.2.2 Master 单元测试

Master 的单元测试比较麻烦，主要原因应该是我的实现将 *Master* 和 *Node* 耦合的比较深。比如在 *Master* 的 `addNode` 方法中，会通过 RPC 调用 *Node* 的一些接口，这就使得单元测试的时候一定会报错，因为并没有这样的 *Node* RPC 服务。

其次，Master的大部分方法都是 `private`，测试需要使用 java 的反射机制。

另外，由于 *Master* 中使用了分布式锁，而 *Master* 和 *zookeeper* 的耦合也比较深，分布式锁依赖于 *zookeeper*，这使得 Master 的单元测试需要运行很多进程，这一点不像单元测试。

为了简化测试，我将 *Master* 中 `getNode` 的 `readerWriterLock` 注释，这样就可以只运行单元测试的代码了。测试的部分输出如下

<img src="D:\courses_2020Spring\Distributed System\labs\labs\kvstore\assets\imgs\Master单元测试.png" alt="image-20200702220720777" style="zoom:80%;" />

<div align="center"><small>图6：Master单元测试结果</small></div>

#### 4.1.3 Node 的 日志单元测试

日志模块比较独立，单元测试相对容易的多。Key-value 存储系统的日志模块比较简单，对系统的性能影响比较大，这里是未来的一个优化的点。目前的实现是每一次的写操作都会记录对应的操作到日志中。当从日志恢复数据的时候需要从头至尾扫描日志，这应该是最不可取的日志恢复机制了，但是 worse is better，虽然不够好，但是足够简单。

#### 4.1.4 系统的测试

Key-value 的测试设计比较简单。测试需要手动启动整个系统之后，运行对应的测试代码。测试代码分为两个部分，一个是单线程的 Client 发送请求，另一个是 多线程的 Client 发送请求，测试会检查每次 `put` 后 `get` 是否能得到数据，以及 `delete` 后 `get` 是否能得到 `null`。多线程的 Client 发送请求时，操作的 `key` 之间不重叠，这里主要是因为防止出现其它 Client 更改了某个 Client 的数据，导致类似幻读、脏读的问题，因为 Key-value 存储系统没有提供 Transaction。

每个 Client 请求的逻辑代码如下

```java
try {
	res = RpcCall.getNodeRpcService(handle.primaryNode).put(key, value, handle);
    res = RpcCall.getNodeRpcService(handle.primaryNode).get(key, handle);
    Assert.assertEquals(res.payload, value);
    res = RpcCall.getNodeRpcService(handle.primaryNode).delete(key, handle);
    Assert.assertEquals(res.payload, value);
    res = RpcCall.getNodeRpcService(handle.primaryNode).get(key, handle);
    Assert.assertNull(res.payload);
} catch (RuntimeException e) {
	e.printStackTrace();
}
```

在测试中可能会出现 *SofaRPC* 的报错信息，一般是由于超时导致的问题，这个问题出现的次数比较少，也很难复现，因此我没有成功找出原因。

测试时，我启动了两个 *Node*，分别隶属于两个 *Group*，分别运行单线程 和 3个线程并发的 Client，都通过测试。

### 4.2 可扩展性测试

可扩展性测试主要通过人工动态增加 *Node*，观察 Node 的输出信息来实现。理想状况下，新添加一个已经存在的 *Group* 的 *Node*，对系统没有明显影响；而增加一个不存在的 *Group* 的 *Node*，将会发生至少一次数据迁移。

#### 4.2.1 扩展 Group 中的 Nodes

在增加一个已经存在的 *Group* 的 *Node* 之后，Master 会将 这个 *Group* 的 *Primary Node* 与 该 *Node* 同步数据。倘若数据同步成功，那么在增加这个机器之前能够访问的数据，增加这个机器之后也能够访问；除此以外，在关掉除了这个 Group 中其他机器，仅剩这个机器时，数据仍然应该能够访问。

<div style="display:flex">
	<div style="flex:1">
    	<img src="D:\courses_2020Spring\Distributed System\labs\labs\kvstore\assets\imgs\扩展一个Group-1.png" alt="image-20200702225434929" style="margin-right: 5px" />
    	<div align="center">
            <small>图7：添加数据</small>
        </div>
    </div>
    <div style="flex:1;"> 
        <img src="C:\Users\ALIENWARE\AppData\Roaming\Typora\typora-user-images\image-20200702225603986.png" alt="image-20200702225603986" style="margin-left: 5px; margin-top: 40px" />
        <div align="center" style="margin-top: 20px">
            <small>图8：DataNode1 log信息</small>
        </div>
    </div>
</div>


 

1. 图7 展示了向系统中添加数据，从图8 中可以看到 <5, 5> 是存储到 *DataNode1* 中了
2. 新增加一个 DataNode2，从下图10中可以看到，扩展之后访问正常

<div style="display:flex">
	<div style="flex:1">
    	<img src="C:\Users\ALIENWARE\AppData\Roaming\Typora\typora-user-images\image-20200702225818507.png" alt="image-20200702225818507" style="margin-right:5px;" />
    	<div align="center" style="margin-top:10px">
            <small>图9：增加DataNode2时，Masterlog输出</small>
        </div>
    </div>
    <div style="flex:1;"> 
        <img src="D:\courses_2020Spring\Distributed System\labs\labs\kvstore\assets\imgs\扩展Group-4.png" alt="image-20200702225853864" style="margin-left:5px;" />
        <div align="center">
            <small>图10：增加DataNode2后访问数据</small>
        </div>
    </div>
</div>

 

3. 关掉 DataNode1，如果数据迁移成功，则 get 5 应当返回 5

   <img src="C:\Users\ALIENWARE\AppData\Roaming\Typora\typora-user-images\image-20200702230130429.png" alt="image-20200702230130429" style="zoom:50%;" />

   <div align="center"><small>图11：关掉DataNode1后，访问数据</small></div>

   从图11可以看到，关掉之后 get 5 确实返回了 5，说明数据备份成功。这里 ”*Node Exception. Try again*“ 是由于 zookeeper 的心跳机制监测节点的状态有一定延迟，这个延迟主要由 zookeeper 的配置项 `tickTime` 决定，`tickTime` 表明心跳间隔。

#### 4.2.2 扩展 Group 

新增加一个 *Group* 之后， Master 会通过 RPC 调用迁移数据到新的 *Group* 中。下图展示了，在`4.2.1`的基础上增加一个 `group-3` 之后的数据迁移。

<div style="display:flex">
	<div style="flex:1">
    	<img src="D:\courses_2020Spring\Distributed System\labs\labs\kvstore\assets\imgs\4.2.2-1.png" alt="image-20200702230609129" style="zoom:50%; margin-right:5px" />
    	<div align="center">
            <small>图12：增加DataNode5时，Masterlog输出</small>
        </div>
    </div>
    <div style="flex:1;"> 
        <img src="C:\Users\ALIENWARE\AppData\Roaming\Typora\typora-user-images\image-20200702230903584.png" alt="image-20200702230903584" style="margin-left:5px"/>
        <div align="center" style="margin-top:8px">
            <small>图13：增加DataNode5后访问数据</small>
        </div>
    </div>
</div>

1. 图12展示了增加一个新的 *Group* <group-3>
2. 增加新的 *Group* 后，图13 是访问数据及其回复，其中部分数据来自于新增加的节点 *DataNode5*

<img src="D:\courses_2020Spring\Distributed System\labs\labs\kvstore\assets\imgs\4.2.2-2.png" alt="image-20200702230805064" style="zoom:50%;" />

<div align="center"><small>图14：访问数据时，DataNode5的log输出</small></div>



### 4.3 可用性测试

可用性测试主要是测试 Key-value 存储系统的备份有效性。当一个 *Group* 的 *Node* 只剩下一个时，访问是否正常？当一个 *Group* 完全挂掉时，重启能否保留尽可能多的数据？ 

#### 4.3.1 多个 Nodes 备份

为了方便展示，在系统中，我仅仅启动了一个 *Group*， 两个 *Nodes*（*DataNode1* 和 *DataNode2*），同样是插入 10 个数据。

1. 首先，在*DataNode1* 和 *DataNode2* 都正常运行时插入数据，如图15所示

<div style="display:flex">
	<div style="flex:1">
    	<img src="D:\courses_2020Spring\Distributed System\labs\labs\kvstore\assets\imgs\4.3.1-1.png" alt="image-20200702231631384" style="margin-right:5px" />
    	<div align="center">
            <small>图15：DataNode1和DataNode2均正常</small>
        </div>
    </div>
    <div style="flex:1;"> 
        <img src="C:\Users\ALIENWARE\AppData\Roaming\Typora\typora-user-images\image-20200702231737624.png" alt="image-20200702231737624" style="margin-left:5px;" />
        <div align="center" style="margin-top:3px">
            <small>图16：仅剩DataNode1</small>
        </div>
    </div>
</div>

2. 挂掉 *DataNode2* 后访问数据，仍然得到正常的数据。

#### 4.3.2 挂掉完整的 Group 后重启

在 `4.3.1` 的基础上，将 *DataNode1* 重启，然后访问数据。 

<img src="C:\Users\ALIENWARE\AppData\Roaming\Typora\typora-user-images\image-20200702231848390.png" alt="image-20200702231848390" style="zoom:50%;" />

<div align="center"><small>图17：重启后访问数据</small></div>

从图17中可以看到，在 *DataNode1* 完全恢复后，访问仍然能够得到正长数据。



## 5. Appendix

### 5.1 系统结构

```bash
├─main
│  ├─java
│  │  └─sjtu
│  │      └─sdic
│  │          └─kvstore
│  │              │  Client.java
│  │              │  DataNode1.java				# 数据节点入口，由于Slf4j冲突（未解决），没有将源代码打包成 jar
│  │              │  DataNode2.java
│  │              │  DataNode3.java
│  │              │  DataNode4.java
│  │              │  MasterNode.java
│  │              │
│  │              ├─common
│  │              │      DistributedLock.java
│  │              │      LogOps.java			# NodeLogger的操作类型
│  │              │      Utils.java				# 系统的辅助类，包含配置文件的读写，hash函数等等
│  │              │      ZkPath.java			# 系统使用的 zookeeper 路径
│  │              │
│  │              ├─core
│  │              │      Chord.java				# 一致性hash
│  │              │      Group.java		
│  │              │      GroupHandle.java		# Client使用 GroupHandle 请求数据节点
│  │              │      Master.java		
│  │              │      MasterInfo.java		# Master的元数据
│  │              │      Node.java			
│  │              │      NodeInfo.java			# Node的元数据
│  │              │      NodeLogger.java		
│  │              │      NodeResponse.java		# 数据节点返回给Client的响应
│  │              │      RES_TYPE.java			# NodeReponse的类型
│  │              │      VirtualGroup.java		
│  │              │
│  │              └─rpc
│  │                      MasterRpcService.java
│  │                      NodeRpcService.java
│  │                      RpcCall.java			# 辅助RPC调用的类
│  │
│  └─resources
│          log4j.properties						# log4j的配置
│
└─test
    └─java
        └─sjtu
            └─sdic
                └─kvstore
                        AppTest.java			# 系统启动后可以运行此测试代码
                        LockTest.java			# 分布式锁的单元测试
                        MasterTest.java			# Master的单元测试
                        NodeLoggerTest.java		# NodeLogger 的单元测试
                        TestUtils.java			# 测试辅助类，包含文件访问、私有成员反射机制
```



### 5.2 系统依赖库

```xml
<dependencies>
    <dependency>
      <groupId>com.101tec</groupId>
      <artifactId>zkclient</artifactId>
      <version>0.11</version>
    </dependency>

    <dependency>
      <groupId>com.google.guava</groupId>
      <artifactId>guava</artifactId>
      <version>29.0-jre</version>
    </dependency>

    <dependency>
      <groupId>org.yaml</groupId>
      <artifactId>snakeyaml</artifactId>
      <version>1.26</version>
    </dependency>

    <dependency>
      <groupId>com.alibaba</groupId>
      <artifactId>fastjson</artifactId>
      <version>1.2.57</version>
    </dependency>

    <dependency>
      <groupId>com.alipay.sofa</groupId>
      <artifactId>sofa-rpc-all</artifactId>
      <version>5.5.2</version>
      <exclusions>
        <exclusion>
          <groupId>org.slf4j</groupId>
          <artifactId>slf4j-api</artifactId>
        </exclusion>
      </exclusions>
    </dependency>

    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <version>1.18.12</version>
      <scope>provided</scope>
    </dependency>

    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>1.7.25</version>
    </dependency>

    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-log4j12</artifactId>
      <version>1.7.25</version>
    </dependency>

    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <version>4.11</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
```





