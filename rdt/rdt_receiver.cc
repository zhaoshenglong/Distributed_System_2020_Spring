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
static struct frame sliding_window[WINDOW_SZ];
static bool arrived[WINDOW_SZ];


/* receiver initialization, called once at the very beginning */
void Receiver_Init()
{
    frame_expected = 0;
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

/* event handler, called when a packet is passed from the lower layer at the 
   receiver */
void Receiver_FromLowerLayer(struct packet *pkt)
{
    /* extract data from frame */
    struct frame *f = (struct frame*)(pkt);
    
    /* 4-byte header size */
    int header_size = 4;
    
    /* construct a message and deliver to the upper layer */
    struct message *msg = (struct message*) malloc(sizeof(struct message));
    ASSERT(msg!=NULL);
    msg->data = (char*) malloc(RDT_PKTSIZE - header_size);
    ASSERT(msg->data!=NULL);
    
    if (verify_checksum(f)){
        if (between(frame_expected, f->seq, frame_upper_bound) && !arrived[f->seq % WINDOW_SZ]) {
            arrived[f->seq % WINDOW_SZ] = true;
            sliding_window[f->seq % WINDOW_SZ] = *f;
            while (arrived[frame_expected % WINDOW_SZ]) {
                msg->size = sliding_window[frame_expected % WINDOW_SZ].size;
                memcpy(msg->data, sliding_window[frame_expected % WINDOW_SZ].data, msg->size);
                Receiver_ToUpperLayer(msg);
                arrived[frame_expected % WINDOW_SZ] = false;
                inc(&frame_expected);
                inc(&frame_upper_bound);
            }
            
        }
    } else {
        /* send nak */
        f->nak = 1;
        f->ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);
        struct packet *p = (struct packet *)f;
        Receiver_ToLowerLayer(p);
    }


    /* don't forget to free the space */
    if (msg->data!=NULL) free(msg->data);
    if (msg!=NULL) free(msg);
}
