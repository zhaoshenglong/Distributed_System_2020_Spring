/*
 * FILE: rdt_receiver.cc
 * DESCRIPTION: Reliable data transfer receiver.
 * NOTE: This implementation assumes there is no packet loss, corruption, or 
 *       reordering.  You will need to enhance it to deal with all these 
 *       situations.  In this implementation, the packet format is laid out as 
 *       the following:
 *       
 *       |<-  1 byte  ->|<-             the rest            ->|
 *       | payload size |<-             payload             ->|
 *
 *       The first byte of each packet indicates the size of the payload
 *       (excluding this single-byte header)
 */


#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "rdt_struct.h"
#include "rdt_receiver.h"
#include "helper.h"

/* Global variable definition */
static seq_nr frame_expected;
static struct message* current_msg;

/* receiver initialization, called once at the very beginning */
void Receiver_Init()
{
    frame_expected = 0;
    current_msg = nullptr;
    
    fprintf(stdout, "At %.2fs: receiver initializing ...\n", GetSimulationTime());
}

/* receiver finalization, called once at the very end.
   you may find that you don't need it, in which case you can leave it blank.
   in certain cases, you might want to use this opportunity to release some 
   memory you allocated in Receiver_init(). */
void Receiver_Final()
{
    fprintf(stdout, "At %.2fs: receiver finalizing ...\n", GetSimulationTime());
}

static void send_ack(seq_nr ack) {
    struct packet pkt;
    memset(pkt.data, 0, RDT_PKTSIZE);
    set_pkt_ack(&pkt, ack);
    set_pkt_checksum(&pkt, compute_checksum(&pkt));
    printf("~~~~~~~~~~~~~~~~~~~Receive send ack: %d~~~~~~~~~~~~~~~~~~~~~\n", frame_expected);
    print_pkt(&pkt);
    Receiver_ToLowerLayer(&pkt);
}

static void reassembly_msg(struct packet *pkt) {
    if(!current_msg) {
        current_msg = (struct message*) malloc(sizeof(struct message));
        current_msg->size = 0;
        current_msg->data = nullptr;
    }
    current_msg->data = (char*)realloc(current_msg->data, current_msg->size + get_pkt_payload_size(pkt));
    memcpy(current_msg->data + current_msg->size, pkt->data + HEADERSIZE, get_pkt_payload_size(pkt));
    current_msg->size += get_pkt_payload_size(pkt);
    
    if (is_pkt_end(pkt)) {
        Receiver_ToUpperLayer(current_msg);
        free(current_msg->data);
        free(current_msg);
        current_msg = nullptr;
    }
}

/* event handler, called when a packet is passed from the lower layer at the 
   receiver */
void Receiver_FromLowerLayer(struct packet *pkt)
{
    /* extract data from frame */
    if (verify_checksum(pkt)){
        seq_nr seq = get_pkt_seq(pkt);
        // if (seq == 1 && frame_expected == last_frame && last_frame == 0) {
        //     printf("++++++++++++++++++++++ Receiver receives seq: %d, expected: %d, last_Frame: %d +++++++++++++++++++\n", seq, frame_expected, last_frame);
        //     exit(-1);    
        // }
        printf("++++++++++++++++++++++ Receiver receives seq: %d, expected: %d +++++++++++++++++++\n", seq, frame_expected);
        if (seq == frame_expected) {
            reassembly_msg(pkt);
            inc(&frame_expected);
            send_ack(frame_expected);
        } else {
            send_ack(frame_expected);
        }
    } else {
        // printf("^^^^^^^^^^^^^^^^^^^^^^ Verify checksum failed ^^^^^^^^^^^^^^^^^^\n");
        // printf("packet: size: %d, is_end: %d, ack: %d, seq: %d, checksum: %d, verify: %d\n", pkt->data[0], pkt->data[1], pkt->data[2], pkt->data[3], *(unsigned short*)(pkt->data + 4), verify_checksum(pkt));

        // for (int i = 0; i < 10; i++) {
        //     for (int j = 0; j < 12; j++) {
        //         printf("%.2x ", pkt->data[i*10+j + 8]);
        //     }
        //     printf("\n");
        // }

        // // exit(-1);
    }
}
