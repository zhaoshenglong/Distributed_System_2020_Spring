#include <stdint.h>
#include <inttypes.h>
#include <rte_eal.h>
#include <rte_ethdev.h>
#include <rte_cycles.h>
#include <rte_lcore.h>
#include <rte_mbuf.h>
#include <stdio.h>
#include <rte_ip.h>
#include <rte_ether.h>
#include <rte_udp.h>
#include <unistd.h>

#define RX_RING_SIZE 128
#define TX_RING_SIZE 512

#define NUM_MBUFS 8191
#define MBUF_CACHE_SIZE 250
#define BURST_SIZE 32

static const struct rte_eth_conf port_conf_default = {
	.rxmode = { .max_rx_pkt_len = ETHER_MAX_LEN }
};

struct arp_packet {
    uint16_t hw_t;
    uint16_t proto_t;
    uint8_t hw_sz;
    uint8_t proto_sz;
    uint16_t opcode;

    /* It may cause alignment problem */
    // struct ether_addr sender_macaddr;
    // uint32_t sender_ip;
    // struct ether_addr target_macaddr;
    // uint32_t target_ip;
};


static inline int
port_init(uint8_t port, struct rte_mempool *mbuf_pool)
{
	struct rte_eth_conf port_conf = port_conf_default;
	const uint16_t rx_rings = 1, tx_rings = 1;
	int retval;
	uint16_t q;

	if (port >= rte_eth_dev_count())
		return -1;

	/* Configure the Ethernet device. */
	retval = rte_eth_dev_configure(port, rx_rings, tx_rings, &port_conf);
	if (retval != 0)
		return retval;

    /* Allocate and set up 1 RX queue per Ethernet port. */
	for (q = 0; q < rx_rings; q++) {
		retval = rte_eth_rx_queue_setup(port, q, RX_RING_SIZE,
				rte_eth_dev_socket_id(port), NULL, mbuf_pool);
		if (retval < 0)
			return retval;
	}

	/* Allocate and set up 1 TX queue per Ethernet port. */
	for (q = 0; q < tx_rings; q++) {
		retval = rte_eth_tx_queue_setup(port, q, TX_RING_SIZE,
				rte_eth_dev_socket_id(port), NULL);
		if (retval < 0)
			return retval;
	}

	/* Start the Ethernet port. */
	retval = rte_eth_dev_start(port);
	if (retval < 0)
		return retval;
    
    /* Display my mac addr */
    struct ether_addr addr;
	rte_eth_macaddr_get(port, &addr);
	printf("Port %u MAC: %02" PRIx8 " %02" PRIx8 " %02" PRIx8
			   " %02" PRIx8 " %02" PRIx8 " %02" PRIx8 "\n",
			(unsigned)port,
			addr.addr_bytes[0], addr.addr_bytes[1],
			addr.addr_bytes[2], addr.addr_bytes[3],
			addr.addr_bytes[4], addr.addr_bytes[5]);

	return 0;
}


static void
get_macaddr_arp(uint8_t port, struct rte_mempool* mbuf_pool, \
    uint32_t my_ip, struct ether_addr *my_macadd, \
    uint32_t dst_ip, struct ether_addr *dst_macaddr)
{   
    uint8_t nb_rx = 1;
    for (;;) {
        struct rte_mbuf *bufs[BURST_SIZE];
        bufs[0] = rte_pktmbuf_alloc(mbuf_pool);
        
        /* Fill ether header */
        struct ether_hdr eth_h;
        eth_h.ether_type = htons(ETHER_TYPE_ARP);
        ether_addr_copy(my_macadd, &eth_h.s_addr);
        eth_h.d_addr.addr_bytes[0] = 0xFF;
        eth_h.d_addr.addr_bytes[1] = 0xFF;
        eth_h.d_addr.addr_bytes[2] = 0xFF;
        eth_h.d_addr.addr_bytes[3] = 0xFF;
        eth_h.d_addr.addr_bytes[4] = 0xFF;
        eth_h.d_addr.addr_bytes[5] = 0xFF;

        /* Fill ARP request frame */
        struct arp_packet arp_pkt;
        arp_pkt.hw_t = htons(1);                    // Ethernet (1)
        arp_pkt.proto_t  = htons(ETHER_TYPE_IPv4);  // IPv4 (0x0800)
        arp_pkt.hw_sz = 6;                          // MAC
        arp_pkt.proto_sz = 4;                       // IPv4
        arp_pkt.opcode = htons(1);                  // request (1)

        /* Copy to mbuf */
        char *head_off = rte_pktmbuf_append(bufs[0], sizeof(struct ether_hdr));
        if (!head_off)
            rte_exit(EXIT_FAILURE, "Cannot allocate more space after mbuf");
        memcpy(head_off, &eth_h, sizeof(struct ether_hdr));

        char *data_off = rte_pktmbuf_append(bufs[0], sizeof(struct arp_packet));
        if (!data_off)
            rte_exit(EXIT_FAILURE, "Cannot allocate more space after mbuf");
        memcpy(data_off, &arp_pkt, sizeof(struct arp_packet));
        
        data_off = rte_pktmbuf_append(bufs[0], 20);
        if (!data_off)
            rte_exit(EXIT_FAILURE, "Cannot allocate more space after mbuf");

        ether_addr_copy(my_macadd, (struct ether_addr*)data_off);
        *(uint32_t *)(data_off + 6) = htonl(my_ip);
        *(uint32_t *)(data_off + 16) = htonl(dst_ip);

        /* Send burst of TX packets, broadcast */
        const uint16_t nb_tx = rte_eth_tx_burst(port, 0, bufs, nb_rx);

        if (unlikely(nb_tx == 0)) {
            printf("************** nb_tx == 0 ****************\n");
            continue;
        }

        nb_rx = rte_eth_rx_burst(port, 0, bufs, BURST_SIZE);

        if(unlikely(nb_rx == 0)) {
            printf("************** nb_rx == 0 ****************\n");
            continue;
        }
        
        /* Parse host mac addr */
        struct ether_hdr *rxeth = rte_pktmbuf_mtod(bufs[0], struct ether_hdr*);
        ether_addr_copy(&rxeth->s_addr, dst_macaddr);
        break;
    }
}

static __attribute__((noreturn)) void 
send_pkt_to_host(uint8_t port, struct rte_mempool *mbuf_pool, \
    uint32_t my_ip, struct ether_addr *my_macaddr, \
    uint32_t dst_ip, struct ether_addr *dst_macaddr)
{
    uint16_t eth_sz = sizeof(struct ether_hdr), 
            ip_sz = sizeof(struct ipv4_hdr), 
            udp_sz = sizeof(struct udp_hdr), 
            data_sz;

    /* UDP Header, Arbitrary payload & port configuration */
    char data[] = "Hello from vmware";
    data_sz = sizeof(data);

    struct udp_hdr udp_h;
    udp_h.dgram_len = htons(sizeof(data));
    udp_h.dst_port = htons(128);
    udp_h.src_port = htons(128);

    /* IPv4 Header */
    struct ipv4_hdr ipv4_h;
    ipv4_h.version_ihl = (4 << 4) | 5;          // version 4 & 5 words    
    ipv4_h.type_of_service = 0;                 // make no sense
    ipv4_h.packet_id = htons(1);                
    ipv4_h.total_length = htons(ip_sz + udp_sz + data_sz);
    ipv4_h.fragment_offset = ntohs((1 << 14) | 0);
    ipv4_h.time_to_live = 50;
    ipv4_h.next_proto_id = 17;                  // UDP (17)
    ipv4_h.src_addr = htonl(my_ip);
    ipv4_h.dst_addr = htonl(dst_ip);

    /* Ethernet Header */
    struct ether_hdr eth;
    ether_addr_copy(my_macaddr, &eth.s_addr);
    ether_addr_copy(dst_macaddr, &eth.d_addr);
    eth.ether_type = ntohs(ETHER_TYPE_IPv4);

    /* Fill mbuf */
    struct rte_mbuf *bufs[BURST_SIZE];
    bufs[0] = rte_pktmbuf_alloc(mbuf_pool);
    char *data_off = rte_pktmbuf_append(bufs[0], eth_sz);
    if (!data_off)
        rte_exit(EXIT_FAILURE, "Cannot append ether to mbuf\n");
    memcpy(data_off, &eth, eth_sz);
    data_off = rte_pktmbuf_append(bufs[0], ip_sz);
    if (!data_off)
        rte_exit(EXIT_FAILURE, "Cannot append ether to mbuf\n");
    memcpy(data_off, &ipv4_h, ip_sz);
    data_off = rte_pktmbuf_append(bufs[0], udp_sz);
    if (!data_off)
        rte_exit(EXIT_FAILURE, "Cannot append ether to mbuf\n");
    memcpy(data_off, &udp_h, udp_sz);
    data_off = rte_pktmbuf_append(bufs[0], data_sz);
    if (!data_off)
        rte_exit(EXIT_FAILURE, "Cannot append ether to mbuf\n");
    memcpy(data_off, data, data_sz);

    /* Start sending packet */
    for (;;) {
        /* Use offload checksum computation */
        bufs[0]->l2_len = eth_sz;
        bufs[0]->l3_len = ip_sz;
        bufs[0]->ol_flags |= PKT_TX_IPV4 | PKT_TX_IP_CKSUM | PKT_TX_UDP_CKSUM;

        const uint8_t nb_tx = rte_eth_tx_burst(port, 0, bufs, 1);
        if (unlikely(nb_tx == 0))
            printf("++++++++++++++++++++ Send UDP Packet Failed +++++++++++++++++++++++\n");
        sleep(1);
    }   
}

int
main(int argc, char *argv[])
{
	struct rte_mempool *mbuf_pool;
	unsigned nb_ports;
	uint8_t portid;
    uint32_t my_ip, dst_ip;
    struct ether_addr my_macaddr, dst_macaddr;

	/* Initialize the Environment Abstraction Layer (EsAL). */
	int ret = rte_eal_init(argc, argv);
	if (ret < 0)
		rte_exit(EXIT_FAILURE, "Error with EAL initialization\n");

	argc -= ret;
	argv += ret;

	/* Check that there is an even number of ports to send/receive on. */
	nb_ports = rte_eth_dev_count();
	if (nb_ports < 1)
		rte_exit(EXIT_FAILURE, "Error: no port found\n");

	/* Creates a new mempool in memory to hold the mbufs. */
	mbuf_pool = rte_pktmbuf_pool_create("MBUF_POOL", NUM_MBUFS * nb_ports,
		MBUF_CACHE_SIZE, 0, RTE_MBUF_DEFAULT_BUF_SIZE, rte_socket_id());

	if (mbuf_pool == NULL)
		rte_exit(EXIT_FAILURE, "Cannot create mbuf pool\n");

    /* Only choose the first port */
    portid = 0; 

    if (port_init(portid, mbuf_pool) != 0 ) 
        rte_exit(EXIT_FAILURE, "Cannot init port %"PRIu8 "\n", portid);
    
    /* Get my ip addr and mac addr */
    my_ip = IPv4(192, 168, 107, 10);
    rte_eth_macaddr_get(portid, &my_macaddr);

    /* Hard code dst ip */
    dst_ip = IPv4(192, 168, 107, 1);

    /* Get dst_macaddr through ARP protocol */
    get_macaddr_arp(portid, mbuf_pool, my_ip, &my_macaddr, dst_ip, &dst_macaddr);

	printf("Target MAC: %02" PRIx8 " %02" PRIx8 " %02" PRIx8
			   " %02" PRIx8 " %02" PRIx8 " %02" PRIx8 "\n",
			dst_macaddr.addr_bytes[0], dst_macaddr.addr_bytes[1],
			dst_macaddr.addr_bytes[2], dst_macaddr.addr_bytes[3],
			dst_macaddr.addr_bytes[4], dst_macaddr.addr_bytes[5]);

    send_pkt_to_host(portid, mbuf_pool, my_ip, &my_macaddr, dst_ip, &dst_macaddr);

	return 0;
}
