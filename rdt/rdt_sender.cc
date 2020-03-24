/*
 * FILE: rdt_sender.cc
 * DESCRIPTION: Reliable data transfer sender.
 * NOTE: This implementation assumes there is no packet loss, corruption, or 
 *       reordering.  You will need to enhance it to deal with all these 
 *       situations.  In this implementation, the packet format is laid out as 
 *       the following:
 *       
 *       |<-  1 byte  ->|<-  1 byte  ->|<- 1 byte ->|<- 1 byte ->|<- 2 byte ->|<-             the rest            ->|
 *       | payload size | is msg end   |    ack     |     seq    |  checksum  |<-             payload             ->|
 *
 *       The first byte of each packet indicates the size of the payload
 *       (excluding this single-byte header)
 */


#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "rdt_struct.h"
#include "rdt_sender.h"
#include "helper.h"
#include <list>

/* Global variables definition */
static seq_nr sending_frame;
static seq_nr next_frame_to_send;
static std::list<struct packet*> msg_buf;
static struct packet *sent_pak[WINDOW_SZ];
static int nsent;
static std::list<struct virtual_timer *>timer_list;

/* sender initialization, called once at the very beginning */
void Sender_Init()
{
    nsent = 0;
    sending_frame = 0;
    next_frame_to_send = 0;
    fprintf(stdout, "At %.2fs: sender initializing ...\n", GetSimulationTime());
}

/* sender finalization, called once at the very end.
   you may find that you don't need it, in which case you can leave it blank.
   in certain cases, you might want to take this opportunity to release some 
   memory you allocated in Sender_init(). */
void Sender_Final()
{
    fprintf(stdout, "At %.2fs: sender finalizing ...\n", GetSimulationTime());
}

static void start_timer(seq_nr seq) {
    if(timer_list.empty()) {
        Sender_StartTimer(TIMEOUT);
    }
    struct virtual_timer *vt = (struct virtual_timer*)malloc(sizeof(struct virtual_timer));
    vt->seq = seq;
    vt->start_time = GetSimulationTime();
    timer_list.push_back(vt);
}

static void stop_timer(seq_nr seq) {
    std::list<struct virtual_timer*>::iterator it = timer_list.begin();
    while (it != timer_list.end() && (*it)->seq != seq) {
        it++;
    }
    ASSERT(it != timer_list.end());
    timer_list.remove(*it);
    free(*it);

    if (timer_list.empty()) {
        Sender_StopTimer();
    } else {
        Sender_StartTimer(TIMEOUT);
    }
}


static void fragmentation(struct message *msg) {
    /* maximum payload size */
    int maxpayload_size = RDT_PKTSIZE - HEADERSIZE;

    /* the cursor always points to the first unsent byte in the message */
    int cursor = 0;

    while (msg->size - cursor > maxpayload_size) {
        struct packet *pkt = (struct packet*)malloc(sizeof(struct packet));
        memset(pkt->data, 0, RDT_PKTSIZE);
        /* fill in the packet */
        set_pkt_payload_size(pkt, maxpayload_size);
        memcpy(pkt->data + HEADERSIZE, msg->data+cursor, maxpayload_size);
        set_pkt_end(pkt, 0);
        /* put into msg_buf */
        msg_buf.push_back(pkt);
        /* move the cursor */
        cursor += maxpayload_size;
    }

    /* send out the last packet */
    if (msg->size > cursor) {
        struct packet *pkt = (struct packet*)malloc(sizeof(struct packet));
        memset(pkt->data, 0, RDT_PKTSIZE);
        set_pkt_payload_size(pkt, msg->size - cursor);
        memcpy(pkt->data + HEADERSIZE, msg->data + cursor, msg->size - cursor);
        msg_buf.push_back(pkt);
    }

    struct packet *last_frame = msg_buf.back();
    set_pkt_end(last_frame, 1);
}


/* event handler, called when a message is passed from the upper layer at the 
   sender */
void Sender_FromUpperLayer(struct message *msg)
{
    fragmentation(msg);
    /* send packet */
    while (nsent < WINDOW_SZ && msg_buf.size() > 0) {
        struct packet *pkt = msg_buf.front();
        msg_buf.pop_front();
        set_pkt_seq(pkt, next_frame_to_send);
        set_pkt_checksum(pkt, compute_checksum(pkt));

        // printf("^^^^^^^^^^^^^^^^^^^^^^ Sender send packet from upper ^^^^^^^^^^^^^^^^^^\n");
        // printf("packet: size: %d, is_end: %d, ack: %d, seq: %d, checksum: %d, verify: %d\n", pkt->data[0], pkt->data[1], pkt->data[2], pkt->data[3], *(unsigned short*)(pkt->data + 4), verify_checksum(pkt));

        // for (int i = 0; i < 10; i++) {
        //     for (int j = 0; j < 12; j++) {
        //         printf("%.2x ", pkt->data[i*10+j + 8]);
        //     }
        //     printf("\n");
        // }

        sent_pak[next_frame_to_send % WINDOW_SZ] = pkt;
        start_timer(next_frame_to_send);
        Sender_ToLowerLayer(pkt);
        nsent++;
        inc(&next_frame_to_send);
    }
}

/* event handler, called when a packet is passed from the lower layer at the 
   sender */
void Sender_FromLowerLayer(struct packet *pkt)
{
    /* Only dealt with fine packet */
    if(verify_checksum(pkt)) {
        seq_nr ack = get_pkt_ack(pkt);
        // if (!between(ack_expected, ack, next_frame_to_send)) {
        //     printf("***************** Ack is not expected, ack_expected: %d, ack: %d, next_frame:%d **********************\n", ack_expected, ack, next_frame_to_send);
        //     // exit(-1);
        // }
        /* Receive acknowledgement, moves sliding window */
        printf("Sender receive frame: sending: %d, ack: %d, next_frame_to_send: %d\n", sending_frame, ack, next_frame_to_send);
        print_pkt(pkt);
        while (between(sending_frame, ack, next_frame_to_send)) {
            nsent = nsent - 1;
            stop_timer(sending_frame);
            free(sent_pak[sending_frame % WINDOW_SZ]);
            inc(&sending_frame);
        }

        while (nsent < WINDOW_SZ && msg_buf.size() > 0) {
            struct packet *to_send_frame = msg_buf.front();
            msg_buf.pop_front();
            set_pkt_seq(to_send_frame, next_frame_to_send);
            set_pkt_checksum(to_send_frame, compute_checksum(to_send_frame));

            // printf("^^^^^^^^^^^^^^^^^^^^^^ Sender Receive ack^^^^^^^^^^^^^^^^^^\n");
            // printf("packet: size: %d, is_end: %d, ack: %d, seq: %d, checksum: %d, verify: %d\n", pkt->data[0], pkt->data[1], pkt->data[2], pkt->data[3], *(unsigned short*)(pkt->data + 4), verify_checksum(pkt));

            // for (int i = 0; i < 10; i++) {
            //     for (int j = 0; j < 12; j++) {
            //         printf("%.2x ", pkt->data[i*10+j + 8]);
            //     }
            //     printf("\n");
            // }
            sent_pak[next_frame_to_send % WINDOW_SZ] = to_send_frame;
            start_timer(next_frame_to_send);
            Sender_ToLowerLayer(to_send_frame);
            nsent++;
            inc(&next_frame_to_send);
        }
        
    }
}

/* event handler, called when the timer expires */
void Sender_Timeout() {
    std::list<struct virtual_timer*>::iterator it = timer_list.begin();
    printf("!!!!!!!!!!!!!!!!!!!!!!!!!!!!! Sender Timeout list size: %lu !!!!!!!!!!!!!!!!!!!!!!!!!!!!\n", timer_list.size());
    while (it != timer_list.end()) {
        printf("..............Send seq: %d....................\n", (*it)->seq);
        Sender_ToLowerLayer(sent_pak[(*it)->seq % WINDOW_SZ]);
        it++;
    }
    
    if (!timer_list.empty()) {
        Sender_StartTimer(TIMEOUT);
    }
}

