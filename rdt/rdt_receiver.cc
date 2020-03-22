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
static seq_nr frame_upper_bound;
static struct frame *sliding_window[WINDOW_SZ];
static bool arrived[WINDOW_SZ];
static struct message* current_msg;

/* receiver initialization, called once at the very beginning */
void Receiver_Init()
{
    frame_expected = 0;
    current_msg = nullptr;
    frame_upper_bound = WINDOW_SZ;
    for (int i = 0; i < WINDOW_SZ; i++) {
        arrived[i] = false;
    }
    
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

static void send_ack(seq_nr ack, int nak) {
    struct frame f;
    f.ack = ack;
    f.nak = nak;
    f.checksum = compute_checksum(&f);
    Receiver_ToLowerLayer((struct packet*)&f);
}

static void reassembly_msg(struct frame *f) {
    if(!current_msg) {
        current_msg = (struct message*) malloc(sizeof(struct message));
        current_msg->size = 0;
        current_msg->data = nullptr;
    }
    current_msg->data = (char*)realloc(current_msg->data, current_msg->size + f->size);
    memcpy(current_msg->data + current_msg->size, f->data, f->size);
    current_msg->size += f->size;
    
    if (f->is_end) {
        Receiver_ToUpperLayer(current_msg);
        free(current_msg->data);
        free(current_msg);
        current_msg = nullptr;
    }
    free(f);
}

/* event handler, called when a packet is passed from the lower layer at the 
   receiver */
void Receiver_FromLowerLayer(struct packet *pkt)
{
    /* extract data from frame */
    struct frame *f = (struct frame*)(pkt);
    //printf("Receiver from lower layer %d\n", verify_checksum(f));
    if (verify_checksum(f)){
        if (between(frame_expected, f->seq, frame_upper_bound) && !arrived[f->seq % WINDOW_SZ]) {
            arrived[f->seq % WINDOW_SZ] = true;
            struct frame *r = (struct frame *) malloc(sizeof(struct frame));
            memcpy(r, f, sizeof(struct frame));
            sliding_window[f->seq % WINDOW_SZ] = r;

            if (arrived[frame_expected % WINDOW_SZ]) {
                int ack = frame_expected;
                while (arrived[frame_expected % WINDOW_SZ]) {
                    arrived[frame_expected % WINDOW_SZ] = false;
                    reassembly_msg(sliding_window[frame_expected % WINDOW_SZ]);
                    
                    ack = frame_expected;
                    /* Moves sliding window */
                    inc(&frame_expected);
                    inc(&frame_upper_bound);
                }
                /* Ack n implies ack n-1, n-2... */
                send_ack(ack, 0);
            }
        }
    } else {
        /* send nak */
        send_ack(f->seq, 1);
    }
}
