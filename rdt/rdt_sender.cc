/*
 * FILE: rdt_sender.cc
 * DESCRIPTION: Reliable data transfer sender.
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
#include "rdt_sender.h"
#include "helper.h"
#include <list>

/* Global variables definition */
static seq_nr ack_expected;
static seq_nr next_frame_to_send;
static std::list<struct frame*> msg_buf;
static struct packet *sent_pak[WINDOW_SZ];
static int nsent;

/* sender initialization, called once at the very beginning */
void Sender_Init()
{
    nsent = 0;
    ack_expected = 0;
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

static void fragmentation(struct message *msg) {
    /* maximum payload size */
    int maxpayload_size = RDT_PKTSIZE - HEADERSIZE;

    /* the cursor always points to the first unsent byte in the message */
    int cursor = 0;

    while (msg->size - cursor > maxpayload_size) {
        struct frame *f = (struct frame*)malloc(sizeof(struct frame));
        /* fill in the packet */
        f->size = maxpayload_size;
        memcpy(f->data, msg->data+cursor, maxpayload_size);
        f->is_end = 0;
        /* put into msg_buf */
        msg_buf.push_back(f);
        /* move the cursor */
        cursor += maxpayload_size;
    }

    /* send out the last packet */
    if (msg->size > cursor) {
        struct frame *f = (struct frame*)malloc(sizeof(struct frame));
        f->size = msg->size - cursor;
        memcpy(f->data, msg->data + cursor, f->size);
        msg_buf.push_back(f);
    }

    struct frame *last_frame = msg_buf.back();
    last_frame->is_end = 1;
}


/* event handler, called when a message is passed from the upper layer at the 
   sender */
void Sender_FromUpperLayer(struct message *msg)
{
    fragmentation(msg);
    /* send packet */
    while (nsent < WINDOW_SZ && msg_buf.size() > 0) {
        struct frame *f = (struct frame*)msg_buf.front();
        msg_buf.pop_front();
        f->nak = 0;
        f->ack = 0;
        f->seq = next_frame_to_send;
        f->checksum = compute_checksum(f);
        //printf("Sender verify checksum !!!!!!!!!!!!!!!!!! %d\n", verify_checksum(f));
        sent_pak[f->seq % WINDOW_SZ] = (struct packet*)f;
        //printf("Sender verify checksum !!!!!!!!!!!!!!!!!! %d\n", verify_checksum((struct frame*)sent_pak[f->seq % WINDOW_SZ]));
        Sender_ToLowerLayer((struct packet*)f);
        free(f);
        nsent++;
        inc(&next_frame_to_send);
    }
}

/* event handler, called when a packet is passed from the lower layer at the 
   sender */
void Sender_FromLowerLayer(struct packet *pkt)
{
    struct frame *f = (struct frame*) pkt;
    //printf("Sender From lower layer %d\n", verify_checksum(f));
    /* Only dealt with fine packet */
    if(verify_checksum(f)) {
        /* Retransmit the packet */
        if (f->nak && between(ack_expected, f->ack, next_frame_to_send)) {
            Sender_ToLowerLayer(sent_pak[f->ack % WINDOW_SZ]);
        }

        /* Receive acknowledgement, moves sliding window */
        while (between(ack_expected, f->ack, next_frame_to_send)) {
            nsent = nsent - 1;
            inc(&ack_expected);
        }

        while (nsent < WINDOW_SZ && msg_buf.size() > 0) {
            struct frame *f = (struct frame*)msg_buf.front();
            msg_buf.pop_front();
            f->nak = 0;
            f->ack = 0;
            f->seq = next_frame_to_send;
            f->checksum = compute_checksum(f);
            sent_pak[f->seq % WINDOW_SZ] = (struct packet *)f;
            Sender_ToLowerLayer((struct packet*)f);
            nsent++;
            inc(&next_frame_to_send);
        }
        
    }
}

/* event handler, called when the timer expires */
void Sender_Timeout() {
}

