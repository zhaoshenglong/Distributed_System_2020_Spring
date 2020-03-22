
#ifndef _HELPER_H_
#define _HELPER_H_

#include "rdt_struct.h"

#define MAX_SEQ         15
#define WINDOW_SZ       8
#define HEADERSIZE      9
#define TIMEOUT         0.3

typedef int seq_nr;

struct frame {
    unsigned int size       : 7;
    unsigned int nak        : 1;
    unsigned int ack        : 4;
    unsigned int seq        : 4;
    unsigned int is_end     : 8;
    unsigned int checksum   : 16;
    char data[RDT_PKTSIZE - HEADERSIZE];
};


/*
 * returns true if b is between a and c circularly
 * 
 */
bool between(seq_nr a, seq_nr b, seq_nr c);

/**
 * returns 16-bit internet checksum
 */
unsigned short compute_checksum(struct frame*);

/**
 * returns true if checksum valid
 */
bool verify_checksum(struct frame*);

void inc(seq_nr *n);

#endif