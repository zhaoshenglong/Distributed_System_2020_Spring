1. What’s the purpose of using hugepage?

使用大页可以有效降低TLB miss的概率。比如一个大小为2MB的数据，使用4K的页存放的话，在TLB中会占用512个表项；而使用2MB的大页，则仅需要1个表项。

2. Take examples/helloworld as an example, describe the execution flow of DPDK programs?

* 程序首先调用`rte_eal_init`初始化 `environment abstract layer`。这个初始化的API读取入口参数`argv`，然后解析保存作为DPDK运行的系统信息。
* 在每个核上(如果有命令行参数-c，那么是mask剩下的核)启动指定的线程。在本程序中，每个核上执行的是`lcore_hello`
    `lcore_hello`执行的动作很简单，就是获取`lcore_id`然后打印`hello from core %u`

3. Read the codes of examples/skeleton, describe DPDK APIs related to sending and
receiving packets.
收发包之前，我们需要先初始化`port`，即绑定到dpdk的网卡。
**初始化**的动作如下:
    1. `rte_eth_dev_configure`
        - 第一个参数是 `port`，即准备用来收包/发包的网卡id
        - `rx_rings`，指定了收包的环数
        - `tx_rings`，指定了法宝的环数
        - `port_conf`，网卡配置，如果是NULL的话，使用默认配置。
    2. 初始化`rx`,`tx`的队列
        * `rte_eth_rx_queue_setup`
        * `rte_eth_tx_queue_setup`
        * 收包的API接受6个参数，发包的API接受5个参数，功能分别是初始化收包队列和发包队列
        * 前五个参数分别如下
            - port —— 绑定的网卡
            - 队列id
            - 收法包的环大小
            - port对应的socket id
            - 配置
        * 收包API还需要一个mbuf_pool参数
    3. 最后调用`rte_eth_dev_start(port)`

在这之后，初始化工作就完成了，然后就可以调用发包、收包的API
**sending**
    - `rte_eth_rx_burst`
        - 接受四个参数: port、队列 id、mbuf、收包数
**receiving**
    - `rte_eth_tx_burst`
        - 和`rte_eth_rx_burst`类似，接受四个参数: port、队列 id、mbuf、发包数

4. Describe the data structure of ‘rte_mbuf’
* `rte_mbuf`主要是一种内存buffer，创建`rte_mbuf`需要指定所属于的`mbuf_pool`, 释放mbuf之后，将会将其放入所属于的mbuf_pool里。
* `rte_mbuf`主要由四部分构成，`频繁访问的信息`,`head room`, `data part`, `trai room`。
    - 频繁访问的信息一般在两个 cache line大小以内
    - head room在`mbuf`刚创建的时候，有一定大小，主要是为了留出一定空间，使得包在一层一层传下来的时候，可以把包`prepend`到mbuf前面。
    - tail room主要是留出空间给`append`包。
* 在刚创建mbuf的时候，`data_len`为零，每次需要往mbuf填充内容时，需要调用API更改data_off或者data_len。
* 当一个包无法放一个mbuf的时候，会用链表将多个mbuf连接起来组成一个巨包。